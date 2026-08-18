package com.gridveritas.core.service;

import com.gridveritas.core.crypto.Ed25519Verifier;
import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.web.dto.AttestationRequest;
import com.gridveritas.core.web.dto.AttestationResponse;
import com.gridveritas.core.web.dto.VerifyRequest;
import com.gridveritas.core.web.dto.VerifyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AttestationService is the ingest and verify path the whole tamper-detection
 * story rests on: signature checking, replay rejection, and re-deriving the
 * signed message from STORED metadata (not the original request) so that a
 * post-hoc edit to a stored row is independently detectable. @DataJpaTest gives
 * a real (H2) repository layer without booting the full Spring context.
 */
@DataJpaTest
@ActiveProfiles("test")
class AttestationServiceTest {

    @Autowired
    private AttestationRepository attestationRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private AttestationService service;

    private record KeyMaterial(String publicKeyBase64, PrivateKey privateKey) {
    }

    private static KeyMaterial generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = gen.generateKeyPair();
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

    private static byte[] payloadHash(String seed) {
        byte[] out = new byte[32];
        byte[] src = seed.getBytes();
        for (int i = 0; i < out.length; i++) {
            out[i] = src[i % src.length];
        }
        return out;
    }

    private AttestationService service() {
        if (service == null) {
            service = new AttestationService(attestationRepository, sourceRepository);
        }
        return service;
    }

    private Source persistSource(String publicKeyBase64) {
        return sourceRepository.save(new Source("edge-agent-1", publicKeyBase64));
    }

    private AttestationRequest requestFor(UUID sourceId, long sequenceNr, long tsMillis,
                                          byte[] payloadHash, String signature) {
        AttestationRequest req = new AttestationRequest();
        req.setSourceId(sourceId);
        req.setSequenceNr(sequenceNr);
        req.setTimestampEpochMillis(tsMillis);
        req.setPayloadHash(HexFormat.of().formatHex(payloadHash));
        req.setSignature(signature);
        return req;
    }

    @Test
    void createStoresAValidlySignedAttestationAndComputesALeafHash() throws Exception {
        KeyMaterial key = generateKeyPair();
        Source source = persistSource(key.publicKeyBase64());
        byte[] hash = payloadHash("payload-1");
        byte[] message = Ed25519Verifier.canonicalAttestation(source.getId().toString(), 1, 1_700_000_000_000L, hash);
        String signature = sign(key.privateKey(), message);

        AttestationResponse response = service().create(requestFor(source.getId(), 1, 1_700_000_000_000L, hash, signature));

        assertThat(response.getSignatureValid()).isTrue();
        assertThat(response.getStatus()).isEqualTo("SIGNATURE_VALID");
        Attestation stored = attestationRepository.findById(response.getId()).orElseThrow();
        assertThat(stored.getLeafHash()).isNotBlank();
    }

