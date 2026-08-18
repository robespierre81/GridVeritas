package com.gridveritas.core.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Canonical signed message for a published Merkle root (M13).
 *
 * Same length-prefix construction as {@link Ed25519Verifier#canonicalAttestation}
 * so there is no delimiter ambiguity. Every operator and every peer must produce
 * identical bytes for the same fields.
 */
public final class FederationCanon {

    static final byte[] DOMAIN_TAG =
            "GridVeritas-Federation-Root-v1".getBytes(StandardCharsets.UTF_8);

    private FederationCanon() {
    }

    public static byte[] canonicalRoot(String operatorId,
                                       String rootHash,
                                       String prevRootHash,
                                       int leafCount,
                                       Instant computedAt) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId is required");
        }
        if (rootHash == null || rootHash.isBlank()) {
            throw new IllegalArgumentException("rootHash is required");
        }
        if (computedAt == null) {
            throw new IllegalArgumentException("computedAt is required");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeField(buf, DOMAIN_TAG);
        writeField(buf, operatorId.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        writeField(buf, rootHash.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        writeField(buf, (prevRootHash == null ? "" : prevRootHash.toLowerCase())
                .getBytes(StandardCharsets.US_ASCII));
        writeField(buf, ByteBuffer.allocate(8).putLong(leafCount).array());
        writeField(buf, ByteBuffer.allocate(8).putLong(computedAt.toEpochMilli()).array());
        return buf.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream buf, byte[] b) {
        buf.write((b.length >>> 24) & 0xFF);
        buf.write((b.length >>> 16) & 0xFF);
        buf.write((b.length >>> 8) & 0xFF);
        buf.write(b.length & 0xFF);
        buf.writeBytes(b);
    }
}
