#!/bin/sh
# Build a dated documentation snapshot for a Zenodo/GitHub deposit (M15).
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
stamp=$(date +%Y%m%d)
out="$root/dist/gridveritas-reference-$stamp.zip"
mkdir -p "$root/dist"
rm -f "$out"
(
  cd "$root"
  zip -r "$out" \
    docs/public/REFERENCE_ARCHITECTURE.md \
    docs/public/VERSIONING.md \
    docs/public/README.md \
    docs/public/RELEASE.txt \
    docs/public/.zenodo.json \
    docs/adr \
    docs/THREAT_MODEL.md \
    docs/m14-ferc-2222-mvs-mapping.md \
    CITATION.cff
)
echo "wrote $out"
