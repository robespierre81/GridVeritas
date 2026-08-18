# ADR-009 — Anchor token verification & trust-pinning

**Status:** Accepted (Implemented)  
**Date:** July–August 2026

## Context

Simply storing an RFC 3161 token is insufficient. A MITM could substitute a self-consistent but bogus token if only the message imprint is checked.

## Decision

Verify each RFC 3161 anchor token for:
- Message imprint against the Merkle root
- CMS signature against the embedded signer certificate
- Timestamping Extended Key Usage (`id-kp-timeStamping`)

Optionally trust-pin the signer to a configured TSA CA via PKIX path building.  
Verification runs both at acquisition (unverifiable tokens are never stored) and on proof retrieval.

## Consequences

### Positive
- Closes the MITM substitution attack
- Makes the anchor’s own provenance verifiable when a CA is configured
- Consistent verification at write and read time

### Negative / Trade-offs
- Without a configured TSA CA, integrity is verified but not trust-pinned (logged at startup)
- Revocation checking (CRL/OCSP) is deferred

## Later enhancements
- CRL/OCSP revocation checking
- Multiple pinned CAs for multi-TSA anchoring

## Validation (August 2026)
`TsaVerifier` is covered by `TsaVerifierTest` (see ADR-012): real, self-minted
BouncyCastle timestamp tokens exercise message-imprint mismatch, tampered/
malformed token bytes, and both directions of trust-pinning (trusted when the
signer is the pinned anchor, fails closed when it is not) against genuine
ASN.1/CMS structures rather than mocks.
