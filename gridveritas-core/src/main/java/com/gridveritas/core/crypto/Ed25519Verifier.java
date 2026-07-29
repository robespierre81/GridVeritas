package com.gridveritas.core.crypto;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verifies Ed25519 signatures produced by the GridVeritas edge agent.
 *
 * Contract with the Go edge agent:
 * - publicKey: Base64 of raw 32-byte Ed25519 public key
 * - signature: Base64 of 64-byte Ed25519 signature
 * - message:   raw SHA-256 digest (32 bytes) of the payload
 *              (payloadHash is the hex encoding of that digest)
 */
public final class Ed25519Verifier {

    /** X.509 SubjectPublicKeyInfo prefix for a raw Ed25519 public key (RFC 8410). */
    private static final byte[] ED25519_SPKI_PREFIX = HexFormat.of().parseHex(
            "302a300506032b6570032100"
    );

    private Ed25519Verifier() {
    }

    /**
     * @param publicKeyBase64 Base64-encoded raw 32-byte Ed25519 public key
     * @param payloadHashHex  hex-encoded SHA-256 of the original payload
     * @param signatureBase64 Base64-encoded 64-byte signature over the raw hash bytes
     * @return true if the signature is valid
     */
    public static boolean verify(String publicKeyBase64, String payloadHashHex, String signatureBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()
                || payloadHashHex == null || payloadHashHex.isBlank()
                || signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            byte[] rawPub = Base64.getDecoder().decode(publicKeyBase64.trim());
            byte[] signature = Base64.getDecoder().decode(signatureBase64.trim());
            byte[] message = HexFormat.of().parseHex(payloadHashHex.trim());

            if (rawPub.length != 32) {
                return false;
            }
            if (signature.length != 64) {
                return false;
            }
            if (message.length != 32) {
                return false;
            }

            byte[] x509 = new byte[ED25519_SPKI_PREFIX.length + rawPub.length];
            System.arraycopy(ED25519_SPKI_PREFIX, 0, x509, 0, ED25519_SPKI_PREFIX.length);
            System.arraycopy(rawPub, 0, x509, ED25519_SPKI_PREFIX.length, rawPub.length);

            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(x509));

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
