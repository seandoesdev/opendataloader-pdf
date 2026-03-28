# Paper Mode v2: 3계층 하이브리드 메타데이터 추출 설계

**Date**: 2026-03-28
**Status**: Approved
**Approach**: Template DB + CRF + 규칙 엔진 3계층 하이브리드
**구현 단위**: Sub-project 1 (Template + Validator) → Sub-project 2 (CRF 통합)

---

## 1. 목표

200개+ 학회, 수만 건 논문에서 구조화된 메타데이터를 정확하게 추출하는 자동 개선 시스템을 구축한다.

### 환경 제약
- CPU 전용 (GPU 없음)
- 외부 API 불가 (로컬/온프레미스만)
- 로컬 LLM 불가
- 배치 + 실시간 모두 지원

### 목표 정확도

| 단계 | 등록 저널 | 미등록 저널 |
|------|-----------|-------------|
| Sub-project 1 완료 | 90-95% | 50-65% |
| Sub-project 2 완료 | 95-97% | 88-95% |

---

## 2. 전체 아키텍처

```
PDF 입력
  │
  ▼
[PaperProcessor v2 — 3계층 라우터]
  │
  ├─ 1계층: Template DB
  │   ├─ JournalFingerprinter: 저널 식별 (DOI prefix/ISSN/저널명)
  │   ├─ TemplateRegistry: 템플릿 조회
  │   ├─ 매칭됨 → TemplateBasedExtractor (confidence 0.90+)
  │   └─ 미매칭 ↓
  │
  ├─ 2계층: CRF 모델 (Sub-project 2)
  │   ├─ CRFFeatureExtractor: Zone별 40+ 특징 벡터
  │   ├─ CRFClassifier: 순차 라벨링 (MALLET CRF)
  │   ├─ confidence >= 0.8 → 필드 추출 → 결과
  │   └─ confidence < 0.8 ↓
  │
  ├─ 3계층: 규칙 엔진 (기존 ZoneClassifier)
  │   └─ 가중 점수 기반 분류 → 결과 (low confidence)
  │
  ▼
[ResultMerger + PaperValidator]
  ├─ 필드별 최고 confidence 결과 채택
  ├─ Validator: 형식/범위/패턴 검증
  ├─ confidence >= 0.9 → 최종 출력
  └─ confidence < 0.9 → 검수 큐

  ▼
[자동 학습 루프]
  ├─ 교정된 결과 → CRF 재학습 데이터
  └─ 동일 저널 고신뢰 10건+ → 템플릿 자동 생성
```

### 설계 원칙

1. **계층 독립성**: 각 계층이 독립적으로 동작. CRF 없어도 1+3계층 동작
2. **Confidence 기반 라우팅**: 상위 계층의 high confidence → 하위 계층 스킵
3. **점진적 개선**: 사용할수록 템플릿 DB 성장 + CRF 정확도 향상
4. **CPU 전용**: 모든 계층이 CPU에서 동작

---

## 3. 1계층: Template DB

### 3.1 JournalFingerprinter

첫 페이지 텍스트에서 저널을 3단계로 식별한다:

1. **DOI prefix 매칭** (가장 정확): `10.21849/cacd` → `cacd`
2. **ISSN 매칭**: `ISSN 2586-7792` → `cacd`
3. **저널명 키워드 매칭** (폴백): `"Commun Sci & Disord"` → `cacd`

매칭 실패 시 `journal_id = "unknown"` 반환.

### 3.2 Template Registry

`resources/paper-templates/` 디렉토리에서 JSON 템플릿을 로드/캐싱한다.

**파일 구조:**
```
resources/paper-templates/
  ├── _registry.json           ← 전체 저널 fingerprint 목록
  ├── cacd.json                ← Communication Sciences & Disorders
  ├── ksepe.json               ← 한국초등체육학회지
  └── default.json             ← 범용 폴백 규칙
```

**_registry.json 형식:**
```json
{
  "journals": [
    {
      "journal_id": "cacd",
      "doi_prefixes": ["10.21849/cacd"],
      "issn": ["2586-7792"],
      "name_patterns": ["Commun Sci & Disord", "Communication Sciences"]
    },
    {
      "journal_id": "ksepe",
      "doi_prefixes": ["10.26844/ksepe"],
      "issn": [],
      "name_patterns": ["한국초등체육학회지", "Korean Journal of Elementary Physical Education"]
    }
  ]
}
```

