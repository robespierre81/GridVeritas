package com.gridveritas.core.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signature check is the whole spoofing defense (Architecture Baseline
 * 3.5 / THREAT_MODEL.md "Spoofing"): an attacker without the source's private
 * key must not be able to produce a signature that verifies, and the operator
 * must not be able to alter any signed field (sourceId/sequence/timestamp/
 * payload hash) without invalidating it. No Spring context, no DB - this is
 * pure crypto math.
 */
class Ed25519VerifierTest {

    private static final byte[] PAYLOAD_HASH = sha256ish("payload-1");

    private static byte[] sha256ish(String s) {
        // Any 32 fixed bytes will do for these tests - canonicalAttestation
        // doesn't care about the hash's provenance, only its bytes.
        byte[] out = new byte[32];
        byte[] src = s.getBytes();
        for (int i = 0; i < out.length; i++) {
            out[i] = src[i % src.length];
        }
        return out;
    }

    private record KeyMaterial(String publicKeyBase64, PrivateKey privateKey) {
    }

    private static KeyMaterial generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = gen.generateKeyPair();
        // Ed25519Verifier expects the raw 32-byte public key, not the full
        // X.509 SubjectPublicKeyInfo encoding - strip the fixed 12-byte
        // prefix the JDK's X509EncodedKeySpec produces for Ed25519.
        byte[] x509 = pair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(x509, x509.length - 32, raw, 0, 32);
        return new KeyMaterial(Base64.getEncoder().encodeToString(raw), pair.getPrivate());
    }

    private static String sign(PrivateKey privateKey, byte[] message) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(message);
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    @Test
    void validSignatureVerifies() throws Exception {
        KeyMaterial key = generateKeyPair();
        byte[] message = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 1, 1_700_000_000_000L, PAYLOAD_HASH);
        String signature = sign(key.privateKey(), message);

        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), message, signature)).isTrue();
    }

    @Test
    void signatureFromAWrongKeyDoesNotVerify() throws Exception {
        KeyMaterial signer = generateKeyPair();
        KeyMaterial impostor = generateKeyPair();
        byte[] message = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 1, 1_700_000_000_000L, PAYLOAD_HASH);
        String signature = sign(signer.privateKey(), message);

        // Checked against the impostor's public key, not the actual signer's.
        assertThat(Ed25519Verifier.verify(impostor.publicKeyBase64(), message, signature)).isFalse();
    }

    @Test
    void alteringAnyBoundFieldInvalidatesTheSignature() throws Exception {
        KeyMaterial key = generateKeyPair();
        byte[] original = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 5, 1_700_000_000_000L, PAYLOAD_HASH);
        String signature = sign(key.privateKey(), original);

        // A malicious operator rewriting the stored sequence, timestamp, or
        // source after the fact must be detectable - each must independently
        // break verification against the original signature.
        byte[] alteredSequence = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 6, 1_700_000_000_000L, PAYLOAD_HASH);
        byte[] alteredTimestamp = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 5, 1_700_000_000_001L, PAYLOAD_HASH);
        byte[] alteredSource = Ed25519Verifier.canonicalAttestation(
                "22222222-2222-2222-2222-222222222222", 5, 1_700_000_000_000L, PAYLOAD_HASH);
        byte[] alteredHash = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 5, 1_700_000_000_000L, sha256ish("different-payload"));

        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), alteredSequence, signature)).isFalse();
        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), alteredTimestamp, signature)).isFalse();
        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), alteredSource, signature)).isFalse();
        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), alteredHash, signature)).isFalse();
        // Sanity: the untouched original still verifies with the same signature.
        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), original, signature)).isTrue();
    }

    @Test
    void tamperedSignatureBytesDoNotVerify() throws Exception {
        KeyMaterial key = generateKeyPair();
        byte[] message = Ed25519Verifier.canonicalAttestation(
                "11111111-1111-1111-1111-111111111111", 1, 1_700_000_000_000L, PAYLOAD_HASH);
        byte[] signatureBytes = Base64.getDecoder().decode(sign(key.privateKey(), message));
        signatureBytes[0] ^= 0x01; // flip one bit
        String tampered = Base64.getEncoder().encodeToString(signatureBytes);

        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), message, tampered)).isFalse();
    }

    @Test
    void malformedInputsFailClosedInsteadOfThrowing() {
        assertThat(Ed25519Verifier.verify(null, "msg".getBytes(), "c2ln")).isFalse();
        assertThat(Ed25519Verifier.verify("", "msg".getBytes(), "c2ln")).isFalse();
        assertThat(Ed25519Verifier.verify("bm90LWJhc2U2NA==", "msg".getBytes(), "not valid base64!!")).isFalse();
        assertThat(Ed25519Verifier.verify("bm90LWJhc2U2NA==", "msg".getBytes(), "")).isFalse();
        assertThat(Ed25519Verifier.verify("bm90LWJhc2U2NA==", new byte[0], "c2ln")).isFalse();
        // Wrong-length key/signature (valid base64, wrong byte count).
        assertThat(Ed25519Verifier.verify(
                Base64.getEncoder().encodeToString(new byte[16]), "msg".getBytes(),
                Base64.getEncoder().encodeToString(new byte[64]))).isFalse();
    }

    @Test
    void sourceIdIsCaseFoldedBeforeSigning() throws Exception {
        KeyMaterial key = generateKeyPair();
        byte[] lower = Ed25519Verifier.canonicalAttestation(
                "abcdef01-1111-1111-1111-111111111111", 1, 1_700_000_000_000L, PAYLOAD_HASH);
        byte[] upper = Ed25519Verifier.canonicalAttestation(
                "ABCDEF01-1111-1111-1111-111111111111", 1, 1_700_000_000_000L, PAYLOAD_HASH);

        // Both must produce the identical signed message, so a signature made
        // against one form verifies against the other too (the core ingest
        // path is expected to normalize case consistently either way).
        assertThat(lower).isEqualTo(upper);
        String signature = sign(key.privateKey(), lower);
        assertThat(Ed25519Verifier.verify(key.publicKeyBase64(), upper, signature)).isTrue();
    }
}
