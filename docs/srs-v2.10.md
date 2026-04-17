# 소프트웨어 요구사항 명세서 (SRS) v2.10

## 청년 복지 통합 플랫폼

| 항목 | 내용 |
|------|------|
| 문서 버전 | 2.14 |
| 작성일 | 2026-04-18 |
| 프로젝트 유형 | 졸업 프로젝트 (2인) |
| 변경 이력 | v2.13→v2.14: **데모 시나리오 문서화** — 회원가입, 로그인, 프로필/우선순위 설정, 정책 목록/검색, 추천 생성·조회, 북마크, 수신 거부, refresh/logout, CTR 분석 SQL을 포함한 실행 문서(`docs/demo-scenario.md`) 추가. v2.12→v2.13: **HTTPS 운영 설정 문서화** — Nginx 리버스 프록시 예시(`deploy/nginx/youth-welfare.conf`) 추가, `80 -> 443 -> 8082` 리다이렉트/프록시, Let’s Encrypt 인증서 경로, `X-Forwarded-*`, HSTS 기준을 배포 문서에 반영. v2.11→v2.12: **배포 기준선 문서화** — `.env.example` 보강, Docker Compose를 `app + db + redis` 3컨테이너 기준으로 정리, 서비스명 기반 DB/Redis 연결과 `APP_BASE_URL` 운영값 명시, 배포 가이드(`docs/deployment.md`) 추가. v2.10→v2.11: **운영 반영 및 검증 갱신** — 정책 목록/검색에 지역 필터와 이름순 정렬 반영. 알림 설정(`notification_min_score`), 이메일 `ai_reason`, 수신 거부 링크, 실패 재시도(30분/2시간) 반영. 북마크 200건 상한 및 30일+미북마크 삭제 배치 반영. 기존 DB용 수동 마이그레이션 SQL 및 MySQL+Redis 통합 테스트 추가. v2.9→v2.10: **챗봇 모듈 설계 반영** — 2차 구현 항목으로 chat/ 패키지 추가. 모듈 경계 원칙(chat→welfare 허용, chat→recommendation 금지). v2.8→v2.9: **확장형 MVP 구조** — 1차(11개) / 2차(9개) 테이블 분리. **Cold Start 전략** — `score_weights` 테이블 추가, 추천 이력 기반 rule/ai 가중치 자동 전환. **AI 점수 구조 수정** — `welfare_services.ai_score` 제거, AI 점수는 `user_recommendations`에만 존재. **스키마 무결성** — `service_tags` UNIQUE KEY 추가, `user_attributes.attr_type` ENUM→VARCHAR(30). **컬럼 수정** — `batch_date DATE`→`recommended_at DATETIME`, `reason`→`ai_reason`, `rule_weight_used`·`ai_weight_used` 추가. **unified_category** — 3개 API 분류 통합 필터용 컬럼 추가. **FR 수정** — FR-05-02 분야 필터를 unified_category 기반으로, FR-07-09 점수 가중치를 score_weights 기반으로, FR-07-14 CLOSED 처리를 user_recommendations 기준으로 수정 |

---

## 1. 소개

### 1.1 목적

본 문서는 "청년 복지 통합 플랫폼"의 기능적·비기능적 요구사항을 정의한다.

### 1.2 프로젝트 범위

온통청년, 복지로 중앙부처, 복지로 지자체 3개 공공 API에서 청년 복지·정책 정보를 수집하여 통합 저장하고, 사용자의 개인 상황 및 선택 우선순위에 기반한 2단계 추천(Retrieval → Re-ranking)으로 맞춤 정책을 제공한다. Cold Start 문제에 대응하기 위해 가중치 기반 테이블리드 추천을 적용하며, 확장형 MVP 구조로 단계적 고도화를 지원한다.

### 1.3 용어 정의

