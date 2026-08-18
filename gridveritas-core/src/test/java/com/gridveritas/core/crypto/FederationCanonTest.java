package com.gridveritas.core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FederationCanonTest {

    @Test
    void sameInputsProduceIdenticalBytes() {
        UUID operator = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Instant at = Instant.parse("2026-08-18T12:00:00Z");
        byte[] a = FederationCanon.canonicalRoot(operator.toString(),
                "AB" + "cd".repeat(31), "00".repeat(32), 4, at);
        byte[] b = FederationCanon.canonicalRoot(operator.toString().toUpperCase(),
                "ab" + "CD".repeat(31), "00".repeat(32), 4, at);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void domainTagIsPresent() {
        byte[] msg = FederationCanon.canonicalRoot(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "aa".repeat(32), null, 1, Instant.EPOCH);
        String asLatin = new String(msg, StandardCharsets.ISO_8859_1);
        assertThat(asLatin).contains("GridVeritas-Federation-Root-v1");
    }

    @Test
    void changingAnyFieldChangesTheBytes() {
        Instant at = Instant.parse("2026-08-18T12:00:00Z");
        String op = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String hash = "ab".repeat(32);
        byte[] base = FederationCanon.canonicalRoot(op, hash, null, 1, at);
        assertThat(FederationCanon.canonicalRoot(op, "cd".repeat(32), null, 1, at)).isNotEqualTo(base);
        assertThat(FederationCanon.canonicalRoot(op, hash, "00".repeat(32), 1, at)).isNotEqualTo(base);
        assertThat(FederationCanon.canonicalRoot(op, hash, null, 2, at)).isNotEqualTo(base);
        assertThat(FederationCanon.canonicalRoot(op, hash, null, 1, at.plusSeconds(1))).isNotEqualTo(base);
    }
}
