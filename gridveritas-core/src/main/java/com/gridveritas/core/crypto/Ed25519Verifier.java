package com.gridveritas.core.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verifies Ed25519 signatures produced by the GridVeritas edge agent.
 *
 * The signature is computed over a CANONICAL attestation message that binds the
 * payload hash together with its metadata (source identity, sequence, timestamp).
 * This prevents an operator from altering stored metadata (timestamp / sequence /
 * source) without invalidating the signature.
 *
 * Contract with the Go edge agent:
 *  - publicKey: Base64 of raw 32-byte Ed25519 public key
 *  - signature: Base64 of 64-byte Ed25519 signature over canonicalAttestation(...)
 *  - the canonical message format MUST be reproduced byte-for-byte by every client
 *    (see Architecture Baseline 3.5).
 */
public final class Ed25519Verifier {

    /** X.509 SubjectPublicKeyInfo prefix for a raw Ed25519 public key (RFC 8410). */
    private static final byte[] ED25519_SPKI_PREFIX =
            HexFormat.of().parseHex("302a300506032b6570032100");

    /** Domain separation tag + version for the signed message. */
    private static final byte[] DOMAIN_TAG =
            "GridVeritas-Attestation-v1".getBytes(StandardCharsets.UTF_8);

    private Ed25519Verifier() {
    }

    /**
     * Builds the exact bytes that are signed. Each field is length-prefixed
     * (4-byte big-endian length + bytes), so there is no delimiter ambiguity or
     * field injection. Go and Java produce identical bytes for the same inputs.
     *
     * @param sourceId       canonical lowercase UUID string of the source
     * @param sequence       per-source monotonic sequence number
     * @param tsEpochMillis  source-claimed timestamp as epoch milliseconds
     * @param payloadHashRaw raw bytes of the payload hash (SHA-256, 32 bytes)
     */
    public static byte[] canonicalAttestation(String sourceId, long sequence,
                                              long tsEpochMillis, byte[] payloadHashRaw) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeField(buf, DOMAIN_TAG);
        writeField(buf, sourceId.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        writeField(buf, ByteBuffer.allocate(8).putLong(sequence).array());
        writeField(buf, ByteBuffer.allocate(8).putLong(tsEpochMillis).array());
        writeField(buf, payloadHashRaw);
        return buf.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream buf, byte[] b) {
        buf.write((b.length >>> 24) & 0xFF);
        buf.write((b.length >>> 16) & 0xFF);
        buf.write((b.length >>> 8) & 0xFF);
        buf.write(b.length & 0xFF);
        buf.writeBytes(b);
    }

    /**
     * Verify an Ed25519 signature over an arbitrary canonical message.
     *
     * @param publicKeyBase64 Base64-encoded raw 32-byte Ed25519 public key
     * @param message         the canonical message bytes that were signed
     * @param signatureBase64 Base64-encoded 64-byte signature
     * @return true if the signature is valid
     */
    public static boolean verify(String publicKeyBase64, byte[] message, String signatureBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()
                || message == null || message.length == 0
                || signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            byte[] rawPub = Base64.getDecoder().decode(publicKeyBase64.trim());
            byte[] signature = Base64.getDecoder().decode(signatureBase64.trim());
            if (rawPub.length != 32 || signature.length != 64) {
                return false;
            }
            PublicKey publicKey = buildPublicKey(rawPub);

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static PublicKey buildPublicKey(byte[] rawPub) throws Exception {
        byte[] x509 = new byte[ED25519_SPKI_PREFIX.length + rawPub.length];
        System.arraycopy(ED25519_SPKI_PREFIX, 0, x509, 0, ED25519_SPKI_PREFIX.length);
        System.arraycopy(rawPub, 0, x509, ED25519_SPKI_PREFIX.length, rawPub.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
    }
}