| 용어 | 정의 |
|------|------|
| 청년 | 만 18세 이상 39세 이하 |
| 군집(Cluster) | 나이대·소득구간으로 묶인 사용자 그룹 (2D). 1차: youth_all 단일 군집 |
| Retrieval | 군집 단위로 후보 정책을 넓게 추출하는 1단계 |
| Re-ranking | 개인의 우선순위 가중치로 후보를 재정렬하는 2단계 |
| rule_base_score | if-else 기본 가점 합산 (정규화 전) |
| rule_weighted_score | 기본 가점 × 우선순위 배율 합산 (정규화 전) |
| norm_rule | rule_weighted_score를 [0,1]로 정규화한 값 |
| norm_ai | ai_score(0~100)를 [0,1]로 정규화한 값 |
| final_score | norm_rule × rule_weight + norm_ai × ai_weight. ai_score NULL이면 norm_rule |
| score_weights | Cold Start 대응 가중치 설정 테이블. 추천 이력 수에 따라 단계 자동 전환 |
| Cold Start | 서비스 초기 사용자 행동 데이터 부족으로 AI 추천 신뢰도가 낮은 상태 |
| ai_score | AI가 특정 유저(또는 군집)에 대해 특정 정책에 부여한 점수(0~100). `user_recommendations`에만 존재 |
| ai_reason | AI가 해당 유저에게 해당 정책을 추천한 이유 1문장. `user_recommendations`에 저장 |
| unified_category | 3개 API의 서로 다른 분류 체계를 통합한 필터용 카테고리. `welfare_services` 컬럼 |
| A타입 | 개인화 후보. user_recommendations final_score 상위 정책 |
| B타입 | 신규 후보. 수집 후 24시간 이내 + 최소 적합도 통과 정책 |
| Batch API | OpenAI 비동기 배치 처리 API. 완료 최대 24시간, 50% 저렴 (2차 구현) |
| 확장형 MVP | 서비스 경계는 최종 설계와 동일하게 두고, 내부 구현만 단순화하는 개발 전략 |

### 1.4 규모 시나리오

| 시나리오 | 전체방문자 | DAU | TPS | 적용 시점 |
|---------|----------|-----|-----|----------|
| 졸업 데모 | 50~200 | ~10 | 0.01 | 현재 |
| 초기 이용 | 1,000~10,000 | ~300 | 0.1~1 | 출시 후 |
| 성공 초창기 | 100,000 | ~3,300 | 1~10 | 성장 시 |

---

## 2. 시스템 개요

### 2.1 아키텍처

```
[React] ── [Spring Boot] ── [MySQL 8.0]
                │ HTTPS
    [OpenAI API (1차: 실시간 / 2차: Batch)]
                │
          [Gmail SMTP]
```

### 2.2 기술 스택

| 계층 | 기술 | 비고 |
|------|------|------|
| Frontend | React + MUI | 모바일 퍼스트 |
| 상태 관리 | React Query + Zustand | |
| Backend | Spring Boot 3.x | API + 배치 + AI 연동 통합 |
| Database | MySQL 8.0+ | FULLTEXT ngram |
| AI API | OpenAI GPT-4o-mini | 1차: 실시간 / 2차: Batch API (50% 저렴) |
| HTTP 클라이언트 | Spring WebClient | |
| 인증 | JWT + Spring Security | HttpOnly 쿠키 |
| 알림 | Spring Mail + Gmail SMTP | |
| 배포 | EC2 **t4g.large** (2vCPU, 8GB, ARM) + Docker Compose | 컨테이너 3개(`app`, `db`, `redis`) |
| XML 파싱 | jackson-dataformat-xml | XXE 비활성화 |
| HTML 정제 | Jsoup | XSS 방지 |

### 2.3 인프라

| 시나리오 | 인프라 | 월비용 |
|---------|--------|---------|
| 졸업 데모 | EC2 t4g.large (8GB ARM) + Docker Compose | ~$25 |
| 성공 초창기 | EC2 t4g.xlarge + RDS | ~$80 |

---

## 3. 기능 요구사항

