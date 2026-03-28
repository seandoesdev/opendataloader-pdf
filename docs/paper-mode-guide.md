# Paper Mode 사용 가이드

학술 논문 PDF에서 제목, 저자, 초록, DOI, 키워드, 본문 섹션, 참고문헌 등 구조화된 메타데이터를 추출하는 기능입니다.

---

## 목차

1. [빠른 시작](#1-빠른-시작)
2. [출력 파일 설명](#2-출력-파일-설명)
3. [새 저널 템플릿 생성](#3-새-저널-템플릿-생성)
4. [대량 배치 처리](#4-대량-배치-처리)
5. [품질 리포트](#5-품질-리포트)
6. [200개 학회 커버리지 달성 로드맵](#6-200개-학회-커버리지-달성-로드맵)
7. [CRF 모델 학습 (고급)](#7-crf-모델-학습-고급)
8. [CLI 옵션 전체 목록](#8-cli-옵션-전체-목록)
9. [템플릿 파일 구조](#9-템플릿-파일-구조)
10. [Python / Node.js에서 사용](#10-python--nodejs에서-사용)
11. [트러블슈팅](#11-트러블슈팅)

---

## 1. 빠른 시작

### 단일 파일 처리

```bash
java -jar opendataloader-pdf-cli.jar --paper-mode -o ./output paper.pdf
```

이 명령은 3개의 파일을 생성합니다:

```
output/
  ├── paper.json              # 기존 일반 추출 결과
  ├── paper.paper.json        # 논문 전용 구조화 JSON
  └── paper.paper.md          # 논문 전용 Markdown (YAML 프론트매터)
```

### 여러 파일 한 번에 처리

```bash
java -jar opendataloader-pdf-cli.jar --paper-mode -o ./output paper1.pdf paper2.pdf paper3.pdf
```

---

## 2. 출력 파일 설명

### paper.paper.json

논문의 모든 메타데이터를 구조화된 JSON으로 제공합니다.

```json
{
  "title": "Stage-Specific Characteristics of Speech Processing in Children",
  "authors": [
    {
      "name": "Deok Gi Chae",
      "affiliation": "Seoul National University",
      "email": "chae@snu.ac.kr",
      "is_corresponding": false
    },
    {
      "name": "Eun Kyoung Lee",
      "is_corresponding": true
    }
  ],
  "abstract": "This study investigated stage-specific characteristics of...",
  "doi": "10.21849/cacd.2025.10.3.1",
  "keywords": ["Phonological disorder", "Speech processing"],
  "publication": {
    "venue": "Communication Sciences & Disorders",
    "volume": "10",
    "issue": "3",
    "date": "2025"
  },
  "sections": [
    {
      "type": "introduction",
      "title": "INTRODUCTION",
      "content": "In clinical settings, children who...",
      "page_start": 1,
      "page_end": 2
    }
  ],
  "references": [
    {
      "id": 1,
      "raw": "[1] Kim, S. \"A study of things.\" Journal, 2024.",
      "authors": ["Kim, S."],
      "title": "A study of things",
      "venue": "Journal",
      "year": 2024,
      "doi": "10.1234/example",
      "citations_in_text": [
        { "page": 2, "context": "...previous research [1] showed..." }
      ]
    }
  ],
  "metadata": {
    "source_file": "paper.pdf",
    "total_pages": 11,
    "language": "en",
    "extraction_mode": "template:cacd+rules",
    "confidence": {
      "title": 0.92,
      "authors": 0.90,
      "abstract": 0.92,
      "keywords": 0.90
    }
  }
}
```

### paper.paper.md

YAML 프론트매터 + Markdown 본문 형태입니다. 정적 사이트 생성기, 검색 엔진 색인 등에 바로 사용할 수 있습니다.

```markdown
---
title: "Stage-Specific Characteristics of Speech Processing in Children"
authors:
  - name: Deok Gi Chae
    affiliation: Seoul National University
  - name: Eun Kyoung Lee
    corresponding: true
doi: "10.21849/cacd.2025.10.3.1"
venue: "Communication Sciences & Disorders"
volume: "10"
issue: "3"
date: 2025
keywords: [Phonological disorder, Speech processing]
language: en
---

## Abstract

This study investigated stage-specific characteristics of...

## INTRODUCTION

In clinical settings, children who...

## References

[^1]: Kim, S. "A study of things." Journal, 2024. doi:10.1234/example
```

---

## 3. 새 저널 템플릿 생성

논문 추출 정확도를 높이려면 저널별 템플릿이 필요합니다. `--paper-analyze` 옵션으로 PDF를 분석하면 템플릿이 자동 생성됩니다.

### 3.1 자동 분석 실행

```bash
# 새 저널의 PDF 1개를 분석
java -jar opendataloader-pdf-cli.jar --paper-analyze \
  --paper-template-dir ./my-templates \
  new-journal-paper.pdf
```

출력 예시:

```
=== Paper Structure Analysis ===
File: new-journal-paper.pdf
Pages: 11
Body font size: 9.4

Journal ID: cacd
DOI: 10.21849/cacd.2025.10.3.1
DOI prefix: 10.21849/cacd
ISSN: 2508-5948

Title: "Stage-Specific Characteristics of Speech Processing..."
  page=1 fontSize=16.02 heading=true
Skip labels: [Original Article]
Exclude patterns: [Received:, Revised:, Accepted:]
Abstract label: "Purpose"
Keyword label: "Keywords"
Reference heading: "REFERENCES"
Reference entry pattern: bracket_number

Template saved: ./my-templates/cacd.json
Registry entry saved: ./my-templates/cacd.registry-entry.json
```

### 3.2 생성된 파일 확인

```bash
# 템플릿 내용 확인
cat ./my-templates/cacd.json
```

```json
{
  "journal_id": "cacd",
  "name": "Auto-generated from new-journal-paper.pdf",
  "title_rules": {
    "skip_labels": ["Original Article"],
    "strategy": "largest_font_excluding_skips",
    "page": 0,
    "position": "upper_half"
  },
  "author_rules": {
    "exclude_patterns": ["Received:", "Revised:", "Accepted:"],
    "separator": ",",
    "affiliation_marker": "superscript"
  },
  "abstract_rules": {
    "labels": ["Purpose", "Abstract", "초록"],
    "end_labels": ["Keywords", "Key words", "키워드"]
  },
  "keyword_rules": {
    "labels": ["Keywords", "Key words", "키워드"],
    "separators": [",", ";", "·"]
  },
  "reference_rules": {
    "heading_patterns": ["REFERENCES", "References", "참고문헌"],
    "entry_pattern": "bracket_number"
  }
}
```

### 3.3 템플릿 적용

```bash
# 생성된 템플릿으로 같은 저널의 다른 논문 처리
java -jar opendataloader-pdf-cli.jar --paper-mode \
  --paper-template-dir ./my-templates \
  -o ./output \
  same-journal-paper-2.pdf same-journal-paper-3.pdf
```

### 3.4 템플릿을 프로젝트에 영구 등록 (선택)

자주 사용하는 저널의 템플릿은 프로젝트의 기본 템플릿으로 등록할 수 있습니다.

1. 템플릿 파일을 복사합니다:
   ```bash
   cp ./my-templates/cacd.json \
      java/opendataloader-pdf-core/src/main/resources/paper-templates/
   ```

2. `_registry.json`에 fingerprint를 추가합니다:
   ```json
   {
     "journals": [
       {
         "journal_id": "cacd",
         "doi_prefixes": ["10.21849/cacd"],
         "issn": ["2508-5948"],
         "name_patterns": ["Communication Sciences & Disorders"]
       }
     ]
   }
   ```

3. 프로젝트를 다시 빌드합니다:
   ```bash
   cd java && mvn package -DskipTests
   ```

이후 해당 저널의 PDF는 `--paper-template-dir` 없이도 자동으로 템플릿이 적용됩니다.

---

## 4. 대량 배치 처리

### 4.1 기본 배치 처리

```bash
./scripts/batch-paper.sh <PDF-디렉토리> [출력-디렉토리] [검수큐-디렉토리] [CRF-모델]
```

예시:

```bash
# 기본 사용
./scripts/batch-paper.sh /path/to/pdfs /path/to/output /path/to/review

# CRF 모델과 함께 사용
./scripts/batch-paper.sh /path/to/pdfs /path/to/output /path/to/review /path/to/model.crf
```

출력:

```
=== Batch Paper Processing ===
Input: /path/to/pdfs
Output: /path/to/output
Review: /path/to/review

[1] Processing: paper001
[2] Processing: paper002
...
[100] Processing: paper100

=== Summary ===
Processed: 100 PDFs (98 success, 2 failed)
Output files: 98 paper.json
Review queue: 45 items
```

### 4.2 커스텀 템플릿과 함께 배치 처리

```bash
java -jar opendataloader-pdf-cli.jar --paper-mode \
  --paper-template-dir ./my-templates \
  --paper-review-dir ./review \
  -o ./output \
  /path/to/pdfs/*.pdf
```

### 4.3 검수 큐 활용

confidence가 0.9 미만인 결과는 자동으로 검수 큐에 저장됩니다.

```
review/
  ├── paper001.review.json
  ├── paper015.review.json
  └── paper042.review.json
```

검수 파일 구조:

```json
{
  "source": "paper001.pdf",
  "overall_confidence": 0.68,
  "fields": {
    "title": { "value": "Detected Title", "confidence": 0.92, "corrected": null },
    "abstract": { "value": null, "confidence": 0.0, "corrected": null },
    "doi": { "value": "10.1234/test", "confidence": 0.0, "corrected": null }
  },
  "zones": [
    {
      "index": 0,
      "page": 1,
      "text_preview": "Original Article",
      "classified_as": "TITLE",
      "corrected_as": null
    }
  ]
}
```

교정 방법: `corrected` 또는 `corrected_as` 필드를 채웁니다. 이 데이터는 CRF 모델 학습에 활용됩니다.

---

## 5. 품질 리포트

처리 결과의 전체 통계와 저널별 상세를 확인합니다.

```bash
./scripts/paper-report.sh <PDF-디렉토리> [템플릿-디렉토리]
```

출력 예시:

```
=== Paper Mode Quality Report ===
Input: /path/to/pdfs
Templates: built-in

Processed: 100 PDFs (100 success, 0 failed)

=== Overall Statistics ===
Title:      95/100 (95%)
Authors:    88/100 (88%)
Abstract:   90/100 (90%)
DOI:        72/100 (72%)
Keywords:   85/100 (85%)
Sections:   78/100 (78%)
References: 65/100 (65%)

=== Per-Journal Statistics ===
cacd: 25 docs, confidence 91%
  title=25/25 authors=24/25 abstract=25/25 doi=25/25 refs=20/25
ksepe: 15 docs, confidence 88%
  title=15/15 authors=14/15 abstract=15/15 doi=12/15 refs=10/15
no-template: 30 docs, confidence 62%
  title=25/30 authors=20/30 abstract=22/30 doi=15/30 refs=8/30

=== Missing Fields (needs attention) ===
paper042 [no-template]: missing title, authors
paper067 [no-template]: missing abstract, doi

=== Recommendations ===
- 30 PDFs have no journal template. Run --paper-analyze to generate templates.
- Journal "unknown-xyz": low avg confidence. Review and update template.
```

---

## 6. 200개 학회 커버리지 달성 로드맵

### 단계 1: 저널별 대표 PDF 수집 (1일)

각 학회/저널에서 최근 논문 PDF 1개씩 수집합니다.

```
journal-samples/
  ├── cacd-sample.pdf
  ├── ksepe-sample.pdf
  ├── sportslaw-sample.pdf
  ├── journal-004-sample.pdf
  ...
  └── journal-200-sample.pdf
```

### 단계 2: 템플릿 일괄 생성 (~5분)

```bash
./scripts/analyze-journals.sh ./journal-samples ./generated-templates
```

출력:

```
=== Journal Template Auto-Generation ===
[1] Analyzing: cacd-sample
[2] Analyzing: ksepe-sample
...
[200] Analyzing: journal-200-sample

=== Summary ===
Analyzed: 200 PDFs
Templates generated: 200

Next steps:
1. Review generated templates in ./generated-templates/
2. Copy approved templates to resources/paper-templates/
3. Add registry entries from *.registry-entry.json to _registry.json
```

### 단계 3: 템플릿 검토/수정 (~3시간)

각 템플릿을 열어 자동 감지된 라벨이 맞는지 확인합니다.

주로 확인할 항목:
- `skip_labels`: 논문 카테고리 라벨 (Original Article 등)이 올바르게 감지되었는지
- `abstract_rules.labels`: 초록 시작 라벨이 맞는지
- `reference_rules.heading_patterns`: 참고문헌 제목이 맞는지
- `reference_rules.entry_pattern`: `bracket_number` vs `dot_number`

### 단계 4: 프로젝트에 등록

```bash
# 검토 완료된 템플릿을 프로젝트에 복사
cp ./generated-templates/*.json \
   java/opendataloader-pdf-core/src/main/resources/paper-templates/

# registry-entry.json 파일들을 _registry.json에 통합
# (수동으로 각 엔트리를 _registry.json의 journals 배열에 추가)
```

### 단계 5: 전체 논문 배치 처리

```bash
# 빌드
cd java && mvn package -DskipTests

# 전체 배치 처리
./scripts/batch-paper.sh /path/to/all-papers /path/to/output /path/to/review

# 품질 확인
./scripts/paper-report.sh /path/to/all-papers
```

---

## 7. CRF 모델 학습 (고급)

CRF(Conditional Random Field) 모델은 템플릿이 없는 미등록 저널의 정확도를 높입니다.

### 7.1 학습 데이터 준비

배치 처리에서 생성된 검수 큐(`.review.json`)를 교정합니다.

```json
{
  "zones": [
    {
      "index": 0,
      "text_preview": "Original Article",
      "classified_as": "TITLE",
      "corrected_as": "PAGE_METADATA"
    },
    {
      "index": 1,
      "text_preview": "Real Paper Title Here",
      "classified_as": "BODY_TEXT",
      "corrected_as": "TITLE"
    }
  ]
}
```

사용 가능한 Zone 라벨:

| 라벨 | 설명 |
|------|------|
| `TITLE` | 논문 제목 |
| `AUTHOR_BLOCK` | 저자 이름/소속 |
| `ABSTRACT` | 초록 |
| `KEYWORDS` | 키워드 |
| `BODY_HEADING` | 본문 섹션 제목 |
| `BODY_TEXT` | 본문 텍스트 |
| `REFERENCE_HEADING` | 참고문헌 제목 |
| `REFERENCE_BODY` | 참고문헌 본문 |
| `PAGE_METADATA` | 저널명, DOI, 페이지 번호 등 |
| `HEADER_FOOTER` | 머리글/바닥글 |
| `TABLE` | 표 |
| `FIGURE` | 그림 |
| `CAPTION` | 캡션 |
| `ACKNOWLEDGMENT` | 감사의 글 |
| `UNKNOWN` | 분류 불가 |

### 7.2 CRF 모델 학습 (Java API)

```java
import org.opendataloader.pdf.processors.paper.crf.*;

// 1. 교정된 review JSON을 학습 데이터로 변환
List<TrainingDataConverter.LabeledZoneData> labeled =
    TrainingDataConverter.fromReviewJson(Path.of("review/paper001.review.json"));

// 2. 여러 문서의 학습 데이터 수집
List<List<CRFClassifier.LabeledZone>> trainingData = new ArrayList<>();
// ... 각 문서의 labeled zone 데이터를 LabeledZone으로 변환

// 3. CRF 모델 학습
CRFClassifier classifier = new CRFClassifier();
classifier.train(trainingData);

// 4. 모델 저장
classifier.save(Path.of("model.crf"));
```

### 7.3 CRF 모델 사용

```bash
# CRF 모델을 지정하여 논문 처리
java -jar opendataloader-pdf-cli.jar --paper-mode \
  --paper-crf-model /path/to/model.crf \
  -o ./output paper.pdf
```

### 7.4 반복 학습 워크플로우

```
1단계: 배치 처리
   ./scripts/batch-paper.sh /pdfs /output /review

2단계: 검수 큐 교정 (review/*.review.json의 corrected_as 수정)

3단계: CRF 모델 학습 (Java API)

4단계: 학습된 모델로 재처리
   ./scripts/batch-paper.sh /pdfs /output /review /path/to/model.crf

5단계: 반복 → 교정 50건 → 학습 → 정확도 점진적 향상
```

---

## 8. CLI 옵션 전체 목록

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `--paper-mode` | boolean | false | 논문 모드 활성화 |
| `--paper-analyze` | boolean | false | PDF 분석 후 저널 템플릿 자동 생성 |
| `--paper-template-dir` | string | (내장) | 커스텀 템플릿 디렉토리 경로 |
| `--paper-weights` | string | (내장) | Zone 분류 가중치 JSON 파일 경로 |
| `--paper-crf-model` | string | null | 학습된 CRF 모델 파일 경로 |
| `--paper-review-dir` | string | null | 검수 큐 출력 디렉토리 (confidence < 0.9) |
| `-o, --output-dir` | string | 입력 위치 | 출력 디렉토리 |

### 조합 예시

```bash
# 기본 사용 (내장 템플릿만)
java -jar cli.jar --paper-mode -o ./output paper.pdf

# 커스텀 템플릿 + 검수 큐
java -jar cli.jar --paper-mode \
  --paper-template-dir ./templates \
  --paper-review-dir ./review \
  -o ./output paper.pdf

# 전체 옵션 (템플릿 + CRF + 검수큐)
java -jar cli.jar --paper-mode \
  --paper-template-dir ./templates \
  --paper-crf-model ./model.crf \
  --paper-review-dir ./review \
  -o ./output paper.pdf

# 분석 모드 (처리 대신 템플릿 생성)
java -jar cli.jar --paper-analyze \
  --paper-template-dir ./new-templates \
  paper.pdf
```

---

## 9. 템플릿 파일 구조

### _registry.json — 저널 식별 데이터베이스

```json
{
  "journals": [
    {
      "journal_id": "cacd",
      "doi_prefixes": ["10.21849/cacd"],
      "issn": ["2508-5948", "2586-7792"],
      "name_patterns": ["Communication Sciences & Disorders", "Commun Sci & Disord"]
    },
    {
      "journal_id": "ksepe",
      "doi_prefixes": ["10.26844/ksepe"],
      "issn": [],
      "name_patterns": ["한국초등체육학회지"]
    }
  ]
}
```

저널 식별 우선순위:
1. **DOI prefix** — 가장 정확. 논문 첫 페이지에서 DOI를 찾아 prefix 매칭
2. **ISSN** — DOI가 없으면 ISSN으로 매칭
3. **저널명 패턴** — ISSN도 없으면 텍스트에서 저널명 검색

### 저널 템플릿 JSON — 추출 규칙

```json
{
  "journal_id": "cacd",
  "name": "Communication Sciences & Disorders",

  "title_rules": {
    "skip_labels": ["Original Article", "Review Article"],
    "strategy": "largest_font_excluding_skips",
    "page": 0,
    "position": "upper_half"
  },

  "author_rules": {
    "exclude_patterns": ["Received:", "Revised:", "Accepted:", "Published:"],
    "stop_before_patterns": ["^Department", "^School"],
    "separator": ",",
    "affiliation_marker": "superscript"
  },

  "abstract_rules": {
    "labels": ["Purpose", "Abstract", "초록", "요약"],
    "end_labels": ["Keywords", "Key words", "키워드"]
  },

  "keyword_rules": {
    "labels": ["Keywords", "Key words", "키워드", "핵심어"],
    "separators": [",", ";", "·"]
  },

  "reference_rules": {
    "heading_patterns": ["References", "REFERENCES", "참고문헌"],
    "entry_pattern": "bracket_number"
  }
}
```

### 각 필드 설명

| 섹션 | 필드 | 설명 |
|------|------|------|
| **title_rules** | `skip_labels` | 제목으로 오인하기 쉬운 카테고리 라벨. 이 텍스트는 건너뜀 |
| | `strategy` | 제목 감지 방식. `largest_font_excluding_skips` = 가장 큰 폰트 |
| **author_rules** | `exclude_patterns` | 저자 영역에서 제거할 텍스트 패턴 (날짜 등) |
| | `separator` | 저자 이름 구분자 (보통 `,`) |
| | `affiliation_marker` | 소속 표시 방식: `superscript` (¹²³) 또는 `parentheses` |
| **abstract_rules** | `labels` | 초록 시작을 나타내는 라벨 목록 |
| | `end_labels` | 초록 끝을 나타내는 라벨 (보통 키워드 시작) |
| **keyword_rules** | `labels` | 키워드 시작 라벨 |
| | `separators` | 키워드 구분자 |
| **reference_rules** | `heading_patterns` | 참고문헌 섹션 제목 |
| | `entry_pattern` | `bracket_number` = `[1]`, `dot_number` = `1.` |

---

## 10. Python / Node.js에서 사용

### Python

```bash
pip install opendataloader-pdf
```

```python
import opendataloader_pdf

# 기본 사용
opendataloader_pdf.convert(
    input_path="paper.pdf",
    output_dir="./output",
    paper_mode=True
)

# 커스텀 템플릿 + CRF 모델
opendataloader_pdf.convert(
    input_path=["paper1.pdf", "paper2.pdf", "paper3.pdf"],
    output_dir="./output",
    paper_mode=True,
    paper_template_dir="/path/to/templates",
    paper_crf_model="/path/to/model.crf",
    paper_review_dir="/path/to/review"
)

# 결과 읽기
import json
with open("./output/paper.paper.json") as f:
    data = json.load(f)
    print(f"Title: {data['title']}")
    print(f"Authors: {[a['name'] for a in data['authors']]}")
    print(f"DOI: {data['doi']}")
```

### Node.js

```bash
npm install @opendataloader/pdf
```

```javascript
const { convert } = require('@opendataloader/pdf');

// 기본 사용
await convert(['paper.pdf'], {
    outputDir: './output',
    paperMode: true
});

// 커스텀 템플릿
await convert(['paper1.pdf', 'paper2.pdf'], {
    outputDir: './output',
    paperMode: true,
    paperTemplateDir: '/path/to/templates',
    paperCrfModel: '/path/to/model.crf'
});

// 결과 읽기
const fs = require('fs');
const data = JSON.parse(fs.readFileSync('./output/paper.paper.json', 'utf8'));
console.log(`Title: ${data.title}`);
console.log(`Authors: ${data.authors.map(a => a.name).join(', ')}`);
```

---

## 11. 트러블슈팅

### 제목이 "Original Article"로 잘못 추출됨

원인: 해당 저널의 템플릿에 `skip_labels`가 설정되지 않았습니다.

해결:
```bash
# 1. 해당 논문을 분석하여 템플릿 자동 생성
java -jar cli.jar --paper-analyze --paper-template-dir ./fix paper.pdf

# 2. 생성된 템플릿에 skip_labels가 자동으로 추가됨
# 3. 해당 템플릿으로 재처리
java -jar cli.jar --paper-mode --paper-template-dir ./fix -o ./output paper.pdf
```

### 저자에 날짜 텍스트가 포함됨

원인: 템플릿의 `exclude_patterns`에 해당 패턴이 없습니다.

해결: 템플릿의 `author_rules.exclude_patterns`에 패턴을 추가합니다.
```json
"exclude_patterns": ["Received:", "Revised:", "Accepted:", "Published:", "투고일", "심사일"]
```

### 초록이 감지되지 않음

원인: 해당 저널의 초록 라벨이 템플릿에 등록되지 않았습니다.

해결: 논문을 열어 초록 앞의 라벨을 확인하고 템플릿에 추가합니다.
```json
"abstract_rules": {
  "labels": ["Purpose", "Abstract", "초록", "요약", "국문초록", "BACKGROUND"]
}
```

### 참고문헌이 감지되지 않음

원인 1: 참고문헌 제목이 템플릿과 다릅니다.

해결:
```json
"reference_rules": {
  "heading_patterns": ["References", "REFERENCES", "참고문헌", "참고 문헌", "Bibliography", "BIBLIOGRAPHY"]
}
```

원인 2: 참고문헌 번호 형식이 다릅니다.

해결: `entry_pattern`을 확인합니다.
- `[1] Author...` → `"entry_pattern": "bracket_number"`
- `1. Author...` → `"entry_pattern": "dot_number"`

### 새 저널이 "unknown" 템플릿으로 처리됨

원인: 해당 저널의 fingerprint가 `_registry.json`에 등록되지 않았습니다.

해결:
```bash
# 1. 분석하여 registry entry 생성
java -jar cli.jar --paper-analyze --paper-template-dir ./tmp paper.pdf

# 2. 생성된 .registry-entry.json을 _registry.json에 추가
cat ./tmp/*.registry-entry.json
```

### confidence가 낮음 (< 0.8)

가능한 원인:
- 저널 템플릿이 없거나 부정확
- PDF에서 텍스트 추출이 불완전 (스캔 PDF 등)
- 논문 형식이 특수함 (포스터, 단보 등)

해결:
1. `--paper-analyze`로 해당 저널의 템플릿을 생성/수정
2. 검수 큐의 review.json을 교정하여 CRF 학습 데이터로 활용
3. 스캔 PDF의 경우 `--hybrid docling-fast` 옵션으로 OCR 병행
