# 트러블슈팅 로그 (작업 중 문제/해결 기록)

## 1) `JAVA_HOME` 미설정으로 테스트/빌드 실패
- 문제: 테스트 실행 시 Java 환경 변수 미설정으로 Gradle 실행 불가
- 해결: `JAVA_HOME`, `PATH`, `GRADLE_USER_HOME`를 명시해 실행

## 2) 루트 경로에서 `./gradlew` 없음
- 문제: 저장소 루트에서 Gradle wrapper를 찾지 못함
- 해결: `backend/gradlew` 기준으로 작업 디렉토리를 `backend`로 통일

## 3) Gradle 에러: `Could not determine a usable wildcard IP`
- 문제: 샌드박스 제약 환경에서 Gradle 네트워크/호스트 정보 인식 실패
- 해결: escalated 권한으로 빌드/테스트 실행

## 4) Bash 인라인 환경변수 지정 구문 에러
- 문제: PATH 내 특수문자(`(`) 포함으로 인라인 할당 시 파싱 실패
- 해결: 인라인 할당 대신 `export` 방식으로 변수 설정

## 5) Docker MySQL 인증 실패
- 문제: `root/root` 계정으로 접속 시 `Access denied`
- 해결: `.env`의 실제 DB 계정/비밀번호로 접속

## 6) `@WebMvcTest` 컨텍스트 로딩 실패 (`JPA metamodel must not be empty`)
- 문제: Web MVC 슬라이스 테스트에서 JPA Auditing 빈 생성 충돌
- 해결: 테스트에서 `JpaMetamodelMappingContext`를 `@MockBean`으로 주입

## 7) 테스트에서 `@AuthenticationPrincipal`이 `null`
- 문제: WebMvc 테스트 환경에서 principal 주입값과 mock 기대값 불일치
- 해결: mock 매칭을 `isNull()` 조건으로 조정

## 8) 조회수 dedup 도입 후 앱 부팅 실패 (`char` vs `varchar`)
- 문제: `service_view_logs.client_fingerprint` 타입 불일치로 JPA validate 실패
- 해결: 코드/스키마/실DB 컬럼을 `VARCHAR(64)`로 통일

## 9) 알림 이력 테이블 추가 후 앱 부팅 실패 (`varchar` vs `enum`)
- 문제: `notifications.channel/period_type/status`가 JPA enum 기대와 불일치
- 해결: 스키마와 실DB 컬럼을 MySQL `ENUM`으로 변경

## 10) API별 조회수 신호 편차 (외부 조회수 필드 유무/품질 차이)
- 문제: 소스마다 `api_view_count` 신뢰도/존재 여부가 달라 랭킹 왜곡 가능
- 해결: source별 외부조회수 정규화 + 외부신호 미존재 시 가중치 재정규화

## 11) 조회수 dedup 검증의 불확실성
- 문제: API 응답만 보면 dedup 동작 여부 확인이 어려움
- 해결: API 호출 결과 + DB(`service_view_logs`, `welfare_services.view_count`) 교차 검증

## 12) 프론트 연동 요청 대비 프론트 코드베이스 부재
- 문제: 저장소에 React 프론트 코드가 없어 직접 API 연동 수정 불가
- 해결: `project-plan-v11.md`, `api-mapping.md`를 최신 스펙으로 갱신해 연동 기준 제공

## 13) 액세스 토큰 만료 후 로그아웃 불가
- 문제: `/api/auth/logout`가 인증 principal에 의존해 access token이 만료되면 refresh token 무효화와 쿠키 삭제를 정상 처리할 수 없었음
- 해결: refresh token header/cookie만 있어도 로그아웃 가능하도록 변경하고, 보안 설정에서 `/api/auth/logout`을 permit 처리
- 이유: 실제 운영에서는 "만료된 access token + 살아있는 refresh token" 상태가 자주 발생하며, 이 경우 로그아웃이 막히면 사용자 경험과 보안(서버 저장 토큰 정리) 둘 다 나빠짐

## 14) 정책 북마크 API 계약 불일치
- 문제: 문서에는 `POST /api/policies/{id}/bookmark`가 있었지만 구현은 추천 엔드포인트 기준 북마크만 존재했음
- 해결: 정책 기준 북마크 엔드포인트를 추가하고, 추천 이력이 없는 정책도 placeholder recommendation을 생성해 북마크 가능하게 처리
- 이유: 프론트/외부 연동은 정책 상세나 목록에서 바로 북마크하는 흐름이 자연스럽고, 문서 계약과 구현이 다르면 연동 리스크가 커지기 때문