### FR-01. 회원가입 및 로그인

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-01-01 | 이메일·비밀번호 회원가입 | 필수 |
| FR-01-02 | 필수: 이메일, 비밀번호, 이름, 생년월일 | 필수 |
| FR-01-03 | 선택: 주소, 소득수준, 가구형태, 취업상태, 관심분야 | 필수 |
| FR-01-04 | 선택 미입력 시 해당 가점 건너뜀. 프로필 완성도 표시 | 필수 |
| FR-01-05 | 주소: Kakao 주소 검색 API | 필수 |
| FR-01-06 | 비밀번호 BCrypt 해싱 | 필수 |
| FR-01-07 | 로그인 성공 시 Access Token(30분) + Refresh Token(7일) 발급 | 필수 |
| FR-01-08 | Access Token 만료 시 Refresh Token으로 자동 재발급 | 필수 |
| FR-01-09 | Refresh Token: HttpOnly+Secure+SameSite=Strict 쿠키. Rotation + Reuse Detection | 필수 |
| FR-01-10 | 로그인 실패 5회 시 30분 잠금 | 필수 |

### FR-02. 추천 우선순위 설정

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-02-01 | 회원가입 후 또는 마이페이지에서 우선순위 설정 | 필수 |
| FR-02-02 | 선택지 7개 중 최대 5개를 1~5순위로 지정 (주거/금액/온라인/청년전용/교육취업/문화여가/마감임박) | 필수 |
| FR-02-03 | 미설정 시 해당 항목 ×1.0 | 필수 |
| FR-02-04 | 언제든 수정 가능. 다음 추천 계산부터 반영 | 필수 |
| FR-02-05 | 추천 표시 건수 기본 10건, 최대 30건 | 필수 |

### FR-03. 회원 탈퇴

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-03-01 | 마이페이지에서 탈퇴. 비밀번호 재입력 확인 | 필수 |
| FR-03-02 | 식별정보 전체 NULL (이메일→withdrawn, 주소·소득·취업상태 등 모두 NULL) | 필수 |
| FR-03-03 | `user_attributes`, `user_priorities` 즉시 삭제 | 필수 |
| FR-03-04 | 비식별화된 데이터는 통계용 보존 | 필수 |

### FR-04. 정책 조회 및 검색

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-04-01 | 목록 20건 페이징 | 필수 |
| FR-04-02 | 기본: ACTIVE + UPCOMING만 표시 | 필수 |
| FR-04-03 | "마감 포함" 토글 시 CLOSED 표시 | 필수 |
| FR-04-04 | 키워드 검색: MySQL FULLTEXT(ngram) | 필수 |
| FR-04-05 | 검색 결과: 관련도 + 조회수 정렬 | 필수 |
| FR-04-06 | 검색 키워드·건수 `search_logs` 기록 (2차 구현) | 선택 |

### FR-05. 필터링 검색

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-05-01 | 지역: 시도→시군구 2단 | 필수 |
| FR-05-02 | **분야: `unified_category` 기반 통합 분류** (일자리/주거/교육·직업훈련/금융·생활지원/문화·여가/건강·의료/가족·돌봄/안전·위기/참여·기회/기타). 3개 API의 이질적인 분류 체계를 수집 시 매핑하여 단일 드롭다운으로 제공 | 필수 |
| FR-05-03 | 출처: 온통청년/복지로중앙/복지로지자체 | 필수 |
| FR-05-04 | 상태: ACTIVE/CLOSED/UPCOMING/전체 | 필수 |
| FR-05-05 | 정렬: 조회수순/최신순/이름순 | 필수 |
| FR-05-06 | 다중 필터 조합 가능 | 필수 |

### FR-06. 정책 상세 조회

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-06-01 | 대상자 상세·선정기준·지원내용·신청방법 표시 (`welfare_service_details`) | 필수 |
| FR-06-02 | `welfare_service_details` JOIN 조회 (실시간 API 호출 없음) | 필수 |
| FR-06-03 | 30분 이내 재조회 시 조회수 미증가 | 필수 |
| FR-06-04 | 신청 URL 있으면 "신청하기" 버튼 | 필수 |

