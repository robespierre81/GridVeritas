package com.gridveritas.core.service;

import com.gridveritas.core.domain.Aggregator;
import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.DerResource;
import com.gridveritas.core.domain.SettlementRecord;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.repository.AggregatorRepository;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.DerResourceRepository;
import com.gridveritas.core.repository.SettlementRecordRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.web.dto.ProofResponse;
import com.gridveritas.core.web.dto.SettlementDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SettlementService {

    private static final Set<String> ROLES = Set.of("AGGREGATOR", "UTILITY", "RTO");
    private static final Set<String> TYPES = Set.of("BATTERY", "EV", "LOAD", "SOLAR", "MIXED");

    private final AggregatorRepository aggregatorRepository;
    private final DerResourceRepository resourceRepository;
    private final SettlementRecordRepository settlementRepository;
    private final SourceRepository sourceRepository;
    private final AttestationRepository attestationRepository;
    private final MerkleService merkleService;
    private final AuditService auditService;

    public SettlementService(AggregatorRepository aggregatorRepository,
                             DerResourceRepository resourceRepository,
                             SettlementRecordRepository settlementRepository,
                             SourceRepository sourceRepository,
                             AttestationRepository attestationRepository,
                             MerkleService merkleService,
                             AuditService auditService) {
        this.aggregatorRepository = aggregatorRepository;
        this.resourceRepository = resourceRepository;
        this.settlementRepository = settlementRepository;
        this.sourceRepository = sourceRepository;
        this.attestationRepository = attestationRepository;
        this.merkleService = merkleService;
        this.auditService = auditService;
    }

    public Map<String, Object> mapping() {
        return Ferc2222Mapping.catalog();
    }

    @Transactional
    public SettlementDtos.AggregatorView createAggregator(SettlementDtos.AggregatorRequest req) {
        Aggregator a = new Aggregator();
        a.setName(req.name.trim());
        a.setPartyRole(normalize(req.partyRole, ROLES, "AGGREGATOR"));
        a.setCreatedAt(Instant.now());
        Aggregator saved = aggregatorRepository.save(a);
        auditService.recordAudit("AGGREGATOR_CREATED", saved.getId().toString(),
                "role=" + saved.getPartyRole());
        return toAggregator(saved);
    }

    @Transactional(readOnly = true)
    public List<SettlementDtos.AggregatorView> listAggregators() {
        return aggregatorRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(SettlementService::toAggregator)
                .toList();
    }

    @Transactional
    public SettlementDtos.ResourceView createResource(SettlementDtos.ResourceRequest req) {
        Aggregator aggregator = aggregatorRepository.findById(req.aggregatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "aggregator not found"));
        DerResource resource = new DerResource();
        resource.setAggregator(aggregator);
        resource.setName(req.name.trim());
        resource.setResourceType(normalize(req.resourceType, TYPES, "BATTERY"));
        resource.setExternalId(req.externalId == null ? null : req.externalId.trim());
        resource.setCreatedAt(Instant.now());
        Set<Source> sources = new HashSet<>();
        for (UUID sourceId : req.sourceIds) {
            sources.add(sourceRepository.findById(sourceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "source not found: " + sourceId)));
        }
        resource.setSources(sources);
        DerResource saved = resourceRepository.save(resource);
        auditService.recordAudit("DER_RESOURCE_CREATED", saved.getId().toString(),
                "sources=" + sources.size());
        return toResource(saved);
    }

    @Transactional(readOnly = true)
    public List<SettlementDtos.ResourceView> listResources() {
        return resourceRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(SettlementService::toResource)
                .toList();
    }

    @Transactional(readOnly = true)
    public SettlementDtos.ResourceView getResource(UUID id) {
        return toResource(resource(id));
    }

    @Transactional(readOnly = true)
    public List<Attestation> resourceAttestations(UUID resourceId) {
        DerResource resource = resource(resourceId);
        List<UUID> sourceIds = resource.getSources().stream().map(Source::getId).toList();
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return attestationRepository.findBySourceIdInOrderByTimestampDesc(sourceIds);
    }

    @Transactional
    public SettlementDtos.SettlementView createSettlement(SettlementDtos.SettlementRequest req) {
        if (!req.periodEnd.isAfter(req.periodStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodEnd must be after periodStart");
        }
        DerResource resource = resource(req.resourceId);
        List<UUID> sourceIds = resource.getSources().stream().map(Source::getId).toList();
        List<Attestation> window = sourceIds.isEmpty()
                ? List.of()
                : attestationRepository.findBySourceIdInAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
                        sourceIds, req.periodStart, req.periodEnd);
        SettlementRecord record = new SettlementRecord();
        record.setResource(resource);
        record.setPeriodStart(req.periodStart);
        record.setPeriodEnd(req.periodEnd);
        record.setMarket(req.market == null || req.market.isBlank() ? Ferc2222Mapping.MARKET : req.market.trim());
        record.setFormatName(Ferc2222Mapping.FORMAT_NAME);
        record.setCreatedAt(Instant.now());
        record.setAttestations(new HashSet<>(window));
        SettlementRecord saved = settlementRepository.save(record);
        auditService.recordAudit("SETTLEMENT_CREATED", saved.getId().toString(),
                "attestations=" + window.size());
        return toSettlement(saved);
    }

    @Transactional(readOnly = true)
    public List<SettlementDtos.SettlementView> listSettlements() {
        return settlementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSettlement)
                .toList();
    }

    @Transactional(readOnly = true)
    public SettlementDtos.SettlementView getSettlement(UUID id) {
        return toSettlement(settlementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "settlement not found")));
    }

    private DerResource resource(UUID id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "resource not found"));
    }

    private SettlementDtos.SettlementView toSettlement(SettlementRecord record) {
        SettlementDtos.SettlementView view = new SettlementDtos.SettlementView();
        view.id = record.getId();
        view.resourceId = record.getResource().getId();
        view.resourceName = record.getResource().getName();
        view.externalId = record.getResource().getExternalId();
        view.aggregatorId = record.getResource().getAggregator().getId();
        view.aggregatorName = record.getResource().getAggregator().getName();
        view.periodStart = record.getPeriodStart();
        view.periodEnd = record.getPeriodEnd();
        view.market = record.getMarket();
        view.formatName = record.getFormatName();
        view.disclaimer = (String) Ferc2222Mapping.catalog().get("disclaimer");
        view.createdAt = record.getCreatedAt();
        List<Attestation> ordered = record.getAttestations().stream()
                .sorted(Comparator.comparing(Attestation::getTimestamp))
                .toList();
        for (Attestation attestation : ordered) {
            SettlementDtos.IntervalView interval = new SettlementDtos.IntervalView();
            interval.attestationId = attestation.getId();
            interval.sourceId = attestation.getSource().getId();
            interval.payloadHash = attestation.getPayloadHash();
            interval.signatureValid = attestation.getSignatureValid();
            interval.datetimeBeginningUtc = attestation.getTimestamp();
            Instant next = attestation.getTimestamp().plus(Duration.ofHours(1));
            interval.datetimeEndingUtc = next.isAfter(record.getPeriodEnd()) ? record.getPeriodEnd() : next;
            try {
                ProofResponse proof = merkleService.buildProof(attestation.getId());
                interval.anchored = proof.isAnchored();
                interval.provenanceIntact = proof.getProvenanceIntact();
                interval.rootHash = proof.getRootHash();
                if (Boolean.TRUE.equals(interval.anchored)) {
                    view.anchoredCount++;
                }
                if (Boolean.TRUE.equals(interval.provenanceIntact)) {
                    view.provenanceIntactCount++;
                }
            } catch (Exception e) {
                interval.anchored = false;
            }
            view.intervals.add(interval);
        }
        view.attestationCount = view.intervals.size();
        return view;
    }

    private static SettlementDtos.AggregatorView toAggregator(Aggregator a) {
        SettlementDtos.AggregatorView v = new SettlementDtos.AggregatorView();
        v.id = a.getId();
        v.name = a.getName();
        v.partyRole = a.getPartyRole();
        v.createdAt = a.getCreatedAt();
        return v;
    }

    private static SettlementDtos.ResourceView toResource(DerResource r) {
        SettlementDtos.ResourceView v = new SettlementDtos.ResourceView();
        v.id = r.getId();
        v.aggregatorId = r.getAggregator().getId();
        v.aggregatorName = r.getAggregator().getName();
        v.name = r.getName();
        v.resourceType = r.getResourceType();
        v.externalId = r.getExternalId();
        v.sourceIds = r.getSources().stream().map(Source::getId).toList();
        v.createdAt = r.getCreatedAt();
        return v;
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported value: " + value);
        }
        return upper;
    }
}