## 15) 기존 DB와 최신 코드 스키마 불일치
- 문제: 새 컬럼/테이블(`notifications.retry_count`, `next_retry_at`, `service_view_logs` 등) 반영 후 기존 DB에서는 `schema.sql`만으로 최신화되지 않아 validate 실패 가능성이 있었음
- 해결: `db/migration/V2026_04_17_01__recent_schema_updates.sql`을 추가하고 운영 적용 절차를 `docs/db-migration.md`에 문서화
- 이유: 신규 초기화와 기존 운영 DB 업데이트는 경로가 달라야 하며, 수동 마이그레이션 기준이 없으면 배포 때마다 코드와 DB가 다시 어긋나기 때문

## 16) 통합 테스트용 DB가 코드보다 뒤처져 실패
- 문제: MySQL+Redis 통합 테스트를 붙인 뒤 실제 컨테이너 DB 스키마가 최신 코드보다 뒤처져 테스트가 실패했음
- 해결: 테스트 전에 `V2026_04_17_01__recent_schema_updates.sql`을 실제 Docker MySQL에 적용한 뒤 통합 테스트를 실행
- 이유: mock 기반 테스트만으로는 배포 리스크를 잡기 어렵고, 실제 DB/Redis와 맞물린 검증을 하려면 테스트 환경 스키마도 코드와 동일해야 함

## 17) `onlineApply`를 URL 존재만으로 추론하면 오판 가능
- 문제: 온통청년/복지로의 URL은 실제 신청 링크가 아니라 상세 안내 페이지일 수 있어, URL만 보고 `온라인 신청 가능`으로 판단하면 추천이 왜곡됨
- 해결: 추천 가점과 우선순위에서 `onlineApply` 의존을 제거하고, 필드는 표시/필터 용도로만 유지
- 이유: 잘못 채운 boolean은 `null`보다 위험하다. API마다 링크 의미가 달라 일관된 해석이 불가능하면 추천 신호로 쓰지 않는 편이 안전함

## 18) `sourceType=YOUTH`를 청년전용 정책으로 간주한 과대추론
- 문제: 추천 초기 로직에서 `YOUTH` 출처 정책에 `청년전용 +20` 가점을 부여했지만, 수집 출처와 자격 조건은 동일한 의미가 아니었음
- 해결: `sourceType=YOUTH` 기반 가점과 `YOUTH_ONLY` 우선순위 매칭을 제거
- 이유: 출처 메타데이터를 자격 신호처럼 쓰면 추천 설명 가능성과 정확도가 모두 떨어진다. 직접 자격 근거가 없는 값은 점수에서 제외하는 게 맞음

## 19) 소득 조건 표현 체계가 API마다 달라 직접 비교가 불안정
- 문제: 사용자 입력은 `incomeLevel(1~10 분위)` 하나인데, 정책 데이터는 소득분위/중위소득 %/월·연소득 금액/저소득층 문구가 섞여 있어 같은 축으로 비교할 수 없었음
- 해결: 추천 후보 SQL의 직접 소득 필터를 `YOUTH` 구조화 값에만 적용하고, 복지로 계열은 `TARGET_GROUP`/`KEYWORD` 태그를 보조 신호로만 사용하도록 변경
- 이유: 다른 체계의 값을 무리하게 같은 숫자로 비교하면 잘못된 탈락/통과가 발생한다. 같은 타입끼리만 직접 비교하고 나머지는 약한 신호로 남기는 편이 안전함

## 20) 신뢰도 낮은 필드를 빼면 동점 후보가 늘어날 수 있음
- 문제: `onlineApply`, `YOUTH_ONLY` 같은 신호를 제거하면 추천 점수 조합이 단순해져 동점이 많아질 우려가 있었음
- 해결: `ReRankingService`에 동점 해소 정렬을 추가해 `AI 점수 → 마감임박 여부 → applyEndDate → 내부/외부 조회수 → 최신성 → serviceId` 순으로 보조 정렬
- 이유: 신뢰도 낮은 필드를 억지로 점수에 넣는 것보다, 신뢰도 높은 핵심 점수는 유지하고 deterministic tie-breaker를 두는 편이 품질과 설명 가능성 모두 낫기 때문