### 3.3 Template 구조

```json
{
  "journal_id": "cacd",
  "name": "Communication Sciences & Disorders",
  "title_rules": {
    "skip_labels": ["Original Article", "Review Article", "Brief Report", "Case Report"],
    "strategy": "largest_font_excluding_skips",
    "page": 0,
    "position": "upper_half"
  },
  "author_rules": {
    "exclude_patterns": ["Received:", "Revised:", "Accepted:", "Published:"],
    "stop_before_patterns": ["^Department", "^School", "^\\d+[A-Z]"],
    "separator": ",",
    "affiliation_marker": "superscript"
  },
  "abstract_rules": {
    "labels": ["Purpose", "Abstract", "Background", "Objectives"],
    "end_labels": ["Keywords", "Key words", "KEYWORDS"]
  },
  "keyword_rules": {
    "labels": ["Keywords", "Key words", "키워드", "핵심어", "주제어"],
    "separators": [",", ";", "·"]
  },
  "reference_rules": {
    "heading_patterns": ["References", "REFERENCES", "참고문헌"],
    "entry_pattern": "bracket_number"
  }
}
```

### 3.4 TemplateBasedExtractor

템플릿 매칭 시 범용 ZoneClassifier 대신 템플릿 규칙으로 직접 추출한다.

**처리 흐름:**
1. `skip_labels`에 해당하는 텍스트 제외 후 제목 감지
2. `exclude_patterns`으로 저자 영역 필터링
3. `labels`/`end_labels`로 초록 범위 지정
4. `entry_pattern`으로 참고문헌 분리
5. 각 필드에 high confidence (0.90-0.95) 부여

---

## 4. 2계층: CRF 모델 (Sub-project 2)

### 4.1 Feature Engineering

Zone당 40+ 특징을 추출한다.

**위치 특징 (8개):**
- relativeY, relativeX, widthRatio, heightRatio
- pageIndex, pageRatio, isFirstPage, isLastQuarter

**폰트 특징 (6개):**
- fontSizeRatio, maxFontSize, isBold, isCentered, isItalic, distinctFontCount

**텍스트 통계 (8개):**
- lineCount, avgLineLength, textDensity, charCount
- koreanRatio, digitRatio, punctuationRatio, wordCount

**패턴 특징 (10개):**
- hasEmail, hasDoi, hasUrl, hasNumberPrefix, hasSuperscript
- hasQuotedText, startsWithCapital, containsYear, hasCommaList, hasParentheses

**키워드 특징 (5개):**
- containsAbstractLabel, containsKeywordLabel, containsRefLabel
- containsSectionNumber, containsAckLabel

**문맥 특징 (5개):**
- gapToPrev, gapToNext, fontSizeDiffFromPrev, fontSizeDiffFromNext, zoneIndexInPage

### 4.2 CRF 모델

**라이브러리**: MALLET (Java 네이티브 CRF)

```java
public class CRFClassifier {
    // 학습: List<LabeledDocument> → CRF 모델
    public void train(List<LabeledPaperData> trainingData);

    // 추론: Zone 특징 시퀀스 → (ZoneType, confidence) 시퀀스
    public List<ZoneClassification> classify(List<CRFFeatures> zones);

    // 모델 저장/로드
    public void save(Path modelPath);
    public static CRFClassifier load(Path modelPath);
}
```

**CRF vs 현재 ZoneClassifier:**

| 현재 (가중 점수) | CRF |
|---|---|
| 수동 가중치 튜닝 | 데이터에서 자동 학습 |
| 2-pass 문맥 보정 (수동) | 전이 확률 자동 학습 |
| 새 저널마다 조정 필요 | 학습 데이터 추가만으로 개선 |
| 특징 조합 불가 | 특징 간 상호작용 학습 |

**성능:** CPU에서 ~1ms/문서 추론, 모델 크기 ~1-5MB

### 4.3 학습 데이터 부트스트래핑

```
Phase 1: 수동 라벨링 50-100건 (3-5일)
  → 현재 paper mode 결과를 JSON으로 출력
  → 사람이 zone labels만 교정

Phase 2: CRF 학습 → 500건 추가 처리 (자동)
  → confidence >= 0.9 자동 수락
  → < 0.9만 수동 교정 → 재학습

Phase 3: 1000건+ → 대부분 저널 커버
```

