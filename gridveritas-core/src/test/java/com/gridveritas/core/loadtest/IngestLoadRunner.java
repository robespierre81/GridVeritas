package com.gridveritas.core.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridveritas.core.crypto.Ed25519Verifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone HTTP load generator for the attestation ingest path
 * (POST /api/v1/attestations) against a RUNNING gridveritas-core instance. Not a
 * JUnit test (no @Test methods, never runs from `mvn test`) - it drives real
 * load against whatever base URL it's pointed at, so it is invoked explicitly:
 *
 *   mvn -Pload-test -Dexec.args="<baseUrl> <adminPassword> <ingestPassword> [threads] [attestationsPerThread]" \
 *       test-compile exec:java
 *
 * Signs real Ed25519 attestations (the JDK's native Ed25519 support, same as
 * production) so the ingest path's actual crypto and DB write cost is exercised,
 * not a stub. Mixes in GET /attestations, POST /verify, and GET /actuator/health
 * calls per worker to approximate real traffic shape rather than a pure write
 * hammer. Responses are bucketed into success / rate-limited (429) / error so a
 * run against a live rate limiter reports throttling as data, not noise.
 *
 * Also tracks the X-Instance-Id response header (ADR-013): against a
 * Traefik-fronted, multi-replica deployment, the report's distinct-instance
 * count is direct evidence load balancing actually happened, not just an
 * assumption that it did.
 *
 * See ../../../../../../load-tests/README.md for the companion k6 script that
 * covers the read/auth path without needing per-request signing.
 */
