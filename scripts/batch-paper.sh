#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"

INPUT_DIR="${1:?Usage: batch-paper.sh <input-pdf-dir> [output-dir] [review-dir] [crf-model]}"
OUTPUT_DIR="${2:-$INPUT_DIR/output}"
REVIEW_DIR="${3:-$INPUT_DIR/review}"
CRF_MODEL="${4:-}"

if [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found. Run './scripts/build-java.sh' first."
    exit 1
fi

mkdir -p "$OUTPUT_DIR" "$REVIEW_DIR"

echo "=== Batch Paper Processing ==="
echo "Input: $INPUT_DIR"
echo "Output: $OUTPUT_DIR"
echo "Review: $REVIEW_DIR"
if [ -n "$CRF_MODEL" ]; then
    echo "CRF Model: $CRF_MODEL"
fi
echo ""

COUNT=0
SUCCESS=0
FAIL=0
for pdf in "$INPUT_DIR"/*.pdf; do
    [ -f "$pdf" ] || continue
    COUNT=$((COUNT + 1))
    name=$(basename "$pdf" .pdf)
    echo "[$COUNT] Processing: $name"

    EXTRA_ARGS="--paper-mode --paper-review-dir $REVIEW_DIR -o $OUTPUT_DIR"
    if [ -n "$CRF_MODEL" ]; then
        EXTRA_ARGS="$EXTRA_ARGS --paper-crf-model $CRF_MODEL"
    fi

    if java -jar "$JAR_PATH" $EXTRA_ARGS "$pdf" 2>/dev/null; then
        SUCCESS=$((SUCCESS + 1))
    else
        FAIL=$((FAIL + 1))
        echo "  ERROR processing $name"
    fi
done

echo ""
echo "=== Summary ==="
echo "Processed: $COUNT PDFs ($SUCCESS success, $FAIL failed)"
echo "Output files: $(ls "$OUTPUT_DIR"/*.paper.json 2>/dev/null | wc -l) paper.json"
echo "Review queue: $(ls "$REVIEW_DIR"/*.review.json 2>/dev/null | wc -l) items"