    @Test
    void createStoresButFlagsAnInvalidSignature() throws Exception {
        KeyMaterial signer = generateKeyPair();
        KeyMaterial impostor = generateKeyPair();
        Source source = persistSource(signer.publicKeyBase64()); // source's real key
        byte[] hash = payloadHash("payload-2");
        byte[] message = Ed25519Verifier.canonicalAttestation(source.getId().toString(), 1, 1_700_000_000_000L, hash);
        String signature = sign(impostor.privateKey(), message); // signed with the WRONG key

        AttestationResponse response = service().create(requestFor(source.getId(), 1, 1_700_000_000_000L, hash, signature));

        assertThat(response.getSignatureValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("SIGNATURE_INVALID");
    }

    @Test
    void createRejectsADuplicateSequenceForTheSameSourceAsAConflict() throws Exception {
        KeyMaterial key = generateKeyPair();
        Source source = persistSource(key.publicKeyBase64());
        byte[] hash1 = payloadHash("payload-3a");
        byte[] message1 = Ed25519Verifier.canonicalAttestation(source.getId().toString(), 5, 1_700_000_000_000L, hash1);
        service().create(requestFor(source.getId(), 5, 1_700_000_000_000L, hash1, sign(key.privateKey(), message1)));

        byte[] hash2 = payloadHash("payload-3b"); // different payload, SAME sequence -> replay
        byte[] message2 = Ed25519Verifier.canonicalAttestation(source.getId().toString(), 5, 1_700_000_001_000L, hash2);
        AttestationRequest replay = requestFor(source.getId(), 5, 1_700_000_001_000L, hash2, sign(key.privateKey(), message2));

        assertThatThrownBy(() -> service().create(replay))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("Duplicate attestation");
    }

    @Test
    void createThrowsNotFoundForAnUnknownSource() {
        AttestationRequest req = requestFor(UUID.randomUUID(), 1, 1_700_000_000_000L, payloadHash("x"), "c2ln");

        assertThatThrownBy(() -> service().create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void verifyReturnsValidWhenTheStoredSignatureStillChecksOut() throws Exception {
        KeyMaterial key = generateKeyPair();
        Source source = persistSource(key.publicKeyBase64());
        byte[] hash = payloadHash("payload-4");
        byte[] message = Ed25519Verifier.canonicalAttestation(source.getId().toString(), 1, 1_700_000_000_000L, hash);
        service().create(requestFor(source.getId(), 1, 1_700_000_000_000L, hash, sign(key.privateKey(), message)));

        VerifyRequest verifyReq = new VerifyRequest();
        verifyReq.setPayloadHash(HexFormat.of().formatHex(hash));
        VerifyResponse result = service().verify(verifyReq);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSourceId()).isEqualTo(source.getId());
    }

    @Test
    void verifyDetectsTamperedStoredMetadataEvenThoughTheSignatureBytesAreUnchanged() throws Exception {
        KeyMaterial key = generateKeyPair();
        Source source = persistSource(key.publicKeyBase64());
        byte[] hash = payloadHash("payload-5");
        byte[] message = Ed25519Verifier.canonicalAttestation(source.getId().toString(), 1, 1_700_000_000_000L, hash);
        AttestationResponse created = service().create(
                requestFor(source.getId(), 1, 1_700_000_000_000L, hash, sign(key.privateKey(), message)));

        // Simulate a malicious operator rewriting the stored sequence directly in
        // the database, without re-signing - verify() rebuilds the canonical
        // message from what's NOW stored, so this must be caught.
        Attestation row = attestationRepository.findById(created.getId()).orElseThrow();
        row.setSequenceNr(999L);
        attestationRepository.save(row);

        VerifyRequest verifyReq = new VerifyRequest();
        verifyReq.setPayloadHash(HexFormat.of().formatHex(hash));
        VerifyResponse result = service().verify(verifyReq);

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void verifyReportsNotFoundWhenNoAttestationMatchesThePayloadHash() {
        VerifyRequest verifyReq = new VerifyRequest();
        verifyReq.setPayloadHash(HexFormat.of().formatHex(payloadHash("never-stored")));

        VerifyResponse result = service().verify(verifyReq);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("No attestation found");
    }

    @Test
    void listBySourceOrdersMostRecentFirst() throws Exception {
        KeyMaterial key = generateKeyPair();
        Source source = persistSource(key.publicKeyBase64());
        for (long seq = 1; seq <= 3; seq++) {
            byte[] hash = payloadHash("payload-order-" + seq);
            long ts = 1_700_000_000_000L + seq * 1000;
            byte[] message = Ed25519Verifier.canonicalAttestation(source.getId().toString(), seq, ts, hash);
            service().create(requestFor(source.getId(), seq, ts, hash, sign(key.privateKey(), message)));
        }

        List<AttestationResponse> results = service().listBySource(source.getId());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSequenceNr()).isEqualTo(3L);
        assertThat(results.get(2).getSequenceNr()).isEqualTo(1L);
    }

    @Test
    void createSourceAndListSourcesRoundTrip() {
        Source created = service().createSource("new-source", "some-public-key");

        assertThat(created.getId()).isNotNull();
        assertThat(service().listSources()).extracting(Source::getName).contains("new-source");
    }
}