### FR-07. 맞춤 추천 — 2단계 Retrieval + Re-ranking

#### 파이프라인

**1차 구현 (실시간 단건 AI 호출)**

```
[추천 요청]
  ① 군집: youth_all 고정
  ② SQL WHERE 필터 (나이/지역 중심, 소득은 구조화 값 있는 경우만 직접 적용)
  ③ if-else 기본 가점 → rule_base_score
  ④ 상위 K건 선별
  ⑤ AiRecommendationGateway 실시간 호출 → ai_score, ai_reason 저장
  ⑥ score_weights 조회 → Cold Start 단계 결정
  ⑦ final_score 계산 → user_recommendations 저장 (recommended_at = 현재 시간)

[사용자 조회] DB 조회만 → 즉시 반환
```

| ID | 요구사항 | 우선순위 | 구현 단계 |
|----|---------|:--------:|:--------:|
| FR-07-01 | **군집화**: 1차 youth_all 단일 군집. 2차: 나이대(4구간) × 소득구간(2구간) = 최대 8개 군집 | 필수 | 1차→2차 |
| FR-07-02 | 2차 군집 폴백: `min_cluster_size`(기본 3) 미만이면 나이대 단일 군집 → "전체 청년" **2단계 폴백만** | 필수 | 2차 |
| FR-07-03 | SQL WHERE 필터: 나이/지역 pass/fail 우선. 소득은 **구조화 값이 존재하는 소스(YOUTH)만 직접 적용**, 복지로 계열은 null 통과 후 대상 태그(`저소득층`, `기초생활`)를 보조 신호로 사용 | 필수 | 1차 |
| FR-07-04 | if-else 기본 가점: 관심분야 태그 일치, 대상유형 태그 일치, 마감임박 중심. `onlineApply`, `청년전용(sourceType=YOUTH)`, `지원금 100만+`는 현재 데이터 신뢰도 부족으로 추천 가점에서 제외 | 필수 | 1차 |
| FR-07-05 | 상위 K=50건 후보 선별 | 필수 | 1차 |
| FR-07-06 | **신규 정책 가점 포함**: 수집 후 24시간 이내 신규 정책 중 `rule_base_score` 최솟값 M=5건을 후보 강제 추가. 점수 조정 없이 슬롯 배치에서만 노출 보장 | 필수 | 1차 |
| FR-07-07 | 우선순위 가중치: 1순위×2.0 / 2순위×1.6 / 3순위×1.3 / 4순위×1.1 / 5순위×1.0 / 미설정×1.0 | 필수 | 1차 |
| FR-07-08 | 정규화: 1차 단순 min-max. 2차 p5~p95 클리핑 + Min-Max → [0,1] | 필수 | 1차→2차 |
| FR-07-09 | **최종 점수**: `final_score = norm_rule × rule_weight + norm_ai × ai_weight`. 가중치는 `score_weights` 테이블에서 `recommendation_logs` 전체 건수 기준으로 결정 (COLD_START: rule 0.8/ai 0.2 → GROWTH: 0.6/0.4 → STABLE: 0.4/0.6). `ai_score` NULL 시 `final_score = norm_rule` | 필수 | 1차 |
| FR-07-10 | 2차 Batch 폴링: 가변 간격 (0~30분: 5분, 30~120분: 15분, 2~6시간: 30분, 6시간~: 1시간) | 필수 | 2차 |
| FR-07-11 | **2차 Batch Fallback**: 재시도 없음. 새벽 6시 하드 데드라인까지 미완료 시 `rule_weighted_score`만으로 Re-ranking | 필수 | 2차 |
| FR-07-12 | 2차: 새벽 6시 하드 데드라인 → 미완료 군집 전체 Fallback | 필수 | 2차 |
| FR-07-13 | 2차: Partial 완료 처리 → expired 시 부분 결과 먼저 저장 후 완료 군집 즉시 Re-ranking | 필수 | 2차 |
| FR-07-14 | **CLOSED 전환 정책 처리**: `welfare_services`에 ai_score 없음. CLOSED 전환 시 해당 정책의 `user_recommendations.ai_score = NULL` 리셋 (새벽 3시) | 필수 | 1차 |
| FR-07-15 | 사용자 조회 시 DB 조회만. 실시간 AI 추가 호출 없음 | 필수 | 1차 |
| FR-07-16 | 추천 화면: `final_score` + **`ai_reason`** + 우선순위 태그 + "신규" 뱃지(`ai_score NULL`) | 필수 | 1차 |
| FR-07-17 | 북마크 가능. 사용자별 최대 200건 유지, 30일+미북마크 삭제 | 필수 | 1차 |
| FR-07-18 | 2차: `normalization_stats` 테이블에 배치별 p5·p95 기록 | 필수 | 2차 |

