#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"
TRAINING_DIR="${1:?Usage: train-crf.sh <training-data-dir> <output-model-path>}"
MODEL_PATH="${2:?Usage: train-crf.sh <training-data-dir> <output-model-path>}"

if [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found. Run './scripts/build-java.sh' first."
    exit 1
fi

echo "=== CRF Training ==="
echo "Training data: $TRAINING_DIR"
echo "Output model: $MODEL_PATH"

COUNT=$(find "$TRAINING_DIR" -name "*.review.json" 2>/dev/null | wc -l)
echo "Found $COUNT review JSON files"

if [ "$COUNT" -lt 10 ]; then
    echo "WARNING: Fewer than 10 training documents. Model quality may be poor."
fi

echo "Training not yet automated via CLI. Use the Java API:"
echo "  1. Load review JSONs with TrainingDataConverter.fromReviewJson()"
echo "  2. Create LabeledZone lists from the labeled data"
echo "  3. Call CRFClassifier.train() and CRFClassifier.save()"
echo ""
echo "See: docs/superpowers/plans/2026-03-28-paper-mode-v2-sub2.md"

echo "=== Done ==="
