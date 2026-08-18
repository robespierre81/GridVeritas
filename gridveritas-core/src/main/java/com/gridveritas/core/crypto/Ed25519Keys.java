package com.gridveritas.core.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Operator-level Ed25519 helpers for federation (M13). Distinct from
 * {@link Ed25519Verifier}, which only verifies source attestation signatures.
 */
public final class Ed25519Keys {

    private Ed25519Keys() {
    }

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 KeyPairGenerator unavailable", e);
        }
    }

    public static byte[] rawPublicKey(PublicKey publicKey) {
        byte[] x509 = publicKey.getEncoded();
        if (x509.length < 32) {
            throw new IllegalArgumentException("public key encoding too short");
        }
        byte[] raw = new byte[32];
        System.arraycopy(x509, x509.length - 32, raw, 0, 32);
        return raw;
    }

    public static String publicKeyBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(rawPublicKey(publicKey));
    }

    public static String sign(PrivateKey privateKey, byte[] message) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(message);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 sign failed", e);
        }
    }

    public static byte[] encodePrivate(PrivateKey privateKey) {
        return privateKey.getEncoded();
    }

    public static PrivateKey decodePrivate(byte[] pkcs8) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 private key decode failed", e);
        }
    }
}
