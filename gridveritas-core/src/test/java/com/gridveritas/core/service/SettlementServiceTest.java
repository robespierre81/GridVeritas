package com.gridveritas.core.service;

import com.gridveritas.core.crypto.TsaVerifier;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.repository.AggregatorRepository;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.AuditLogRepository;
import com.gridveritas.core.repository.DerResourceRepository;
import com.gridveritas.core.repository.MerkleLeafRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.repository.SettlementRecordRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.repository.VerificationEventRepository;
import com.gridveritas.core.web.dto.AttestationRequest;
import com.gridveritas.core.web.dto.SettlementDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class SettlementServiceTest {

    @Autowired
    private AggregatorRepository aggregatorRepository;
    @Autowired
    private DerResourceRepository resourceRepository;
    @Autowired
    private SettlementRecordRepository settlementRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private AttestationRepository attestationRepository;
    @Autowired
    private MerkleRootRepository merkleRootRepository;
    @Autowired
    private MerkleLeafRepository merkleLeafRepository;
    @Autowired
    private AnchorRepository anchorRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private VerificationEventRepository verificationEventRepository;

    private SettlementService settlementService;
    private AttestationService attestationService;

    @BeforeEach
    void setUp() {
        attestationService = new AttestationService(attestationRepository, sourceRepository);
        MerkleService merkleService = new MerkleService(
                attestationRepository, merkleRootRepository, merkleLeafRepository,
                anchorRepository, new TsaVerifier(""));
        ReflectionTestUtils.setField(merkleService, "maxBatch", 1000);
        settlementService = new SettlementService(
                aggregatorRepository, resourceRepository, settlementRepository,
                sourceRepository, attestationRepository, merkleService,
                new AuditService(verificationEventRepository, auditLogRepository));
    }

    @Test
    void mappingIsReferenceOnlyAndNamesPjm() {
        var catalog = settlementService.mapping();
        assertThat(catalog.get("targetMarket")).isEqualTo("PJM");
        assertThat((String) catalog.get("disclaimer")).contains("does not execute market settlement");
        assertThat((String) catalog.get("disclaimer")).doesNotContain("certified");
    }

    @Test
    void settlementSelectsOnlyAttestationsInThePeriod() throws Exception {
        Source source = source("meter-1");
        Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
        ingest(source, 1L, t0.minusSeconds(3600));
        ingest(source, 2L, t0);
        ingest(source, 3L, t0.plusSeconds(1800));
        ingest(source, 4L, t0.plusSeconds(7200));

        SettlementDtos.AggregatorRequest aggReq = new SettlementDtos.AggregatorRequest();
        aggReq.name = "Demo Aggregator";
        aggReq.partyRole = "AGGREGATOR";
        var aggregator = settlementService.createAggregator(aggReq);

        SettlementDtos.ResourceRequest resReq = new SettlementDtos.ResourceRequest();
        resReq.aggregatorId = aggregator.id;
        resReq.name = "Battery-01";
        resReq.resourceType = "BATTERY";
        resReq.externalId = "PJM-REF-BESS-01";
        resReq.sourceIds = List.of(source.getId());
        var resource = settlementService.createResource(resReq);

        SettlementDtos.SettlementRequest setReq = new SettlementDtos.SettlementRequest();
        setReq.resourceId = resource.id;
        setReq.periodStart = t0;
        setReq.periodEnd = t0.plusSeconds(3600);
        var settlement = settlementService.createSettlement(setReq);

        assertThat(settlement.attestationCount).isEqualTo(2);
        assertThat(settlement.intervals).extracting(i -> i.attestationId).hasSize(2);
        assertThat(settlement.formatName).isEqualTo(Ferc2222Mapping.FORMAT_NAME);
        assertThat(settlement.intervals.get(0).datetimeBeginningUtc).isEqualTo(t0);

        var listed = settlementService.resourceAttestations(resource.id);
        assertThat(listed).hasSize(4);

        assertThat(settlementService.getSettlement(settlement.id).id).isEqualTo(settlement.id);
    }

    @Test
    void periodMustBeForward() {
        SettlementDtos.SettlementRequest req = new SettlementDtos.SettlementRequest();
        req.resourceId = UUID.randomUUID();
        req.periodStart = Instant.parse("2026-08-02T00:00:00Z");
        req.periodEnd = Instant.parse("2026-08-01T00:00:00Z");
        assertThatThrownBy(() -> settlementService.createSettlement(req))
                .hasMessageContaining("periodEnd");
    }

    private Source source(String name) throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] x509 = pair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(x509, x509.length - 32, raw, 0, 32);
        Source source = new Source(name, Base64.getEncoder().encodeToString(raw));
        source.setCreatedAt(Instant.now());
        return sourceRepository.save(source);
    }

    private void ingest(Source source, long sequence, Instant timestamp) throws Exception {
        AttestationRequest req = new AttestationRequest();
        req.setSourceId(source.getId());
        req.setSequenceNr(sequence);
        req.setTimestampEpochMillis(timestamp.toEpochMilli());
        req.setPayloadHash("aa".repeat(32));
        req.setSignature("not-verified");
        attestationService.create(req);
    }
}
