#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found at $JAR_PATH"
    echo "Run './scripts/build-java.sh' first."
    exit 1
fi

echo "=== Paper Mode Benchmark ==="
echo ""

for pdf in "$ROOT_DIR"/paper_test*.pdf; do
    [ -f "$pdf" ] || continue
    name=$(basename "$pdf" .pdf)
    outdir=$(mktemp -d)
    echo "Processing: $name"
    java -jar "$JAR_PATH" --paper-mode -o "$outdir" "$pdf" 2>/dev/null

    if [ -f "$outdir/$name.paper.json" ]; then
        echo "  paper.json generated ($(wc -c < "$outdir/$name.paper.json") bytes)"
        python3 -c "
import json, sys
with open('$outdir/$name.paper.json') as f:
    d = json.load(f)
print(f'  Title: {d.get(\"title\", \"MISSING\")[:80]}')
print(f'  Authors: {len(d.get(\"authors\", []))}')
print(f'  Abstract: {\"YES\" if d.get(\"abstract\") else \"MISSING\"}')
print(f'  DOI: {d.get(\"doi\", \"MISSING\")}')
print(f'  Keywords: {len(d.get(\"keywords\", []))}')
print(f'  Sections: {len(d.get(\"sections\", []))}')
print(f'  References: {len(d.get(\"references\", []))}')
conf = d.get('metadata', {}).get('confidence', {})
if conf:
    print(f'  Confidence: {conf}')
" 2>/dev/null || echo "  (python3 not available for JSON parsing)"
    else
        echo "  paper.json NOT generated"
    fi

    if [ -f "$outdir/$name.paper.md" ]; then
        echo "  paper.md generated ($(wc -c < "$outdir/$name.paper.md") bytes)"
    fi
    echo ""
done

echo "=== Done ==="