public final class IngestLoadRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Outcome(String endpoint, int status, long latencyMs, String instanceId) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: IngestLoadRunner <baseUrl> <adminPassword> <ingestPassword> "
                    + "[threads=8] [attestationsPerThread=200]");
            System.exit(2);
        }
        String baseUrl = stripTrailingSlash(args[0]);
        String adminPassword = args[1];
        String ingestPassword = args[2];
        int threads = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        int perThread = args.length > 4 ? Integer.parseInt(args[4]) : 200;

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        System.out.println("Authenticating...");
        String adminToken = login(http, baseUrl, "admin", adminPassword);
        String ingestToken = login(http, baseUrl, "ingest", ingestPassword);

        System.out.println("Provisioning a load-test source...");
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicKeyBase64 = rawEd25519PublicKeyBase64(keyPair);
        String sourceId = createSource(http, baseUrl, adminToken, publicKeyBase64);
        System.out.println("Source: " + sourceId);

        List<Outcome> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicLong sequence = new AtomicLong(0);

        System.out.printf("Running %d threads x %d attestations (plus mixed read traffic)...%n", threads, perThread);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        Instant start = Instant.now();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        long seq = sequence.incrementAndGet();
                        outcomes.add(ingestOne(http, baseUrl, ingestToken, sourceId, keyPair.getPrivate(), seq));

                        // Roughly one read-path call for every write, approximating
                        // a verify-after-ingest client pattern.
                        outcomes.add(verifyByPayloadHash(http, baseUrl, ingestToken, seq));
                        if (i % 10 == 0) {
                            outcomes.add(health(http, baseUrl));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Worker error: " + e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(10, TimeUnit.MINUTES);
        pool.shutdown();
        Duration elapsed = Duration.between(start, Instant.now());

        report(outcomes, elapsed);
    }

    private static Outcome ingestOne(HttpClient http, String baseUrl, String ingestToken, String sourceId,
                                     PrivateKey privateKey, long seq) throws Exception {
        long tsMillis = System.currentTimeMillis();
        byte[] payloadHash = sha256ish("load-test-payload-" + seq);
        byte[] message = Ed25519Verifier.canonicalAttestation(sourceId, seq, tsMillis, payloadHash);
        String signature = sign(privateKey, message);

        String body = MAPPER.writeValueAsString(Map.of(
                "sourceId", sourceId,
                "payloadHash", HexFormat.of().formatHex(payloadHash),
                "timestampEpochMillis", tsMillis,
                "sequenceNr", seq,
                "signature", signature));

        long t0 = System.nanoTime();
        HttpRequest req = authedRequest(baseUrl + "/api/v1/attestations", ingestToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Outcome("POST /attestations", resp.statusCode(), (System.nanoTime() - t0) / 1_000_000,
                instanceIdOf(resp));
    }

    private static Outcome verifyByPayloadHash(HttpClient http, String baseUrl, String token, long seq)
            throws Exception {
        byte[] payloadHash = sha256ish("load-test-payload-" + seq);
        String body = MAPPER.writeValueAsString(Map.of("payloadHash", HexFormat.of().formatHex(payloadHash)));

        long t0 = System.nanoTime();
        HttpRequest req = authedRequest(baseUrl + "/api/v1/verify", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Outcome("POST /verify", resp.statusCode(), (System.nanoTime() - t0) / 1_000_000,
                instanceIdOf(resp));
    }

    private static Outcome health(HttpClient http, String baseUrl) throws Exception {
        long t0 = System.nanoTime();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health")).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Outcome("GET /health", resp.statusCode(), (System.nanoTime() - t0) / 1_000_000, instanceIdOf(resp));
    }

    private static String instanceIdOf(HttpResponse<String> resp) {
        return resp.headers().firstValue("X-Instance-Id").orElse("unknown");
    }

    private static String login(HttpClient http, String baseUrl, String username, String password)
            throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("username", username, "password", password));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Login as " + username + " failed: HTTP " + resp.statusCode()
                    + " " + resp.body());
        }
        return MAPPER.readTree(resp.body()).get("token").asText();
    }

    private static String createSource(HttpClient http, String baseUrl, String adminToken, String publicKeyBase64)
            throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "name", "load-test-source-" + System.currentTimeMillis(), "publicKey", publicKeyBase64));
        HttpRequest req = authedRequest(baseUrl + "/api/v1/sources", adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Source creation failed: HTTP " + resp.statusCode() + " " + resp.body());
        }
        JsonNode node = MAPPER.readTree(resp.body());
        return node.get("id").asText();
    }

    private static HttpRequest.Builder authedRequest(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token);
    }

    private static void report(List<Outcome> outcomes, Duration elapsed) {
        Map<String, List<Outcome>> byEndpoint = new java.util.TreeMap<>();
        for (Outcome o : outcomes) {
            byEndpoint.computeIfAbsent(o.endpoint(), k -> new ArrayList<>()).add(o);
        }

        System.out.println();
        System.out.println("=== Load test report ===");
        System.out.printf("Total requests: %d in %.1fs (%.1f req/s)%n",
                outcomes.size(), elapsed.toMillis() / 1000.0, outcomes.size() / (elapsed.toMillis() / 1000.0));

        List<String> distinctInstances = outcomes.stream().map(Outcome::instanceId).distinct().sorted().toList();
        System.out.printf("Distinct serving instances (X-Instance-Id): %d %s%n",
                distinctInstances.size(), distinctInstances);
        if (distinctInstances.size() <= 1) {
            System.out.println("  (only one instance served this run - either core isn't scaled >1, "
                    + "or the load balancer isn't distributing traffic - check docker compose ps / Traefik)");
        }
        System.out.println();

        for (var entry : byEndpoint.entrySet()) {
            List<Outcome> list = entry.getValue();
            long[] latencies = list.stream().mapToLong(Outcome::latencyMs).sorted().toArray();
            long success = list.stream().filter(o -> o.status() >= 200 && o.status() < 300).count();
            long throttled = list.stream().filter(o -> o.status() == 429).count();
            long errors = list.size() - success - throttled;

            System.out.printf("%-16s n=%-6d 2xx=%-6d 429=%-6d err=%-6d  p50=%dms p95=%dms p99=%dms max=%dms%n",
                    entry.getKey(), list.size(), success, throttled, errors,
                    percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 99),
                    latencies.length == 0 ? 0 : latencies[latencies.length - 1]);
        }
    }

    private static long percentile(long[] sortedLatencies, int p) {
        if (sortedLatencies.length == 0) {
            return 0;
        }
        int index = Math.min(sortedLatencies.length - 1, (int) Math.ceil(p / 100.0 * sortedLatencies.length) - 1);
        return sortedLatencies[Math.max(0, index)];
    }

    private static String rawEd25519PublicKeyBase64(KeyPair pair) {
        byte[] x509 = pair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(x509, x509.length - 32, raw, 0, 32);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static String sign(PrivateKey privateKey, byte[] message) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(message);
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    private static byte[] sha256ish(String s) {
        byte[] out = new byte[32];
        byte[] src = s.getBytes();
        for (int i = 0; i < out.length; i++) {
            out[i] = src[i % src.length];
        }
        return out;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private IngestLoadRunner() {
    }
}
