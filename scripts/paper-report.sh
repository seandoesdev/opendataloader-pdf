#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"

INPUT_DIR="${1:?Usage: paper-report.sh <input-pdf-dir> [template-dir]}"
TEMPLATE_DIR="${2:-}"

if [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found. Run './scripts/build-java.sh' first."
    exit 1
fi

OUTDIR=$(mktemp -d)
EXTRA_ARGS="--paper-mode --paper-review-dir $OUTDIR -o $OUTDIR"
if [ -n "$TEMPLATE_DIR" ]; then
    EXTRA_ARGS="$EXTRA_ARGS --paper-template-dir $TEMPLATE_DIR"
fi

echo "=== Paper Mode Quality Report ==="
echo "Input: $INPUT_DIR"
echo "Templates: ${TEMPLATE_DIR:-built-in}"
echo ""

TOTAL=0
SUCCESS=0
FAIL=0
for pdf in "$INPUT_DIR"/*.pdf; do
    [ -f "$pdf" ] || continue
    TOTAL=$((TOTAL + 1))
    if java -jar "$JAR_PATH" $EXTRA_ARGS "$pdf" 2>/dev/null; then
        SUCCESS=$((SUCCESS + 1))
    else
        FAIL=$((FAIL + 1))
    fi
done

echo "Processed: $TOTAL PDFs ($SUCCESS success, $FAIL failed)"
echo ""

# Write Node.js report script to temp file to avoid path escaping issues
REPORT_SCRIPT=$(mktemp --suffix=.mjs)
cat > "$REPORT_SCRIPT" << 'NODESCRIPT'
import fs from 'fs';
import path from 'path';

const outDir = process.env.PAPER_REPORT_OUTDIR;
const files = fs.readdirSync(outDir).filter(f => f.endsWith('.paper.json'));

if (files.length === 0) { console.log('No paper.json files found.'); process.exit(0); }

// Collect stats
const stats = { total: files.length, withTitle: 0, withAuthors: 0, withAbstract: 0,
                withDoi: 0, withKeywords: 0, withSections: 0, withRefs: 0 };
const journalStats = {};
const missingFields = [];

files.forEach(file => {
    const d = JSON.parse(fs.readFileSync(path.join(outDir, file), 'utf8'));
    const mode = d.metadata?.extraction_mode || 'unknown';
    const journal = mode.includes('template:') ? mode.split('template:')[1].split('+')[0] : 'no-template';

    if (!journalStats[journal]) journalStats[journal] = { count: 0, titles: 0, authors: 0, abstracts: 0, dois: 0, keywords: 0, refs: 0, totalConf: 0 };
    const js = journalStats[journal];
    js.count++;

    if (d.title) { stats.withTitle++; js.titles++; }
    if (d.authors?.length > 0) { stats.withAuthors++; js.authors++; }
    if (d.abstract) { stats.withAbstract++; js.abstracts++; }
    if (d.doi) { stats.withDoi++; js.dois++; }
    if (d.keywords?.length > 0) { stats.withKeywords++; js.keywords++; }
    if (d.sections?.length > 0) { stats.withSections++; js.sections = (js.sections || 0) + 1; }
    if (d.references?.length > 0) { stats.withRefs++; js.refs++; }

    const conf = d.metadata?.confidence || {};
    const avgConf = Object.values(conf).length > 0
        ? Object.values(conf).reduce((a,b) => a+b, 0) / Object.values(conf).length : 0;
    js.totalConf += avgConf;

    // Track missing fields
    const missing = [];
    if (!d.title) missing.push('title');
    if (!d.authors?.length) missing.push('authors');
    if (!d.abstract) missing.push('abstract');
    if (!d.doi) missing.push('doi');
    if (missing.length > 0) missingFields.push({ file: file.replace('.paper.json',''), journal, missing });
});

console.log('=== Overall Statistics ===');
console.log('Title:      ' + stats.withTitle + '/' + stats.total + ' (' + Math.round(stats.withTitle/stats.total*100) + '%)');
console.log('Authors:    ' + stats.withAuthors + '/' + stats.total + ' (' + Math.round(stats.withAuthors/stats.total*100) + '%)');
console.log('Abstract:   ' + stats.withAbstract + '/' + stats.total + ' (' + Math.round(stats.withAbstract/stats.total*100) + '%)');
console.log('DOI:        ' + stats.withDoi + '/' + stats.total + ' (' + Math.round(stats.withDoi/stats.total*100) + '%)');
console.log('Keywords:   ' + stats.withKeywords + '/' + stats.total + ' (' + Math.round(stats.withKeywords/stats.total*100) + '%)');
console.log('Sections:   ' + stats.withSections + '/' + stats.total + ' (' + Math.round(stats.withSections/stats.total*100) + '%)');
console.log('References: ' + stats.withRefs + '/' + stats.total + ' (' + Math.round(stats.withRefs/stats.total*100) + '%)');
console.log('');

console.log('=== Per-Journal Statistics ===');
Object.entries(journalStats).sort((a,b) => b[1].count - a[1].count).forEach(([journal, s]) => {
    const avgConf = (s.totalConf / s.count * 100).toFixed(0);
    console.log(journal + ': ' + s.count + ' docs, confidence ' + avgConf + '%');
    console.log('  title=' + s.titles + '/' + s.count +
        ' authors=' + s.authors + '/' + s.count +
        ' abstract=' + s.abstracts + '/' + s.count +
        ' doi=' + s.dois + '/' + s.count +
        ' refs=' + s.refs + '/' + s.count);
});

if (missingFields.length > 0) {
    console.log('');
    console.log('=== Missing Fields (needs attention) ===');
    missingFields.forEach(m => {
        console.log(m.file + ' [' + m.journal + ']: missing ' + m.missing.join(', '));
    });
}

console.log('');
console.log('=== Recommendations ===');
const noTemplate = journalStats['no-template'];
if (noTemplate && noTemplate.count > 0) {
    console.log('- ' + noTemplate.count + ' PDFs have no journal template. Run --paper-analyze to generate templates.');
}
Object.entries(journalStats).forEach(([journal, s]) => {
    if (journal !== 'no-template' && s.totalConf / s.count < 0.8) {
        console.log('- Journal "' + journal + '": low avg confidence. Review and update template.');
    }
});
NODESCRIPT

# Convert OUTDIR to Windows path for Node.js on MSYS2/Git Bash
if command -v cygpath &>/dev/null; then
    NODE_OUTDIR=$(cygpath -w "$OUTDIR")
else
    NODE_OUTDIR="$OUTDIR"
fi

PAPER_REPORT_OUTDIR="$NODE_OUTDIR" node "$REPORT_SCRIPT"

rm -f "$REPORT_SCRIPT"
rm -rf "$OUTDIR"
echo "=== Done ==="
