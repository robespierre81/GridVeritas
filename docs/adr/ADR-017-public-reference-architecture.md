# ADR-017 — Public reference-architecture publication package

**Status:** Accepted (Implemented; deposit recorded in `docs/public/RELEASE.txt`)
**Date:** August 2026

## Context

ODT ADR-015 called for publishing the architecture, ADRs, and threat model on
a public GitHub repository with a Zenodo DOI snapshot. That deposit is an
operator action (accounts, license choice, actual push). This ADR records
what is implemented **in the tree** so the deposit is mechanical.

## Decision

- Keep a publication bundle under `docs/public/`: standalone reference
  architecture, versioning policy, Zenodo metadata template.
- Root `CITATION.cff` for GitHub/Zenodo citation.
- `docs/public/package-release.sh` builds a dated zip of the public files
  (ADRs, threat model, M14 mapping, this package).
- Do **not** invent a DOI or a public GitHub URL. Those appear in
  `docs/public/RELEASE.txt` only after a real deposit.

## Residual

Deposit recorded 2026-08-18 from the existing Zenodo v1 record and GitHub
remote:

- https://github.com/robespierre81/GridVeritas
- https://doi.org/10.5281/zenodo.21820827

A later trust-model change needs a new tag and a new DOI version, not a
reuse of `10.5281/zenodo.21820827`. The GitHub remote was private at
record time (HTTPS 404); visibility is an operator setting on that repo.

## Numbering

ODT ADR-015 is this decision. This repository already used ADR-015 for
Postgres HA.
