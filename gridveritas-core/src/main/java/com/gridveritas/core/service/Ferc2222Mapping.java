package com.gridveritas.core.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static FERC Order No. 2222 MV&S mapping used by the M14 reference
 * workflow. This is a documented correspondence, not a PJM/ISO submission
 * schema and not a claim of certification.
 */
public final class Ferc2222Mapping {

    public static final String FORMAT_NAME = "PJM-PowerMeter-interval-reference-v1";
    public static final String MARKET = "PJM";

    private Ferc2222Mapping() {
    }

    public static Map<String, Object> catalog() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", "FERC Order No. 2222 (2020)");
        m.put("disclaimer",
                "Reference mapping only. GridVeritas does not execute market settlement, "
                        + "clear funds, or claim RTO/ISO certification or endorsement.");
        m.put("targetFormat", FORMAT_NAME);
        m.put("targetMarket", MARKET);
        m.put("targetFormatNote",
                "Shaped after PJM's publicly documented PowerMeter / hourly interval "
                        + "concepts (datetime_beginning_utc, datetime_ending_utc, resource id). "
                        + "Interval MW is not invented: this engine stores payload hashes and proofs, "
                        + "not meter registers.");
        m.put("evidentiaryMap", List.of(
                row("Source identity of the DER / meter",
                        "Source.publicKey + Ed25519 signature over the canonical attestation"),
                row("Measurement interval (what PJM calls datetime_beginning/ending_utc)",
                        "Attestation.src timestamp; settlement_records.period_start/period_end"),
                row("That the reading was not altered after the fact",
                        "Merkle inclusion proof + provenanceIntact vs the sealed leaf"),
                row("That the record existed by a given time, including against the operator",
                        "RFC 3161 anchor on the covering Merkle root"),
                row("Which aggregated resource the interval belongs to",
                        "der_resources + resource_sources (no duplicated attestation rows)"),
                row("Which market party presented the resource",
                        "aggregators.party_role (AGGREGATOR / UTILITY / RTO)")
        ));
        return m;
    }

    private static Map<String, String> row(String requirement, String gridveritas) {
        return Map.of("fercOrMarketNeed", requirement, "gridveritasPrimitive", gridveritas);
    }
}
