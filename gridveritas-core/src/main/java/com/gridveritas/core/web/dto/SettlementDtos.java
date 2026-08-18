package com.gridveritas.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SettlementDtos {

    private SettlementDtos() {
    }

    public static class AggregatorRequest {
        @NotBlank
        public String name;
        public String partyRole;
    }

    public static class AggregatorView {
        public UUID id;
        public String name;
        public String partyRole;
        public Instant createdAt;
    }

    public static class ResourceRequest {
        @NotNull
        public UUID aggregatorId;
        @NotBlank
        public String name;
        public String resourceType;
        public String externalId;
        @NotEmpty
        public List<UUID> sourceIds;
    }

    public static class ResourceView {
        public UUID id;
        public UUID aggregatorId;
        public String aggregatorName;
        public String name;
        public String resourceType;
        public String externalId;
        public List<UUID> sourceIds = new ArrayList<>();
        public Instant createdAt;
    }

    public static class SettlementRequest {
        @NotNull
        public UUID resourceId;
        @NotNull
        public Instant periodStart;
        @NotNull
        public Instant periodEnd;
        public String market;
    }

    public static class IntervalView {
        public Instant datetimeBeginningUtc;
        public Instant datetimeEndingUtc;
        public UUID attestationId;
        public UUID sourceId;
        public String payloadHash;
        public Boolean signatureValid;
        public Boolean anchored;
        public Boolean provenanceIntact;
        public String rootHash;
    }

    public static class SettlementView {
        public UUID id;
        public UUID resourceId;
        public String resourceName;
        public String externalId;
        public UUID aggregatorId;
        public String aggregatorName;
        public Instant periodStart;
        public Instant periodEnd;
        public String market;
        public String formatName;
        public String disclaimer;
        public Instant createdAt;
        public List<IntervalView> intervals = new ArrayList<>();
        public int attestationCount;
        public int anchoredCount;
        public int provenanceIntactCount;
    }
}
