# 사용자 제공 행정표준 코드북 정리

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

## 목적

사용자가 제공한 `C:\Users\82103\Downloads\코드들` 묶음을
프로젝트 안에서 바로 재사용 가능한 reference asset으로 고정합니다.

이번 정리의 목표는 두 가지입니다.

1. 작은 `xlsx` 코드표를 기계적으로 읽을 수 있는 JSON 자산으로 남긴다.
2. 큰 `txt` 코드표는 row count, 핵심 컬럼, 샘플과 intended use 를 같이 남긴다.

## 생성 자산

- 앱 리소스: [local-official-codebooks.json](../../backend/src/main/resources/reference/official-codes/local-official-codebooks.json)
- 작업 산출물: `tmp/local-official-codebooks/latest-local-official-codebooks.json`
- 재생성 스크립트: [extract_user_provided_official_codes.py](../../scripts/extract_user_provided_official_codes.py)
- draft seed 생성기: [generate_local_official_codebooks_seed_sql.py](../../scripts/generate_local_official_codebooks_seed_sql.py)
- 실제 migration: [V2026_06_02_02__seed_local_official_codebooks.sql](../../backend/src/main/resources/db/migration/V2026_06_02_02__seed_local_official_codebooks.sql)
- draft seed SQL: [V2026_06_02_02__seed_local_official_codebooks.sql](../../backend/src/main/resources/db/migration-draft/V2026_06_02_02__seed_local_official_codebooks.sql)

## 포함한 코드북

작은 코드표 `12개`:

- `LOCAL_HOUSE_TENURE_TYPE`
- `LOCAL_FAMILY_RELATIONSHIP`
- `LOCAL_BUILDING_USAGE`
- `LOCAL_LEGAL_BASIS_TYPE`
- `LOCAL_BASIC_LIVING_RECIPIENT_TYPE`
- `LOCAL_VETERAN_TARGET_TYPE`
- `LOCAL_DISABILITY_GRADE`
- `LOCAL_HOUSING_TYPE`
- `LOCAL_JOB_GROUP`
- `LOCAL_JOB_SERIES`
- `LOCAL_JOB_TYPE`
- `LOCAL_JOB_SUBTYPE`

큰 코드표 `3개`:

- `LOCAL_LEGAL_DISTRICT`
- `LOCAL_ADMIN_INSTITUTION`
- `LOCAL_ADMIN_INSTITUTION_WITH_TYPE_MEANING`

draft SQL 승격 범위:

- 작은 코드표 `12개`는 `normalization_codes` row까지 seed
- 큰 코드표 `3개`는 `normalization_code_sets` metadata만 등록

actual migration 반영 범위도 동일하다.
즉 현재는 reference asset -> draft seed -> actual migration 까지 연결된 상태다.

## practical 판단

이 코드북들은 `Gov24` 의 `서비스분야 / 사용자구분 / 지원유형` 공식 codebook 대체재는 아니다.
그 세 축은 계속 `Gov24` raw/live inventory를 source-of-truth로 읽는다.

반면 아래 용도에는 바로 쓸 수 있다.

- 사용자 프로필 정규화
  - `주거형태`, `주택유형`, `가족관계`
- 정책 자격조건 정규화
  - `기초생활수급권자`, `보훈대상자`, `장애등급`
- 직업/직군 세분화
  - `직군`, `직렬`, `직종`, `직종세분류`
- 메타데이터 정규화
  - `근거법령`, `건물용도`
- 기관/지역 crosswalk
  - `기관코드 전체자료`, `법정동코드 전체자료`

## follow-up 후보

1. `normalization_code_sets / normalization_codes` seed 초안으로 일부 코드북을 승격
2. `user_profiles.householdType`, `employmentStatus` 와 연결할 internal enum 정합성 점검
3. `Gov24 supportConditions` 의 `장애인 / 보훈 / 무주택 / 수급자` 해석과 이 코드북 연결
