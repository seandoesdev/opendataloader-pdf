#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"

INPUT_DIR="${1:?Usage: analyze-journals.sh <input-pdf-dir> [template-output-dir]}"
TEMPLATE_DIR="${2:-$INPUT_DIR/templates}"

if [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found. Run './scripts/build-java.sh' first."
    exit 1
fi

mkdir -p "$TEMPLATE_DIR"

echo "=== Journal Template Auto-Generation ==="
echo "Input: $INPUT_DIR"
echo "Templates: $TEMPLATE_DIR"
echo ""

COUNT=0
for pdf in "$INPUT_DIR"/*.pdf; do
    [ -f "$pdf" ] || continue
    COUNT=$((COUNT + 1))
    name=$(basename "$pdf" .pdf)
    echo "[$COUNT] Analyzing: $name"
    java -jar "$JAR_PATH" --paper-analyze --paper-template-dir "$TEMPLATE_DIR" "$pdf" 2>/dev/null || echo "  ERROR"
    echo ""
done

echo "=== Summary ==="
echo "Analyzed: $COUNT PDFs"
echo "Templates generated: $(ls "$TEMPLATE_DIR"/*.json 2>/dev/null | grep -v registry | wc -l)"
echo ""
echo "Next steps:"
echo "1. Review generated templates in $TEMPLATE_DIR/"
echo "2. Copy approved templates to resources/paper-templates/"
echo "3. Add registry entries from *.registry-entry.json to _registry.json"
