package com.gridveritas.core.service;

import com.gridveritas.core.domain.AnomalyFinding;
import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.repository.AnomalyFindingRepository;
import com.gridveritas.core.repository.AttestationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Deterministic tests of the three anomaly signals. No DB, no network:
 * repositories are mocked and crafted attestation lists drive each rule.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyDetectionServiceTest {

    @Mock
    AttestationRepository attestationRepository;

    @Mock
    AnomalyFindingRepository anomalyRepository;

    @Captor
    ArgumentCaptor<AnomalyFinding> findingCaptor;

    AnomalyDetectionService service;

    private final Source source = new Source("demo-source", "ZGVtbw==");

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionService(attestationRepository, anomalyRepository);
        ReflectionTestUtils.setField(service, "lookbackHours", 24L);
        ReflectionTestUtils.setField(service, "minSamples", 5);
        ReflectionTestUtils.setField(service, "invalidRatioThreshold", 0.2);
        ReflectionTestUtils.setField(service, "silenceThresholdMs", 300_000L); // 5 min
        ReflectionTestUtils.setField(source, "id", java.util.UUID.randomUUID());
        when(anomalyRepository.existsByDedupKey(anyString())).thenReturn(false);
    }

    private Attestation att(long seq, boolean valid, Instant createdAt) {
        Attestation a = new Attestation(source, "hash" + seq, Instant.now(), seq, "sig");
        a.setSignatureValid(valid);
        a.setCreatedAt(createdAt);
        return a;
    }

    @Test
    void detectsSequenceGap() {
        Instant now = Instant.now();
        // seq 1,2,5 -> missing 3,4
        when(attestationRepository.findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(any()))
                .thenReturn(List.of(att(1, true, now), att(2, true, now), att(5, true, now)));

        service.detect();

        verify(anomalyRepository, atLeastOnce()).save(findingCaptor.capture());
        assertThat(findingCaptor.getAllValues())
                .anySatisfy(f -> {
                    assertThat(f.getType()).isEqualTo("SEQUENCE_GAP");
                    assertThat(f.getDedupKey()).contains("3-4");
                    assertThat(f.getMetricValue()).isEqualTo(2.0);
                });
    }

    @Test
    void detectsInvalidSignatureSpike() {
        Instant now = Instant.now();
        // 5 contiguous attestations, all invalid -> ratio 1.0 >= 0.2, CRITICAL
        when(attestationRepository.findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(any()))
                .thenReturn(List.of(
                        att(1, false, now), att(2, false, now), att(3, false, now),
                        att(4, false, now), att(5, false, now)));

        service.detect();

        verify(anomalyRepository, atLeastOnce()).save(findingCaptor.capture());
        assertThat(findingCaptor.getAllValues())
                .anySatisfy(f -> {
                    assertThat(f.getType()).isEqualTo("SIGNATURE_INVALID_SPIKE");
                    assertThat(f.getSeverity()).isEqualTo("CRITICAL");
                    assertThat(f.getMetricValue()).isEqualTo(1.0);
                });
    }

    @Test
    void detectsSourceSilence() {
        Instant now = Instant.now();
        Instant stale = now.minus(10, ChronoUnit.MINUTES); // > 5 min threshold
        when(attestationRepository.findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(any()))
                .thenReturn(List.of(att(1, true, stale), att(2, true, stale)));

        service.detect();

        verify(anomalyRepository, atLeastOnce()).save(findingCaptor.capture());
        assertThat(findingCaptor.getAllValues())
                .anySatisfy(f -> assertThat(f.getType()).isEqualTo("SOURCE_SILENCE"));
    }

    @Test
    void noFindingsForHealthyStream() {
        Instant now = Instant.now();
        // contiguous, all valid, fresh -> no gap, ratio 0, not silent
        when(attestationRepository.findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(any()))
                .thenReturn(List.of(
                        att(1, true, now), att(2, true, now), att(3, true, now),
                        att(4, true, now), att(5, true, now)));

        service.detect();

        verify(anomalyRepository, never()).save(any());
    }

    @Test
    void doesNotRecordDuplicateFindings() {
        Instant now = Instant.now();
        when(anomalyRepository.existsByDedupKey(anyString())).thenReturn(true); // already recorded
        when(attestationRepository.findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(any()))
                .thenReturn(List.of(att(1, true, now), att(2, true, now), att(5, true, now)));

        service.detect();

        verify(anomalyRepository, never()).save(any());
    }
}