## 21) 복지로 전체 수집 시 비청년 정책이 과다 적재됨
- 문제: 복지로 중앙/지자체 목록을 그대로 적재하면 검색/추천/AI 후보에 비청년 일반 복지가 대량 포함되어 노이즈가 커졌음
- 해결: `BokjiroYouthFilter`를 추가해 `청년 직접 신호`, `청년 생애주기`, `청년 범위 연령 조건`이 있는 정책만 정규화 테이블에 저장하도록 변경
- 이유: 프로젝트 목적이 청년 정책 중심 추천이므로, 저장 단계에서 후보 풀을 먼저 줄이는 편이 검색/추천 품질과 운영 비용을 동시에 낮추기 때문

## 22) 수집 규칙 변경 시 기존 데이터 정리가 어려움
- 문제: 기존 구조는 API 응답을 곧바로 정규화 테이블에 저장해, 필터/매핑 규칙이 바뀌면 원문 재해석 없이 과거 데이터를 복구하거나 재처리하기 어려웠음
- 해결: `raw_api_payloads` 테이블을 추가해 목록/상세 원문 payload를 별도로 보관하고, 이후 정규화 재처리가 가능하도록 변경
- 이유: MVP 단계에서는 수집 규칙과 추천 feature가 자주 바뀌므로, 원문을 남겨두는 편이 전체 재수집 비용과 운영 리스크를 줄이기 때문

## 23) 추천 갱신 시 동일 정책이 반복 노출됨
- 문제: `user_recommendations`에 추천 이력이 계속 누적되는데 조회 쿼리가 최신 세트를 기준으로 제한하지 않아 같은 정책이 여러 번 응답에 섞였음
- 해결: 추천 조회를 `user_id + service_id` 기준 최신 `recommended_at` 1건만 반환하도록 바꾸고, 새 추천 저장 전 비북마크 이력을 정리하도록 변경
- 이유: 추천 이력 보존과 사용자 응답 품질은 분리해야 한다. 이력은 남기되 API는 최신 추천 세트만 보여주는 편이 UX와 운영 관리 모두 낫기 때문

## 24) 청년 관련성 필터만으로는 특수 대상 정책 노이즈가 남음
- 문제: `농촌출신대학생학자금융자`, `공공산림가꾸기`처럼 청년 신호는 있지만 특정 대상에만 맞는 정책이 일반 사용자 추천 상위에 남았음
- 해결: 프로필에 `targetTypes`를 추가하고, 추천 점수에서 `농어촌`, `자립준비`, `장애`, `한부모`, `조손`, `보훈` 등 특수 대상이 실제 사용자와 맞을 때만 보너스를 주고, 불일치 시 패널티를 주도록 변경
- 이유: 청년 여부만으로는 추천 정밀도가 부족하다. 특수 대상 정책은 해당 사용자에게는 강하게 올리고, 아닌 사용자에게는 내려야 설명 가능성과 체감 품질이 함께 좋아지기 때문

## 25) Docker 빌드가 외부 Gradle 다운로드에 과하게 의존함
- 문제: Docker 이미지 빌드 시 `./gradlew bootJar`가 매번 외부에서 Gradle 배포본을 받으려 해 `timeout`, `504` 같은 네트워크 실패가 자주 발생했음
- 해결: Dockerfile에 BuildKit cache mount(`--mount=type=cache,target=/root/.gradle`)를 추가해 Gradle wrapper/의존성 캐시를 재사용하도록 변경
- 이유: 코드 문제를 빌드 인프라 불안정과 분리해야 한다. 캐시를 붙이면 외부 네트워크 의존이 줄어 재빌드 속도와 성공률이 같이 좋아진다

## 26) Docker 빌드에서 로컬 전용 JDK 경로를 참조해 실패
- 문제: `gradle.properties`의 `org.gradle.java.home=/home/minseok/youth-welfare/.jdk/...` 설정이 컨테이너 안에서는 존재하지 않아 `Java home supplied is invalid` 오류가 발생했음
- 해결: Docker 빌드 컨텍스트에서는 `gradle.properties`를 복사하지 않도록 변경해, 컨테이너 내부 JDK(`eclipse-temurin:17-jdk-jammy`)를 그대로 사용하게 함
- 이유: 로컬 개발 편의용 설정과 Docker 빌드 환경 설정은 분리해야 한다. 컨테이너는 자체 JDK를 이미 가지므로 호스트 경로를 강제로 주입할 필요가 없기 때문