### 4.4 학습 데이터 형식

```json
{
  "document": "paper_test1.pdf",
  "zones": [
    {
      "zone_index": 0,
      "page": 1,
      "text_preview": "Original Article",
      "features": { "relativeY": 0.05, "fontSizeRatio": 0.8, ... },
      "label": "PAGE_METADATA"
    },
    {
      "zone_index": 1,
      "page": 1,
      "text_preview": "Speech Processing Abilities in Children...",
      "features": { "relativeY": 0.12, "fontSizeRatio": 2.1, ... },
      "label": "TITLE"
    }
  ]
}
```

---

## 5. 결과 병합 + PaperValidator

### 5.1 ResultMerger

3계층에서 같은 필드를 추출한 경우 필드별로 가장 높은 confidence 결과를 채택한다.

```java
public class ResultMerger {
    public static PaperDocument merge(
        PaperDocument templateResult,    // 1계층 (or null)
        PaperDocument crfResult,         // 2계층 (or null)
        PaperDocument ruleResult         // 3계층
    ) {
        // 각 필드별 highest confidence 선택
        // 필드: title, authors, abstract, doi, keywords,
        //       publication, sections, references
    }
}
```

### 5.2 PaperValidator

추출 결과의 형식/범위/패턴을 검증하고 명백한 오류를 걸러낸다.

**검증 규칙:**

| 필드 | 검증 |
|------|------|
| title | 5자 이상 500자 이하. 카테고리 라벨("Original Article" 등) 아님 |
| author | 2자 이상 100자 이하. 날짜/월 이름 포함 안 함. 숫자만으로 구성 안 함 |
| doi | `10.\d{4,9}/\S+` 패턴 매칭 |
| abstract | 50자 이상 |
| year | 1900~현재 범위 |
| keywords | 각 키워드 1자 이상 50자 이하 |

**검증 실패 시:**
- 해당 필드를 null로 설정하고 confidence를 0으로 낮춤
- 하위 계층 결과로 대체 시도

---

## 6. 자동 학습 루프

### 6.1 배치 처리 파이프라인

```
batch-process/
  ├─ input/                  ← PDF 파일들
  ├─ output/                 ← paper.json + paper.md
  ├─ review-queue/           ← confidence < 0.9인 결과 (교정 대상)
  ├─ approved/               ← 검수 완료 결과 (CRF 학습 데이터)
  └─ training-data/          ← CRF 학습용 누적 데이터
```

### 6.2 검수 큐 형식 (review.json)

```json
{
  "source": "paper_test1.pdf",
  "extraction_layer": "rule_engine",
  "overall_confidence": 0.65,
  "fields": {
    "title": {
      "value": "Original Article",
      "confidence": 0.65,
      "valid": false,
      "corrected": null
    },
    "authors": {
      "value": [{"name": "Received: 1 August"}],
      "confidence": 0.60,
      "valid": false,
      "corrected": null
    }
  },
  "zones": [
    {
      "index": 0, "page": 1,
      "text_preview": "Original Article",
      "classified_as": "TITLE",
      "corrected_as": null
    }
  ]
}
```

사람이 `corrected` 필드만 채우면 학습 데이터가 된다.

### 6.3 템플릿 자동 생성

동일 저널(DOI prefix 기준)에서 고신뢰 결과가 10건 이상 일관되면 자동으로 템플릿을 생성한다.

**로직:**
1. 같은 DOI prefix의 결과 10건+ 수집
2. 제목/저자/초록 위치의 평균/분산 계산
3. 분산이 임계값 이내면 → 위치/패턴 기반 템플릿 자동 생성
4. `_registry.json`에 fingerprint 추가

---

## 7. CLI 옵션 확장

기존 `--paper-mode`, `--paper-weights` 외에 추가:

```
--paper-template-dir <path>    커스텀 템플릿 디렉토리 경로 (선택)
--paper-review-dir <path>      검수 큐 출력 디렉토리 (배치 모드용, 선택)
--paper-crf-model <path>       커스텀 CRF 모델 경로 (선택, Sub-project 2)
```

---

## 8. 파일 구조

### Sub-project 1 — 신규 파일