### FR-07-신규. recommendation_logs — 추천 발송 이력 기록

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-07-20 | **recommendation_logs 테이블**: 추천 발송 시마다 정책별 1회 기록. "AI vs 룰 CTR 비교", "Cold Start 단계별 추천 품질" 분석을 위한 핵심 데이터 | 필수 |
| FR-07-21 | **is_fallback**: TRUE = rule만(Fallback), FALSE = AI 포함 | 필수 |
| FR-07-22 | **is_clicked**: 알림 링크 클릭 → 정책 상세 조회 시 TRUE 업데이트 | 필수 |
| FR-07-23 | 알림 링크에 `?log_id={id}` 포함. 클릭 시 `is_clicked = TRUE` 업데이트 | 필수 |
| FR-07-24 | **rule_weight_used, ai_weight_used**: 발송 시점 적용 가중치 기록. Cold Start 단계별 CTR 분석용 | 필수 |
| FR-07-25 | recommendation_logs 영구 보관 (포트폴리오 분석용) | 필수 |

### FR-08. 조회수 랭킹

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-08-01 | ACTIVE 정책 조회수 상위 N건 랭킹 | 필수 |
| FR-08-02 | `view_count`(자체)·`api_view_count`(원본 API) 분리 | 필수 |

### FR-09. 알림

| ID | 요구사항 | 우선순위 | 구현 단계 |
|----|---------|:--------:|:--------:|
| FR-09-01 | 알림 수신 여부, 주기(일간/주간/없음), 최소 점수 설정 | 필수 | 1차 |
| FR-09-02 | Spring Mail + Gmail SMTP 발송. 정책명 + ai_reason 삽입 | 필수 | 1차 |
| FR-09-03 | 발송 실패 시 30분/2시간 후 최대 2회 재시도 | 필수 | 1차 |
| FR-09-04 | 1차: top 3 발송. 2차: 슬롯 배치 [A, A, B?] | 필수 | 1차→2차 |
| FR-09-05 | **슬롯 배치 — A타입** (2차): `user_recommendations`에서 `final_score` 기준 상위 정책 | 필수 | 2차 |
| FR-09-06 | **슬롯 배치 — B타입** (2차): 수집 후 24시간 이내 + `base_score ≥ 0.5` 통과 신규 정책 | 필수 | 2차 |
| FR-09-07 | **슬롯 패턴** (2차): `[A, A, B?]` 3슬롯 하드코딩 | 필수 | 2차 |
| FR-09-08 | B타입 정렬 (2차): `base_score` 내림차순. B타입 0건이면 `[A, A, A]` | 필수 | 2차 |
| FR-09-09 | 수신 동의 수집 기록, 2년 경과 시 재동의 요청 | 필수 | 1차 |
| FR-09-10 | 수신 거부 링크 포함, 거부 즉시 반영 | 필수 | 1차 |

