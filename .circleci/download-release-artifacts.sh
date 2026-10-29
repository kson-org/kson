#!/usr/bin/env bash
# Download every artifact of one CircleCI run into <out-dir>/release-artifacts/ and verify the checksums.
#   download-release-artifacts.sh <run-id | branch> [out-dir]
# A branch picks its latest run. Needs the CircleCI CLI (1.11 or newer), logged in.
set -euo pipefail

project=gh/kson-org/kson
ref=${1:?usage: $0 <run-id | branch> [out-dir]}
if [[ $ref =~ ^[0-9a-f-]{36}$ ]]; then run=("$ref"); else run=(--project "$project" --branch "$ref"); fi
circleci run get "${run[@]}" --json --jq '"Run \(.id) at \(.revision): \(.current_outcome // .phase)"'

mkdir -p "${2:-.}"
out=$(cd "${2:-.}" && pwd)
dest=$out/release-artifacts
if [[ -e $dest ]]; then
  echo "$dest exists; remove it first so nothing from another run lingers" >&2
  exit 1
fi

while read -r id name outcome; do
  echo "== $name ($outcome)"
  if listing=$(circleci artifact "$id" --json 2>&1 </dev/null); then
    circleci artifact "$id" -o "$out" >/dev/null </dev/null
  elif [[ $listing == *artifacts.not_found* ]]; then
    echo "   no artifacts"
  else
    echo "$listing" >&2
    exit 1
  fi
done < <(circleci run get "${run[@]}" --json --jq '.workflows[].jobs[] | "\(.id) \(.name) \(.outcome // .phase)"')

cd "$dest"
if command -v sha256sum >/dev/null; then check=(sha256sum -c); else check=(shasum -a 256 -c); fi
status=0
while IFS= read -r -d '' sum; do
  (cd "$(dirname "$sum")" && "${check[@]}" "$(basename "$sum")") || status=1
done < <(find . -name '*.sha256' -print0 | sort -z)
echo
echo "Downloaded into $dest:"
find . -type f ! -name '*.sha256' | sort
exit $status