| 파일 | 역할 |
|------|------|
| `processors/paper/JournalFingerprinter.java` | DOI/ISSN/이름으로 저널 식별 |
| `processors/paper/TemplateRegistry.java` | 템플릿 로드/조회/캐싱 |
| `processors/paper/TemplateBasedExtractor.java` | 템플릿 기반 필드 추출 |
| `processors/paper/PaperValidator.java` | 결과 교차 검증 |
| `processors/paper/ResultMerger.java` | 다계층 결과 병합 |
| `processors/paper/ReviewQueueWriter.java` | 검수 큐 JSON 출력 |
| `resources/paper-templates/_registry.json` | 저널 fingerprint DB |
| `resources/paper-templates/cacd.json` | paper_test1 저널 템플릿 |
| `resources/paper-templates/ksepe.json` | paper_test3 저널 템플릿 |
| `resources/paper-templates/default.json` | 범용 폴백 규칙 |

### Sub-project 1 — 수정 파일

| 파일 | 변경 |
|------|------|
| `PaperProcessor.java` | 3계층 라우팅 (1계층 시도 → 3계층 폴백) |
| `TitleExtractor.java` | Validator 통합 |
| `AuthorExtractor.java` | exclude 패턴 + Validator |
| `Config.java` | `paperTemplateDir`, `paperReviewDir` 필드 추가 |
| `CLIOptions.java` | `--paper-template-dir`, `--paper-review-dir` 옵션 |

### Sub-project 2 — 신규 파일

| 파일 | 역할 |
|------|------|
| `processors/paper/crf/CRFFeatureExtractor.java` | 40+ 특징 벡터 생성 |
| `processors/paper/crf/CRFClassifier.java` | MALLET CRF 래퍼 |
| `processors/paper/crf/CRFTrainer.java` | 학습 데이터 → 모델 학습 |
| `processors/paper/crf/TrainingDataConverter.java` | review JSON → MALLET 형식 |
| `processors/paper/TemplateAutoGenerator.java` | 고신뢰 결과 → 템플릿 자동 생성 |
| `scripts/train-crf.sh` | CRF 학습 스크립트 |
| `scripts/batch-paper.sh` | 배치 처리 스크립트 |

### Sub-project 2 — 수정 파일

| 파일 | 변경 |
|------|------|
| `PaperProcessor.java` | 2계층 CRF 분기 추가 |
| `Config.java` | `paperCrfModel` 필드 추가 |
| `CLIOptions.java` | `--paper-crf-model` 옵션 |
| `pom.xml` | MALLET 의존성 추가 |

---

## 9. 테스트 전략

### Sub-project 1

| 테스트 | 내용 |
|--------|------|
| `JournalFingerprinterTest` | DOI/ISSN/이름 매칭, 미매칭 시 unknown |
| `TemplateRegistryTest` | 템플릿 로드, 캐싱, 미등록 저널 처리 |
| `TemplateBasedExtractorTest` | 템플릿 기반 추출 (skip_labels, exclude_patterns) |
| `PaperValidatorTest` | 각 필드별 검증 규칙 |
| `ResultMergerTest` | confidence 기반 병합 |
| `PaperProcessorV2IntegrationTest` | paper_test1/2/3 전체 파이프라인 + 기존 대비 정확도 향상 검증 |

### Sub-project 2

| 테스트 | 내용 |
|--------|------|
| `CRFFeatureExtractorTest` | 특징 벡터 생성 정확성 |
| `CRFClassifierTest` | 소규모 학습 데이터로 학습/추론 |
| `TrainingDataConverterTest` | JSON → MALLET 형식 변환 |
| `TemplateAutoGeneratorTest` | 일관된 결과 → 템플릿 자동 생성 |

---

## 10. 예상 성능

| 항목 | Sub-project 1 | Sub-project 2 |
|------|---------------|---------------|
| 처리 속도 (템플릿 히트) | 0.1초/논문 | 0.1초 |
| 처리 속도 (CRF) | N/A | 0.2초 |
| 처리 속도 (규칙 폴백) | 0.1초 | 0.1초 |
| 정확도 (등록 저널) | 90-95% | 95-97% |
| 정확도 (미등록 저널) | 50-65% | 88-95% |
| 추가 비용 | $0 | $0 |
| GPU 필요 | 아니오 | 아니오 |