### FR-10. 데이터 수집 (배치)

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-10-01 | 매일 새벽 2시 3개 공공 API 수집 | 필수 |
| FR-10-02 | 온통청년 JSON, 복지로 XML (XXE 비활성화) | 필수 |
| FR-10-03 | API별 DTO 3종 → WelfareServiceMapper 공통 Entity | 필수 |
| FR-10-04 | "청년 포함" 필터 + `isYouthRelevant()` 2차 검증 | 필수 |
| FR-10-05 | UPSERT(source_type + source_id) 중복 방지 | 필수 |
| FR-10-06 | Jsoup HTML strip 후 저장 | 필수 |
| FR-10-07 | 수집 시 `unified_category` 매핑 저장 | 필수 |
| FR-10-08 | 새벽 3시: 만료 정책 `status = CLOSED` 갱신 + 해당 정책의 `user_recommendations.ai_score = NULL` 리셋 | 필수 |
| FR-10-09 | 수집 실패 시 1시간 후 재시도, 최대 2회. API별 독립 실행 | 필수 |

### FR-11. 사용자 프로필 관리

| ID | 요구사항 | 우선순위 |
|----|---------|:--------:|
| FR-11-01 | 마이페이지 개인정보 수정 | 필수 |
| FR-11-02 | 우선순위 설정, 표시 건수 설정, 알림 설정(`notification_yn`, `notification_period`, `notification_min_score`) | 필수 |
| FR-11-03 | 추천 목록, 북마크, 알림, 프로필, 우선순위, 탈퇴 | 필수 |
| FR-11-04 | 프로필 완성도 % 표시 | 필수 |

---

## 4. 비기능 요구사항

### NFR-01. 성능

| ID | 요구사항 | 목표 |
|----|---------|------|
| NFR-01-01 | 정책 목록 조회 | 500ms |
| NFR-01-02 | 정책 상세 조회 | 300ms |
| NFR-01-03 | FULLTEXT 검색 | 500ms |
| NFR-01-04 | 추천 목록 조회 | 300ms |
| NFR-01-05 | 수집 배치 전체 | 5분 |
| NFR-01-06 | Re-ranking 계산 | 졸업 1분 / 1만 사용자 ~30분 |

### NFR-02. 보안

| ID | 요구사항 |
|----|---------|
| NFR-02-01 | 비밀번호 BCrypt |
| NFR-02-02 | 이메일 인덱스 + UNIQUE + HTTPS |
| NFR-02-03 | 전화번호 AES-256 암호화 |
| NFR-02-04 | HTTPS 필수 구간 |
| NFR-02-05 | API 키 .env + .gitignore |
| NFR-02-06 | CORS React origin만 허용 |
| NFR-02-07 | XXE 비활성화 |
| NFR-02-08 | MySQL 포트 Docker 내부만 |
| NFR-02-09 | 개인정보 처리방침 페이지 |
| NFR-02-10 | Refresh Token HttpOnly 쿠키 + Rotation + Reuse Detection |
| NFR-02-11 | 로그인 실패 5회 30분 잠금 |
| NFR-02-12 | AI 프롬프트: 군집 범주값만 전송 (개인 식별 정보 제외) |
| NFR-02-13 | 수집 데이터 Jsoup HTML strip. dangerouslySetInnerHTML 금지 |
| NFR-02-14 | search_logs user_id 3개월 후 NULL (2차) |

### NFR-03. 가용성

| ID | 요구사항 |
|----|---------|
| NFR-03-01 | 외부 API 장애 시 기존 DB로 서비스 유지 |
| NFR-03-02 | ai_score NULL 시 rule_weighted_score만으로 추천 (NULL-safe) |
| NFR-03-03 | 신규 정책을 점수 조정 없이 슬롯 배치로 노출 보장 |
| NFR-03-04 | Docker 볼륨 마운트로 MySQL 데이터 유실 없음 |

---

## 5. 외부 인터페이스

