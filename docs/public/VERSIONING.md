# Versioning the public reference architecture

- **Document version** (Architecture Baseline / this package): `MAJOR.MINOR`
  when the trust model or the implemented milestone set changes.
- **ADRs** are append-only. A changed decision gets a new ADR that supersedes
  the old one; the old file stays.
- **Code** stays on git tags (`v0.x.y`). A public documentation snapshot
  should name the git tag it describes.
- **Zenodo**: one DOI per tagged documentation snapshot. Do not reuse a DOI
  when the trust-model text changes.

Process: tag the repo → run `docs/public/package-release.sh` → upload the zip
to Zenodo → write the DOI into `docs/public/RELEASE.txt` → commit that line.
