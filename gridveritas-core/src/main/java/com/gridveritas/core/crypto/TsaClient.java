package com.gridveritas.core.crypto;

import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * RFC 3161 Time-Stamp Protocol client. Sends only the (already-hashed) Merkle root
 * to a public TSA and returns the signed timestamp token. The TSA never sees any
 * attestation data — only the 32-byte root digest.
 */
@Component
public class TsaClient {

    /** Result of a timestamping request. */
    public record Result(String authority, byte[] token, Instant genTime, String serialNumber) {
    }

    private final String tsaUrl;
    private final HttpClient http;
    private final SecureRandom random = new SecureRandom();

    public TsaClient(@Value("${gridveritas.anchor.tsa-url:https://freetsa.org/tsr}") String tsaUrl,
                     @Value("${gridveritas.anchor.tsa-timeout-ms:10000}") long timeoutMs) {
        this.tsaUrl = tsaUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    /**
     * Timestamp a SHA-256 digest (e.g. a Merkle root hash). Verifies the response's
     * nonce/status and that the token's message imprint equals the digest we sent.
     *
     * Note: this does not yet verify the TSA's signature chain (needs the TSA CA
     * cert) — a documented follow-up. The token is stored verbatim for later,
     * independent verification.
     *
     * @param sha256Digest raw 32-byte SHA-256 digest
     */
    public Result timestamp(byte[] sha256Digest) throws Exception {
        if (sha256Digest == null || sha256Digest.length != 32) {
            throw new IllegalArgumentException("Expected a 32-byte SHA-256 digest");
        }

        TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
        gen.setCertReq(true);
        BigInteger nonce = new BigInteger(64, random);
        TimeStampRequest request = gen.generate(TSPAlgorithms.SHA256, sha256Digest, nonce);

        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(tsaUrl))
                .header("Content-Type", "application/timestamp-query")
                .header("Accept", "application/timestamp-reply")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.getEncoded()))
                .build();

        HttpResponse<byte[]> httpResp = http.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());
        if (httpResp.statusCode() != 200) {
            throw new IllegalStateException("TSA HTTP status " + httpResp.statusCode());
        }

        TimeStampResponse response = new TimeStampResponse(httpResp.body());
        response.validate(request); // checks status + nonce match

        TimeStampToken token = response.getTimeStampToken();
        if (token == null) {
            throw new IllegalStateException("TSA returned no token: " + response.getStatusString());
        }

        byte[] imprint = token.getTimeStampInfo().getMessageImprintDigest();
        if (!Arrays.equals(imprint, sha256Digest)) {
            throw new IllegalStateException("TSA message imprint does not match the submitted digest");
        }

        Instant genTime = token.getTimeStampInfo().getGenTime().toInstant();
        String serial = token.getTimeStampInfo().getSerialNumber().toString();
        return new Result(tsaUrl, token.getEncoded(), genTime, serial);
    }
}