| 서비스 | 용도 | 비용 |
|--------|------|------|
| OpenAI GPT-4o-mini (실시간) | 1차 추천 점수 + ai_reason | 건당 과금 |
| OpenAI GPT-4o-mini Batch API | 2차 추천 점수 + ai_reason | 실시간의 50% |
| Kakao 주소 검색 | 구조화 주소 | 무료 |
| Gmail SMTP | 알림 발송 | 무료, 500건/일 |

---

## 6. 데이터 요구사항

### 6.1 테이블 목록

#### 1차 구현 (11개) — 지금 바로 만들 것

| # | 테이블 | 역할 | 주요 변경 |
|---|---|---|---|
| 1 | `users` | 회원 기본정보 | |
| 2 | `user_attributes` | 관심분야·대상 선택 | `attr_type` VARCHAR(30) — ENUM 제거 |
| 3 | `user_priorities` | 우선순위 설정 | |
| 4 | `priority_options` | 선택지 마스터 | |
| 5 | `welfare_services` | 정책 통합 (FULLTEXT) | ai_score **제거**, `unified_category` 추가 |
| 6 | `welfare_service_details` | 정책 상세 | |
| 7 | `service_regions` | 정책-지역 다대다 | |
| 8 | `service_tags` | 정책 태그 | **UNIQUE KEY uq_st** (service_id, tag_type, tag_value) 추가 |
| 9 | `user_recommendations` | 추천 결과 | `recommended_at DATETIME`, `ai_score`, `ai_reason`, `rule/ai_weight_used` |
| 10 | `recommendation_logs` | 추천 클릭 추적 | `rule/ai_weight_used` 추가 |
| 11 | `score_weights` | Cold Start 가중치 설정 | **신규** |

#### 2차 확장 (11개) — 나중에 추가

| # | 테이블 | 역할 | 추가 시점 |
|---|---|---|---|
| 12 | `user_clusters` | 사용자↔군집 매핑 | 군집화 구현 시 |
| 13 | `cluster_ai_results` | 군집×정책 AI 결과 (7일 TTL) | Batch AI 전환 시 |
| 14 | `batch_jobs` | Batch 제출 이력·상태 | Batch AI 전환 시 |
| 15 | `normalization_stats` | 배치별 p5·p95 | 정규화 고도화 시 |
| 16 | `notifications` | 알림 헤더 | 알림 시스템 구현 시 |
| 17 | `notification_services` | 알림-정책 매핑 | 알림 시스템 구현 시 |
| 18 | `api_sync_logs` | 수집 배치 이력 | 배치 안정화 후 |
| 19 | `search_logs` | 검색 키워드 | 검색 기능 안정화 후 |
| 20 | `service_view_logs` | 조회수 중복 방지 | 조회수 정교화 시 |
| 21 | `chat_sessions` | 챗봇 대화 세션 | 챗봇 모듈 구현 시 |
| 22 | `chat_messages` | 챗봇 대화 메시지 | 챗봇 모듈 구현 시 |

### 6.2 데이터 보존

| 데이터 | 보존 |
|--------|------|
| 만료 정책 (`welfare_services`) | 영구 (CLOSED) |
| 추천 결과 (`user_recommendations`) | 30일 + 미북마크 삭제, 북마크 200건 상한 |
| 검색 로그 (`search_logs`, 2차) | 3개월 후 user_id NULL |
| 탈퇴 사용자 | 비식별화 보존 |
| `cluster_ai_results` (2차) | 7일 TTL |
| `batch_jobs` (2차) | 30일 |
| `normalization_stats` (2차) | 90일 |
| `recommendation_logs` | 영구 보관 (CTR + 가중치 단계 분석) |
| `score_weights` | 영구 보관 (이력 관리) |

---

## 7. 화면 요구사항

| 페이지 | 주요 기능 | 인증 |
|--------|-----------|:----:|
| 메인 | 인기 랭킹, 추천 요약, 검색바 | X |
| 정책 목록 | 검색 + 필터(unified_category 포함) + 정렬 + 페이징 | X |
| 정책 상세 | 상세 정보, 신청 링크 | X |
| 마이페이지 | 추천, 북마크, 알림, 프로필, 우선순위, 탈퇴 | O |
| 추천 목록 | final_score + ai_reason + 우선순위 태그 + 신규 뱃지 | O |
| 회원가입 | 필수 4 + 선택 5 + 우선순위(선택) | X |
| 로그인 | 이메일/비밀번호 | X |

---

## 8. 제약사항

| # | 제약 |
|---|------|
| C-01 | 공공 API 일일 1,000건 제한 |
| C-02 | 복지로 API XML만 지원 |
| C-03 | EC2 t4g.large RAM 8GB (ARM Graviton2) |
| C-04 | 개발 13주, 2명 |
| C-05 | Gmail SMTP 500건/일 |
| C-06 | EC2 1대 = SPOF |
| C-07 | @Scheduled 단일 인스턴스 전용 |
| C-08 | OpenAI Batch API 완료 최대 24시간 |

---

## 9. 설계 결정 근거

| 결정 | 선택 | 이유 |
|------|------|------|
| 확장형 MVP | 1차/2차 구조 분리 | 처음부터 서비스 경계 확정 → 재설계 없이 내부만 교체. 발표 시 "확장성 고려한 설계"로 설명 가능 |
| Cold Start | `score_weights` 테이블 (rule 0.8→0.4) | 초기 AI 데이터 부족 시 rule 우선. 이력 축적 후 자동 전환. 가중치 변화가 recommendation_logs에 기록되어 분석 가능 |
| ai_score 위치 | `user_recommendations`에만 | AI 점수는 유저×서비스 단위. `welfare_services`에 두면 모든 유저가 동일 점수 공유 → 개인화 불가 |
| user_attributes.attr_type | VARCHAR(30) | ENUM은 새 속성(LIFE_STAGE, 학력 등) 추가 시 ALTER TABLE 필요. VARCHAR + 애플리케이션 Enum으로 유연성 확보 |
| service_tags UNIQUE KEY | `uq_st (service_id, tag_type, tag_value)` | 중복 태그 삽입 → rule_base_score 이중 합산 버그 방지. RDBMS 무결성 제약 활용 |
| recommended_at | DATETIME | 실시간 방식에서 DATE만으로 같은 날 중복 구분 불가. DATETIME이 배치 시간도 포괄하여 2차 호환 |
| ai_reason | `user_recommendations`에 저장 | 유저별 AI 추천 이유. 추천 UI 및 알림 템플릿에 직접 표시 |
| unified_category | `welfare_services` 별도 컬럼 | 3개 API 분류 체계 불일치. 필터 UI 통합을 위해 수집 시 매핑. 원본은 category_main/service_tags 보존 |
| 군집화 | 1차 youth_all → 2차 나이대×소득 2D | 졸업 전 사용자 수에서 2D는 youth_all 수렴. ClusterService 경계 유지로 2차 교체 용이 |
| Batch Fallback | 재시도 없음, 6시 하드 데드라인 | 재시도 후 구현·디버깅 비용 > 단순 Fallback |
| 슬롯 배치 | [A,A,B?] 3슬롯 하드코딩 | 사용자 피로도 감소. 동적 계산 제거로 버그 추적 용이 |
| recommendation_logs | rule/ai_weight_used 포함 | 가중치 단계별 CTR 분석 → 포트폴리오 핵심 데이터 |
| FastAPI | 제거 (Spring 단일화) | 딥러닝 서빙 없는 2인 졸업작품에서 불필요. Gateway 인터페이스로 분리 가능한 구조 유지 |
| 챗봇 | 2차 구현 | 1차에서 구조만 잡아두고 시간 여유 시 구현. 로그인 전용, 로그아웃 시 세션 삭제. chat/ → welfare/ 허용, recommendation/ 금지 |
| 알림 | Gmail SMTP 단독 | CoolSMS 카카오 알림톡 제거. Gmail 무료 한도(500건/일)로 졸업 데모에 충분 |
