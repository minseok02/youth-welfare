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
- 해결: `api-mapping.md`와 당시 프로젝트 플랜을 최신 스펙으로 갱신해 연동 기준 제공

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

## 27) 프론트 메인 페이지 정렬/검색 UI와 백엔드 API 계약 차이
- 문제: 메인 페이지 더미 데이터는 마감임박 정렬과 전체 검색 페이지 수를 로컬 계산했지만, 백엔드 정책 목록 API는 `LATEST/VIEWS/NAME`만 지원하고 검색 API는 총건수/총페이지를 반환하지 않음
- 해결: 메인 페이지 정렬 옵션을 백엔드가 지원하는 `조회수순/최신순/이름순`으로 맞추고, 검색 결과는 응답 길이 기준으로 다음 페이지 존재 여부만 보수적으로 표시
- 이유: 프론트에서 지원하지 않는 정렬을 보내면 `INVALID_INPUT`이 발생한다. 검색 페이징 계약은 백엔드 응답 스펙을 보완하기 전까지 프론트가 정확한 총페이지를 단정하면 안 됨

## 28) `api_sync_logs.status` 타입 불일치로 통합 테스트 실패
- 문제: 통합 테스트 DB에 `api_sync_logs` 테이블이 없었고, 최초 migration 적용 후에도 `status`가 `VARCHAR(30)`라 Hibernate schema validation이 MySQL `ENUM('running','success','partial_success','failed','skipped')`를 기대하며 실패했음
- 해결: `schema.sql`과 `V2026_04_23_01__add_api_sync_logs.sql`의 `status` 컬럼을 ENUM으로 수정하고, 테스트 DB에도 동일하게 반영한 뒤 `./gradlew integrationTest --no-daemon`을 재실행해 통과 확인
- 이유: 이 프로젝트는 MySQL 환경에서 `@Enumerated(EnumType.STRING)` 컬럼을 실제 `ENUM` 타입으로 검증한다. 신규 테이블 migration도 기존 enum 컬럼 정책과 맞춰야 배포/테스트 DB에서 JPA validate가 실패하지 않음

## 29) 같은 초 안의 추천 refresh 재실행 시 유니크 키 충돌
- 문제: `user_recommendations`의 유니크 키가 `(user_id, service_id, recommended_at)`인데 MySQL `DATETIME`이 초 단위로 저장되어, 북마크 보존 상태에서 같은 초 안에 추천 refresh를 다시 호출하면 동일 정책 insert가 중복될 수 있었음
- 해결: 추천 저장 시 기존 최신 추천의 `recommended_at`이 현재 초보다 같거나 늦으면 새 추천 저장 시각을 기존 최신 시각보다 1초 뒤로 조정
- 이유: 빠른 재시도, 더블클릭, 통합 테스트 반복 실행처럼 같은 초 안에 refresh가 재호출되는 상황은 실제로 발생할 수 있다. 저장 시각을 단조 증가시키면 이력 보존과 북마크 유지 정책을 깨지 않고 유니크 키 충돌을 피할 수 있음

## 30) 수집은 성공했지만 `api_sync_logs`가 비어 있음
- 문제: `POST /api/admin/collect/youth`가 200으로 완료됐는데 `api_sync_logs` 테이블에는 기록이 남지 않았음
- 해결: 실행 중인 `youth-welfare-app` 이미지가 최신 코드 이전 빌드임을 확인하고 `docker compose up -d --build app`으로 재빌드/재기동한 뒤 다시 수집 요청을 실행해 실패 로그 기록을 확인
- 이유: DB migration만 적용해도 앱 컨테이너가 최신 코드가 아니면 `ApiSyncLogService` 호출 경로가 반영되지 않는다. 수집/관측성 변경 검증은 DB 스키마와 앱 이미지 버전을 같이 맞춘 뒤 해야 함

## 31) 온통청년 수집 중 일시적 400 응답
- 문제: 실제 API key로 `POST /api/admin/collect/youth`를 실행했을 때 온통청년 page 10에서 한 차례 400 응답이 발생해 `COL001`로 종료됐음
- 해결: 같은 key와 같은 page 조건을 직접 호출해 200 응답을 확인한 뒤 앱 수집을 재실행했고, 2266건 요청/저장 및 `api_sync_logs.status=success`를 확인
- 이유: 외부 공공 API는 같은 요청도 일시적으로 실패할 수 있다. 실패 로그가 남는지 먼저 확인하고, 재시도 또는 재실행 시 성공하는지 DB 저장 건수와 `api_sync_logs`를 같이 봐야 함

## 32) Gmail SMTP smoke test 환경변수 미로딩
- 문제: Gmail 계정 정보를 입력했다고 판단했지만 실제 `.env`의 `GMAIL_USERNAME`, `GMAIL_PASSWORD` 값이 빈 문자열이라 smoke test가 환경변수 검증 단계에서 실패했음
- 해결: `.env` 저장 상태를 길이 기준으로 확인한 뒤 값을 다시 저장했고, `RUN_SMTP_SMOKE=true`로 실제 Gmail SMTP 발송 테스트를 재실행해 성공 확인
- 이유: `.env`는 IDE에서 입력 후 저장되지 않았거나 다른 파일을 수정하면 실행 프로세스에 반영되지 않는다. 비밀값은 출력하지 말고 길이/존재 여부만 확인해 설정 반영 상태를 검증해야 함

## 33) Gmail SMTP smoke test가 Gradle 캐시로 재발송되지 않음
- 문제: `SMTP_SMOKE_TO`를 추가한 뒤 smoke test를 다시 실행했지만 Gradle이 `:test UP-TO-DATE`로 판단해 실제 메일 발송이 다시 수행되지 않았음
- 해결: Gmail SMTP smoke test 실행 명령에 `--rerun-tasks`를 붙여 테스트를 강제로 재실행했고, 지정 수신자 대상으로 발송 성공을 확인
- 이유: smoke test는 외부 SMTP 발송이라는 부수효과를 확인하는 작업이라 Gradle 캐시가 켜지면 검증 의미가 사라질 수 있다. 실제 재발송 확인 시에는 `--rerun-tasks`를 사용해야 함

## 34) 정책 목록/상세 첫 렌더에서 북마크 상태가 항상 꺼져 보임
- 문제: 정책 북마크 토글 API는 있었지만 목록/검색/상세 조회 응답에 사용자별 `bookmarked` 값이 없어, 로그인 사용자의 기존 북마크도 프론트 첫 렌더에서는 항상 `false`로 보였음
- 해결: 정책 목록/검색/상세 응답 DTO에 `bookmarked` 필드를 추가하고, 최신 추천 이력 기준 북마크 상태를 함께 내려주도록 수정한 뒤 프론트가 초기 상태로 그대로 사용하게 변경
- 이유: 토글 API만 있고 초기 상태 조회 계약이 없으면 목록-상세-마이페이지 간 북마크 표시가 쉽게 어긋난다. 사용자별 상태성 필드는 읽기 API와 쓰기 API를 같이 맞춰야 재발을 막을 수 있음

## 35) 검색 API 총건수는 DB count만으로는 정확하지 않음
- 문제: 정책 검색은 DB FULLTEXT 결과에 청년 관련성 후처리 필터를 한 번 더 적용하므로, 원본 검색 SQL의 count만 쓰면 프론트가 보는 실제 결과 수와 `totalElements`가 어긋날 수 있었음
- 해결: 검색 응답을 `content + totalElements + totalPages + hasNext` 구조로 바꾸고, 검색 전체 결과를 배치 단위로 스캔하면서 청년 후처리 필터 적용 뒤 최종 total count를 계산하도록 수정
- 이유: 페이지 메타데이터가 실제 화면 결과와 다르면 UX가 바로 깨진다. 현재 데이터 규모에서는 정확도를 우선하고, 비용이 커지면 이후 SQL 레벨 청년 필터 이관을 검토하는 편이 안전함

## 36) 정확한 검색 totalCount 계산은 성능 비용이 숨기기 쉬움
- 문제: 정확한 `totalElements`를 위해 검색 결과를 배치 스캔하도록 바꾸면 기능은 맞아도, 배치 수와 후처리 비용이 로그에 드러나지 않으면 운영에서 느려진 시점을 놓치기 쉬움
- 해결: `PolicySearchService`에 검색 스캔 배치 수, 원본 검색 건수, 청년 필터 후 건수, 페이지 크기, 응답 시간을 남기는 관측 로그를 추가하고, 일정 임계치를 넘기면 `warn`으로 올리도록 변경
- 이유: 정확도 보강은 끝이 아니라 관측 가능성이 같이 있어야 유지된다. 성능 문제가 생겼을 때 SQL 레벨 청년 필터 이관이 필요한지 판단하려면 먼저 현재 비용이 얼마나 드는지 로그로 보이는 상태여야 함

## 37) 실제 넓은 검색어에서는 후처리 totalCount 계산 비용이 과도함
- 문제: 실제 Docker 앱에서 인증된 검색 요청으로 관측한 결과, `청년`, `지원`, `사업` 같은 넓은 단일 키워드는 6초~24초, 8~15배치 스캔, 원본 1.5k~2.8k건 조회까지 올라가 현재 Java 후처리 기반 total count 계산 비용이 너무 컸음
- 해결: 현재 구조는 정확도 유지용으로 두되, 다음 작업을 `정책 검색 SQL 레벨 청년 필터 설계 및 이관`으로 승격하고 운영 로그 기준으로 병목 원인을 문서화
- 이유: 이 정도 응답 시간은 검색 UX에 직접 영향을 준다. 관측 결과가 이미 충분히 나왔으므로, 이제는 “더 지켜본다”보다 필터를 SQL 또는 검색 전용 컬럼으로 이관하는 설계 작업으로 넘어가는 편이 맞음

## 38) 데모 문서의 정책 조회 예시는 현재 보안 설정과 불일치했음
- 문제: `demo-scenario.md`의 정책 목록/검색/랭킹/상세 예시는 인증 헤더 없이 호출하도록 되어 있었지만, 실제 `SecurityConfig`는 `/actuator/health` 외 대부분 GET 요청도 인증을 요구해 익명 요청 시 `403`이 발생했음
- 해결: 데모 문서의 정책 조회 예시에 `Authorization: Bearer ${ACCESS_TOKEN}`를 추가해 현재 보안 설정과 맞춤
- 이유: 검증 문서가 실제 보안 정책과 다르면 기능 자체는 정상이더라도 시연과 smoke test가 실패한 것으로 오해된다. 운영/검수 문서는 현재 보안 규칙과 반드시 같이 움직여야 함

## 39) 검색 totalCount 정확도를 Java 후처리에 맡기면 넓은 키워드에서 너무 느려짐
- 문제: `청년`, `지원`, `사업` 같은 넓은 검색어는 Java 후처리 청년 필터와 배치 스캔 기반 `totalElements` 계산 때문에 실제 앱에서 6초~24초까지 올라갔음
- 해결: `welfare_services.search_youth_relevant` 저장 플래그와 인덱스를 추가하고, 수집 저장/복지로 상세 fallback/관리자 백필에서 값을 갱신하도록 바꾼 뒤 검색 SQL과 count query가 이 플래그를 직접 사용하게 변경
- 이유: 요청마다 청년 관련성을 다시 계산하면 정확도는 맞아도 런타임 비용이 너무 크다. 검색용 파생 값은 저장 시점에 계산해 두고, 운영 DB에는 백필 절차를 같이 가져가야 응답 속도와 결과 일관성을 함께 지킬 수 있음

## 40) 지역 필터가 없는 일반 검색에서 `service_regions` 조인이 불필요한 병목이었음
- 문제: SQL 레벨 청년 필터 이관 뒤에도 일반 검색은 `LEFT JOIN service_regions + DISTINCT`를 항상 수행해 `+지원` 기준 중간 결과 72923행, 임시 테이블 dedup/sort 929ms가 발생했고 전체 API 응답이 1초대에 머물렀음
- 해결: 일반 검색은 `welfare_services`만 조회하는 쿼리로 분리하고, 지역 필터가 있을 때만 `NOT EXISTS/EXISTS` 기반 지역 판정 쿼리를 사용하도록 검색 repository와 service를 분기
- 이유: 지역 조건이 없는 요청에서 지역 테이블을 조인하면 결과 정확도 이득 없이 중복행과 정렬 비용만 커진다. 선택적 조인 또는 `EXISTS` 분기는 검색처럼 호출 빈도가 높은 쿼리에서 먼저 적용해야 함

## 41) `GET /api/recommendations` 응답에 `logId` 누락
- 문제: `POST /api/recommendations/refresh`는 응답에 `logId`를 포함했지만 `GET /api/recommendations`는 `RecommendationResponse.from(rec)`만 호출해 `logId=null`이 반환됐음. 프론트가 저장된 추천 목록을 조회할 때 CTR 클릭 추적이 불가능했음
- 해결: `getRecommendations()`에도 `recommendationLogService.findLatestLogIdMap()`로 serviceId → logId 맵을 조회해 응답에 포함하도록 수정
- 이유: refresh와 get 두 엔드포인트가 같은 데이터를 반환하므로 응답 계약이 동일해야 한다. 한쪽에만 필드가 있으면 프론트가 어느 엔드포인트를 쓰느냐에 따라 동작이 달라져 버그를 찾기 어렵다

## 42) CTR 클릭 추적 파라미터명 혼동 (`log_id` vs `logId`)
- 문제: 데모 시나리오에서 `?log_id=153`으로 테스트했지만 백엔드 `@RequestParam`은 `logId`(camelCase)를 기대해 클릭이 기록되지 않았음. 프론트는 axios params로 `{ logId: ... }`를 넘겨 `?logId=`로 변환하므로 실제 동작은 정상이었으나, 직접 curl 테스트에서 혼동이 발생했음
- 해결: 데모 시나리오 curl 예시를 `?logId=`로 수정하고, 프론트-백엔드 연동 흐름(URL `log_id` → searchParams.get → axios params `logId` → 백엔드 `@RequestParam logId`)을 문서화
- 이유: 파라미터명 규칙(camelCase vs snake_case)을 컨트롤러-문서-프론트 세 곳에서 일관되게 유지해야 한다. 직접 curl 테스트와 프론트 axios 동작이 다를 수 있으므로 데모 문서는 프론트 기준이 아닌 백엔드 계약 기준으로 작성해야 함

## 43) 추천 AI 점수 누락 — 20건 중 8건만 응답
- 문제: GPT에 20건을 한 번에 보냈을 때 "모두 평가하라"는 지시가 없어 GPT가 자체 판단으로 일부만 응답함. 20건 중 8건만 AI 점수를 받아 나머지 12건은 rule-only로 처리됐음
- 해결: system/user 역할 분리, 프롬프트에 "반드시 N개 전부 평가" 명시, TOP_N 20→15로 축소. 이후 15건 전부 응답 확인
- 이유: LLM은 명시적 지시가 없으면 긴 목록을 자의적으로 줄인다. 전체 평가를 보장하려면 개수를 줄이거나 프롬프트에 명시적 제약을 걸어야 한다

## 44) 추천 중복 행 누적 — 북마크 정책이 refresh마다 쌓임
- 문제: `deleteUnbookmarkedByUserId`는 북마크된 행을 남겨두는데, 북마크된 정책도 새 추천에 포함되면 같은 service_id 행이 중복으로 쌓였음. 테스트 유저 기준 total=43, unique=40으로 3건 중복 확인
- 해결: `deleteAllByUserId`로 전체 삭제 후 북마크 상태를 Map으로 보존해 새 행에 이전하도록 변경
- 이유: 북마크 보존은 "데이터를 남기는 것"이 아니라 "상태를 이전하는 것"이어야 한다. 행을 남기면 같은 정책이 여러 recommended_at으로 중복 존재하게 된다

## 45) 특수 대상 노이즈 필터가 숫자 임계값에 의존해 취약했음
- 문제: `rule_base_score > 8.0` 조건으로 병역/농촌/다문화 정책을 제거했지만, 이는 점수 공식 변경 시 자동으로 깨지는 구조였음. 또한 "현역병", "병역" 신호가 `hasSpecialTargetSignal()` 목록에 없어 필터를 우회했음
- 해결: `ScoredCandidate`에 `hasSpecialTargetMismatch` 플래그 추가, `RuleScoringService`가 계산 시점에 명시적으로 설정, Facade에서 플래그로 필터링. 특수 신호 목록에 "현역병", "병역" 추가
- 이유: 도메인 의미(특수 대상 불일치)는 점수 계산 로직이 가장 잘 알고 있다. 숫자 임계값 대신 의미 기반 플래그로 표현해야 점수 공식이 바뀌어도 필터가 유지된다

## 42) priority_options 코드가 추천 로직과 UI 코드 사이에서 따로 놀았음
- 문제: DB `priority_options`에는 `ONLINE`, `YOUTH_ONLY`, `EDU_JOB`, `AMOUNT` 코드가 있었지만, `DefaultPriorityMatcher`에서 `ONLINE`과 `YOUTH_ONLY`는 이미 항상 false였고, 프론트는 `JOB`, `EDUCATION`, `FINANCE`, `HEALTH`, `SAFETY` 등 DB에 없는 코드를 전송해 `C001` 오류가 났음
- 해결: `ONLINE`, `YOUTH_ONLY` 제거, `EDU_JOB`→`EDUCATION`, `AMOUNT`→`FINANCE` 코드 변경, `JOB`, `PARTICIPATION`, `FAMILY` 추가. DB migration, `DefaultPriorityMatcher`, 프론트 `PRIORITY_OPTIONS` 세 곳을 동시에 맞춤
- 이유: 우선순위 코드는 DB(마스터), 추천 로직(매처), UI(선택지) 세 곳이 항상 같아야 한다. 한 곳만 바꾸면 나머지가 다시 어긋난다

## 43) `deleteByUserId` 파생 쿼리가 같은 트랜잭션 내 INSERT 전 flush를 보장하지 않음
- 문제: `updatePriorities` 내에서 `userPriorityRepository.deleteByUserId()` 후 새 우선순위를 `save()`할 때, JPA가 DELETE를 DB에 즉시 반영하지 않아 `(user_id, priority_option_id)` 유니크 키 충돌이 발생했음
- 해결: `deleteByUserId`를 `@Modifying` + `@Query` JPQL로 교체해 DELETE를 즉시 DB에 반영되도록 변경
- 이유: Spring Data 파생 `deleteBy` 메서드는 내부적으로 엔티티를 조회 후 삭제하므로 flush 시점이 트랜잭션 끝까지 미뤄질 수 있다. DELETE 후 같은 유니크 컬럼에 INSERT가 오는 경우엔 `@Modifying` JPQL로 즉시 반영해야 한다

## 41) 회원가입/로그인 화면이 실제 인증 계약과 어긋나 있었음
- 문제: 프론트는 `signup`이 토큰과 user를 바로 반환한다고 가정했고, 이메일 중복확인은 미구현 API를 임시 우회하고 있었으며, 우선순위 저장 payload도 백엔드 계약과 달랐음
- 해결: `GET /api/auth/check-email`을 추가하고, 프론트는 `signup -> login -> /api/users/me` 순서로 세션을 만들도록 수정했으며, 우선순위 저장은 `{ priorityCodes: [...] }`로 맞춤. 동시에 "아이디 = 이메일", "비밀번호 재설정 메일은 후속 구현" 방향을 문서화
- 이유: 인증 화면은 응답 포맷 하나만 어긋나도 전체 가입 흐름이 무너진다. 특히 계정 복구처럼 보안 민감한 기능은 구현보다 먼저 정책과 계약을 명확히 고정해야 재작업이 줄어든다

## 46) 챗봇 일정 표기가 프론트와 문서에서 불일치
- 문제: `frontend/src/router/index.jsx`의 `/chat` 자리표시자는 `Phase 4 예정`이었지만, SRS와 `phase-plan.md`는 챗봇을 2차 작업으로 정의하고 있었음
- 해결: `/chat` 자리표시자를 `챗봇 (2차 예정)`으로 수정하고, `docs/chatbot-plan.md`에 현재 기준 설계/작업 순서를 문서화
- 이유: 구현 전 단계 기능일수록 화면 문구와 계획 문서가 같아야 우선순위 오해와 작업 누락이 줄어든다

## 47) 신규 문서가 목차와 작업 추적에서 빠져 상태 파악이 어긋남
- 문제: 운영/설계 보조 문서가 추가돼도 `docs/README.md`와 `docs/phase-plan.md` 작업 추적에 함께 반영되지 않아, 문서는 존재하지만 현재 상태와 후속 작업 목록에서 누락될 수 있었음
- 해결: `docs/README.md`에 신규 문서 링크를 추가하고, `docs/phase-plan.md`에서 완료/진행 예정/남은 작업을 실제 문서 상태와 다시 맞춤
- 이유: 이 저장소는 문서가 구현 계획과 운영 기준의 단일 진입점 역할을 하므로, 새 문서는 본문 작성만으로 끝내지 말고 목차와 추적 문서까지 함께 갱신해야 재발을 막을 수 있음

## 48) read cut-over 시 `user_attributes` / `user_priorities.user_key` 누락으로 프로필·추천 데이터가 비어 보일 수 있었음
- 문제: 프로필/추천 read path를 `user_profiles` 중심으로 바꾸는 과정에서, 보조 테이블 `user_attributes`, `user_priorities`의 기존 row 상당수가 아직 `user_key` 없이 `user_id`만 채워진 상태라 `where user_key = ?` 조회만 쓰면 관심분야/우선순위가 통째로 누락될 수 있었음
- 해결: 이번 전환에서는 read query를 `user_id -> users.user_key` 조인 기준으로 바꿔 기존 데이터를 안전하게 읽도록 고정했고, 후속 작업으로 `user_key` write sync 및 backfill을 별도 항목으로 추가
- 이유: cut-over 단계에서는 "새 키 기준 이상형"보다 "기존 운영 데이터가 빠짐없이 읽히는 것"이 우선이다. 보조 테이블 backfill 전까지는 조인 보정이 있어야 추천/프로필 품질 저하를 막을 수 있음

## 49) migration backfill만 하고 저장 경로를 안 바꾸면 새 `user_attributes` / `user_priorities` row가 다시 `user_key=NULL` 로 쌓였을 수 있음
- 문제: `V2026_04_27_02__add_user_key_columns.sql` 로 기존 row는 한 번 채울 수 있어도, JPA 저장 경로가 계속 `user_id`만 쓰면 이후 프로필 수정/우선순위 저장 때 새 row는 다시 `user_key` 없이 적재되어 read cut-over 이후 같은 문제가 반복될 수 있었음
- 해결: `UserAttribute`, `UserPriority` 엔티티에 `userKey` 컬럼을 매핑하고 `UserService.updateProfile`, `UserService.updatePriorities` 가 저장 시 `user_key` 를 함께 쓰도록 변경했으며, 남아 있는 null row는 `/api/admin/users/metadata-user-key-backfill` 로 마무리하는 경로를 추가
- 이유: cut-over 안정성은 "과거 데이터 1회 backfill"과 "미래 데이터 지속 동기화"가 둘 다 있어야 생긴다. 저장 경로를 같이 바꾸지 않으면 backfill은 일회성 복구에 그친다

## 48) 관리자 이메일 allowlist만 두면 공개 signup으로 권한 선점이 가능했음
- 문제: 이메일 인증이 없는 현재 구조에서 `SECURITY_ADMIN_EMAILS`만 두고 `/api/admin/**` 권한을 주면, 해당 이메일 주소가 DB에 없을 때 누구나 공개 회원가입으로 관리자 계정을 선점할 수 있었음
- 해결: 관리자 예약 이메일은 `signup`에서 차단하고, 운영자가 DB에 수동 생성한 계정만 관리자 권한을 얻도록 변경
- 이유: 관리자 권한은 "누가 그 문자열 이메일을 먼저 입력했는가"가 아니라 운영자가 통제한 계정 생성 절차에 의해 부여돼야 한다

## 49) refresh 재발급 시 역할 claim을 다시 넣지 않으면 관리자 권한이 사라짐
- 문제: access token에만 관리자 역할을 넣고 `refresh`에서는 userId만으로 새 access token을 만들면, 관리자도 재발급 직후 `ROLE_ADMIN` 없이 일반 사용자처럼 떨어질 수 있었음
- 해결: `refresh` 시 DB의 사용자 이메일과 `SECURITY_ADMIN_EMAILS`를 다시 확인해 새 access token에 역할 claim을 재주입하도록 변경
- 이유: 권한 정보는 최초 로그인 한 번이 아니라 access token을 새로 만들 때마다 같은 기준으로 재계산돼야 일관성이 유지된다

## 50) 새 챗 세션의 `last_message_at`가 NULL이면 최근 세션 정렬이 흔들릴 수 있음
- 문제: 챗봇 세션은 생성 직후 메시지가 없을 수 있는데 `last_message_at`를 nullable로 두면 세션 목록 최신순 정렬에서 DB별 NULL 정렬 차이 때문에 방금 만든 세션이 뒤로 밀릴 수 있었음
- 해결: `chat_sessions.last_message_at`를 `NOT NULL DEFAULT CURRENT_TIMESTAMP`로 설계하고 `(user_id, last_message_at DESC)` 인덱스를 함께 추가
- 이유: 세션 생성 직후에도 "최근 대화" 목록의 기준시각이 필요하다. 정렬 기준 컬럼은 가능한 NULL을 피해야 목록 UX와 쿼리 계획이 안정적이다

## 51) MySQL JSON 컬럼은 문자열 포맷을 그대로 보존하지 않을 수 있음
- 문제: `chat_messages.referenced_service_ids`를 JSON 컬럼으로 저장하면 DB가 공백/표현을 정규화할 수 있어, 테스트에서 raw 문자열을 완전히 동일 비교하면 실패할 수 있었음
- 해결: 통합 테스트는 JSON 문자열 전체 포맷이 아니라 포함된 서비스 ID와 빈 배열 여부처럼 의미 기준으로 검증하도록 변경
- 이유: JSON 컬럼 검증은 직렬화 포맷이 아니라 의미값 기준으로 해야 DB 엔진별 정규화 차이에도 안정적이다

## 52) 제목 optional 세션 생성인데 request body를 필수로 두면 첫 진입 흐름이 400으로 막힘
- 문제: `POST /api/chat/sessions`는 `title`이 optional인데 컨트롤러에서 request body 자체를 필수로 받으면, 프론트가 제목 없이 새 세션을 만들 때 빈 body 요청이 400으로 실패할 수 있었음
- 해결: `@RequestBody(required = false)`로 받고 서비스에서 제목을 `trim -> blank면 null`로 정규화하도록 변경
- 이유: "새 대화 시작"은 최소 입력 없이 바로 열려야 한다. optional 필드는 body optional 처리까지 같이 가야 실제 UX와 API 계약이 맞는다

## 53) 챗 세션 권한 오류를 구분해 응답하면 다른 사용자의 세션 존재를 추측할 수 있음
- 문제: 메시지 목록 조회에서 "없는 세션"과 "남의 세션"을 다른 오류로 나누면, 응답 차이만으로 다른 사용자의 세션 ID 존재 여부를 유추할 수 있었음
- 해결: `GET /api/chat/sessions/{sessionId}/messages`도 삭제 API와 동일하게 `findByIdAndUserId`를 사용하고, 존재하지 않거나 소유하지 않은 경우 모두 `CH001` 404로 통일
- 이유: 본인 리소스만 다루는 API는 권한 실패와 미존재를 같은 외부 응답으로 감싸는 편이 안전하다. 내부 구현보다 리소스 존재 노출 최소화가 우선이다

## 54) 챗봇 질문 원문을 그대로 Boolean FULLTEXT에 넣으면 기호성 입력에서 검색어 품질이 흔들릴 수 있음
- 문제: 챗봇 질문에는 `?`, `!`, `/`, 조사 섞인 짧은 문장처럼 FULLTEXT Boolean Mode에 그대로 넣기 애매한 토큰이 자주 들어오는데, 이를 정리하지 않으면 후보 조회가 불안정하거나 의미 없는 검색으로 흘러갈 수 있었음
- 해결: `ChatPolicyService`에서 `[0-9A-Za-z가-힣]+` 토큰만 추려 `+token` 검색어를 만들고, 검색 가능한 토큰이 하나도 없으면 즉시 인기 청년 정책 fallback 조회로 넘기도록 고정
- 이유: 챗봇 입력은 검색창보다 노이즈가 많다. 질문 원문을 그대로 SQL 검색어로 쓰지 말고, 최소한의 토큰 정규화와 fallback 기준을 먼저 고정해야 이후 LLM 연결 시에도 후보 품질이 흔들리지 않는다

## 55) 제목 없는 세션에 첫 메시지까지 지나가면 최근 대화 목록에서 구분이 어려워짐
- 문제: `POST /api/chat/sessions`는 제목 없이 세션을 만들 수 있는데, 이후 메시지 전송 단계에서도 제목을 계속 비워 두면 최근 대화 목록이 여러 개의 빈 제목 세션으로 쌓여 사용자가 구분하기 어려워질 수 있었음
- 해결: `POST /api/chat/sessions/{sessionId}/messages`에서 세션 제목이 비어 있으면 첫 사용자 질문 앞부분을 제목으로 자동 채우도록 변경
- 이유: 새 대화 시작은 무입력으로 열 수 있어야 하지만, 첫 질문 이후에는 최소한의 식별 제목이 있어야 세션 목록 UX가 유지된다

## 56) 통합 테스트용 정책 fixture도 운영 스키마 제약을 그대로 맞춰야 함
- 문제: 챗 메시지 전송 통합 테스트에서 임시 `welfare_services` 데이터를 넣을 때 `api_view_count NOT NULL`, `source_id VARCHAR(50)` 제약을 놓치면, 기능 코드와 무관하게 저장 단계에서 테스트가 깨질 수 있었음
- 해결: 테스트 fixture에 `apiViewCount=0`을 명시하고, `source_id`는 별도 짧은 prefix + 축약 UUID로 생성하도록 수정
- 이유: 검색/추천/챗봇 테스트는 실제 정책 테이블을 공유한다. 임시 row라도 운영 스키마와 동일한 제약을 만족하도록 만들지 않으면 이후 다른 테스트에서도 같은 종류의 실패가 반복된다

## 57) LLM이 후보 밖의 정책 ID를 만들어내면 잘못된 상세 링크와 환각 응답이 생길 수 있음
- 문제: 챗봇이 OpenAI 응답의 `service_id`를 그대로 신뢰하면, 모델이 후보 목록에 없는 정책 ID를 만들어낼 때 프론트 상세 링크와 근거 정책 카드가 잘못 연결될 수 있었음
- 해결: `ChatAiGateway`에서 JSON 응답을 파싱한 뒤 서버가 전달한 후보 정책 allowlist와 대조해 일치하는 `service_id`만 참조 목록에 남기도록 검증
- 이유: LLM 응답은 텍스트 생성 결과이지 DB foreign key가 아니다. 근거 정책 연결은 모델이 아니라 서버가 최종 검증해야 보안과 정합성이 유지된다

## 58) 로그아웃은 토큰만 지우고 챗 히스토리를 남겨두면 재로그인 후 이전 대화가 그대로 노출될 수 있음
- 문제: 로그아웃/회원탈퇴 흐름이 refresh token만 정리하고 `chat_sessions`, `chat_messages`를 건드리지 않으면, 로그인 전용 챗봇 요구사항과 달리 재로그인 시 이전 세션이 그대로 남을 수 있었음
- 해결: `ChatSessionCleanupService`를 두고 `AuthService.logout*`, `UserService.withdraw`에서 공용으로 호출해 사용자별 세션을 삭제하고, FK cascade로 메시지까지 함께 정리하도록 고정
- 이유: 보안 기대치가 높은 개인화 상담 히스토리는 인증 종료 시점과 계정 삭제 시점에 같이 정리되어야 한다. 정리 로직을 서비스 하나로 모아야 logout/withdraw 경로가 늘어나도 누락이 줄어든다

## 59) rate limit를 메시지 저장 뒤에 걸면 차단된 요청도 USER 메시지나 세션 갱신이 남을 수 있음
- 문제: 챗봇 abuse 방지를 뒤늦게 적용하면, 상한을 넘긴 요청이 거절되더라도 이미 USER 메시지 저장이나 `last_message_at` 갱신이 일부 반영돼 세션 이력이 어그러질 수 있었음
- 해결: `ChatMessageService.sendMessage()`에서 활성 사용자 확인 직후 `ChatRateLimitService`를 먼저 호출하고, `CH002`가 나면 세션 조회와 메시지 저장을 시작하지 않도록 순서를 고정
- 이유: rate limit는 단순 에러 응답이 아니라 쓰기 트랜잭션 진입 자체를 막는 보호장치다. 차단 시점이 늦으면 보안뿐 아니라 데이터 정합성도 같이 흔들린다

## 60) 프론트 로그아웃이 로컬 토큰만 지우면 백엔드 logout 정리 로직이 실행되지 않음
- 문제: 프론트가 `localStorage`와 Zustand 상태만 비우고 `/api/auth/logout`을 호출하지 않으면, refresh cookie 무효화와 챗 세션 정리 같은 서버 종료 처리들이 실제 UI 로그아웃 경로에서는 빠질 수 있었음
- 해결: 공용 `performServerLogout()`를 두고 Header 로그아웃, 비밀번호 변경 후 재로그인, 회원탈퇴 후 종료 흐름에서 모두 서버 `/api/auth/logout`을 먼저 호출한 뒤 로컬 상태를 비우도록 통일
- 이유: 로그아웃은 단순 클라이언트 상태 초기화가 아니라 서버 세션 종료 절차다. 프론트와 백엔드가 다른 정의를 가지면 refresh 재발급, 챗 히스토리 정리, 보안 감사 포인트가 모두 어긋난다

## 61) 비밀번호 재설정 요청에서 "없는 이메일"을 에러로 돌리면 계정 존재 여부를 추측할 수 있음
- 문제: 비밀번호 재설정 요청 API가 가입된 이메일과 미가입 이메일에 서로 다른 응답을 주면, 공격자가 응답 차이만으로 계정 존재 여부를 수집할 수 있었음
- 해결: `POST /api/auth/password-reset/request`는 활성 사용자에게만 실제 메일을 보내되, 없는 이메일이나 탈퇴 계정이어도 항상 동일한 성공 응답을 반환하도록 고정
- 이유: 비밀번호 재설정은 대표적인 계정 열거 지점이다. 인증 전 단계에서는 "성공/실패"보다 계정 정보 비노출이 우선이다

## 62) `SECURITY_ADMIN_EMAILS`만 바꾸고 앱을 재기동하지 않으면 운영 admin 권한 반영이 누락될 수 있음
- 문제: 관리자 이메일 allowlist는 `AuthService` 시작 시 1회 로딩되므로, `.env`의 `SECURITY_ADMIN_EMAILS`만 수정하고 앱을 재기동하지 않으면 DB에 admin row를 넣어도 로그인/refresh 시 `ROLE_ADMIN`이 반영되지 않을 수 있었음
- 해결: 운영 admin 계정 런북에 "allowlist 변경 -> 앱 재기동 -> DB row 생성/검증" 순서를 고정하고, 배포 가이드에도 재기동 주의사항을 명시
- 이유: 이 문제는 DB 데이터 오류처럼 보이지만 실제 원인은 stale app config다. 운영 절차에 재기동 단계를 명시해야 같은 혼선을 반복하지 않는다

## 63) 지역 추천 후보를 `LEFT JOIN + DISTINCT`로 조회하면 중복 제거 임시 테이블 비용이 커질 수 있음
- 문제: `service_regions` row가 많은 상태에서 추천 지역 후보를 `LEFT JOIN service_regions` 후 `DISTINCT`로 정리하면, 매칭 여부 판정보다 dedup 임시 테이블 비용이 먼저 커져 실행 계획이 불필요하게 무거워질 수 있었음
- 해결: 추천 지역 후보 JPQL을 `NOT EXISTS/EXISTS` 기반으로 바꾸고, `service_regions(service_id, sido_name, sgg_name)` 및 `(service_id, region_code)` 복합 인덱스를 추가해 지역 매칭 판정을 join 결과 정리가 아닌 존재 여부 검사로 고정
- 이유: 이 도메인의 지역 판정은 "어떤 row가 붙었는가"가 아니라 "매칭 row가 존재하는가"가 핵심이다. 존재 여부 조건으로 표현해야 중복 결과와 임시 테이블 비용을 함께 줄일 수 있다

## 64) 새 인덱스를 추가해도 MySQL 통계가 stale 하면 기대한 실행 계획이 바로 나오지 않을 수 있음
- 문제: `service_regions` 복합 인덱스를 다시 적용한 뒤 EXPLAIN을 돌렸을 때, 인덱스가 실제로 존재해도 옵티마이저가 계속 기존 `idx_sr_service` 경로를 선택해 검증 결과가 흔들릴 수 있었음
- 해결: 마이그레이션 적용 직후 `SHOW INDEX`로 생성 여부를 확인하고, 이어서 `ANALYZE TABLE service_regions`까지 실행하도록 운영 절차와 migration 문서에 반영
- 이유: 인덱스 추가와 통계 갱신은 같은 성능 변경 절차로 봐야 한다. 생성만 확인하고 EXPLAIN을 바로 믿으면 환경마다 다른 플랜을 보고도 원인을 놓치기 쉽다

## 65) optional 지역 파라미터를 한 SQL에 섞으면 `sido+sgg` 경로의 복합 인덱스 선택이 흔들릴 수 있음
- 문제: 지역 검색을 `sr2.sido_name = :sido AND (:sgg IS NULL OR sr2.sgg_name = :sgg)` 한 쿼리로 처리하면, `sgg`가 실제로 주어진 요청에서도 옵티마이저가 `service_id+sido_name+sgg_name` 복합 인덱스를 안정적으로 고르지 못해 성능이 흔들릴 수 있었음
- 해결: 검색 쿼리를 `searchByKeywordWithFiltersWithSido` 와 `searchByKeywordWithFiltersWithSidoSgg` 두 개로 분리하고, 서비스에서 `sgg` 유무에 따라 분기하도록 변경
- 이유: optional 조건은 API 레벨에선 편하지만, SQL 레벨에선 실행 계획을 흐리게 만들 수 있다. 지역처럼 호출이 잦고 인덱스 선택이 중요한 조건은 optional 분기를 서비스에서 나누는 편이 안전하다

## 66) 추천 지역 후보에서 `regionCode OR sidoName` 을 한 EXISTS에 묶으면 프로필 조건과 맞는 인덱스를 직접 타기 어려울 수 있음
- 문제: 추천 후보 쿼리를 `sr2.region_code = :regionCode OR sr2.sido_name = :sidoName` 한 조건으로 유지하면, 사용자가 실제로는 `regionCode`를 가지고 있어도 옵티마이저가 `region_code` 복합 인덱스를 직접 타지 못하거나 불필요한 OR 평가를 같이 안고 갈 수 있었음
- 해결: 추천 후보 쿼리를 `findCandidatesWithRegionCode` / `findCandidatesWithSido` 와 최신순 대응 쿼리로 분리하고, `RetrievalService`에서 `regionCode` 유무에 따라 분기하도록 변경
- 이유: 추천은 사용자 프로필이 이미 정해진 상태에서 호출되므로 optional 분기를 SQL 안에 남길 이유가 적다. 서비스에서 분기하면 쿼리 의미가 단순해지고 인덱스 선택도 예측 가능해진다

## 67) CTR 로그가 적은 초기 구간에서는 AI 품질 결론보다 baseline 관측에 집중해야 함
- 문제: 현재 `recommendation_logs`는 `46건 / 클릭 1건 / fallback 16건 / 가중치 0.80:0.20 단일 구간 / 하루치 2명 사용자` 수준이라, 이 수치만으로 "AI가 rule보다 낫다"거나 프롬프트/가중치를 조정할 근거로 쓰면 오판할 수 있었음
- 해결: 이번 작업에서는 CTR 분석 쿼리 실행 결과를 baseline으로만 문서화하고, `CTR 표본 추가 확보 후 rule/AI 가중치 및 프롬프트 재분석`을 후속 작업으로 분리
- 이유: CTR은 로그가 존재하는 것만으로 충분하지 않고 표본 규모와 분포가 같이 필요하다. 특히 fallback 비교와 가중치 단계 비교는 다일자/다사용자 데이터가 쌓이기 전까지는 운영 판단보다 관측 지표로 다루는 편이 안전하다

## 68) `user_key` 선행 없이 바로 PII 분리 테이블로 갈아타면 `user_id` 연관 전반이 한 번에 깨질 수 있음
- 문제: 현재 구조는 `AuthService`, `UserService`, `NotificationService`, 추천/채팅/로그 테이블이 모두 `users.id` 와 `@ManyToOne User` 에 묶여 있어, `auth_users/user_profiles/user_pii` 를 먼저 만들더라도 공용 식별자 없이 곧바로 cut-over 하면 JWT, Redis key, FK, JPA 연관이 동시에 흔들릴 수 있었음
- 해결: PII 분리 이행안에서 `users.user_key` 및 하위 테이블 `user_key` backfill을 1단계 선행 작업으로 확정하고, 그 다음에 `dual-write -> read cut-over -> legacy 제거` 순서로 진행하도록 문서화
- 이유: 테이블 분리의 실제 리스크는 schema 생성보다 식별자 전환이다. 호환 기간 동안 `user_id` 와 `user_key` 를 함께 유지해야 로그/추천/알림/챗 세션 같은 주변 테이블을 안전하게 단계별로 넘길 수 있다

## 69) `user_key` migration은 기존 row backfill만 하고 끝내면 다음 가입자부터 다시 NULL 이 생길 수 있음
- 문제: `users.user_key` 를 nullable로 추가하고 기존 사용자만 UPDATE 하면, dual-write 릴리스 전까지 새 회원가입 row는 다시 `user_key=NULL` 로 들어가 이후 하위 테이블 backfill과 cut-over 기준이 흔들릴 수 있었음
- 해결: `users.user_key` 를 `CHAR(32) NOT NULL DEFAULT (REPLACE(UUID(), '-', ''))` 로 추가하고, 기존 row는 1회 backfill 후 unique key를 걸도록 migration과 schema를 작성
- 이유: 식별자 선행 단계는 "과거 데이터 정리"와 "이행 기간 신규 데이터 보호"를 같이 해야 의미가 있다. 새 가입자가 다시 NULL 상태로 들어오면 단계적 전환의 전제가 바로 깨진다

## 70) 기존 행 `UUID()` 대량 UPDATE는 MySQL binlog safety에 걸릴 수 있음
- 문제: `UPDATE users SET user_key = REPLACE(UUID(), '-', '')` 형태로 기존 사용자를 backfill 하면, MySQL이 "replica에서 값이 달라질 수 있는 시스템 함수"로 보고 실행을 막거나 경고할 수 있었음
- 해결: 기존 사용자 backfill은 `SUBSTRING(SHA2(CONCAT('user:', id), 256), 1, 32)` 같은 deterministic 식으로 바꾸고, 새 가입자 자동 채움은 `DEFAULT (REPLACE(UUID(), '-', ''))` 를 유지하되 migration 세션 시작 시 `SET SESSION sql_log_bin = 0`을 실행하도록 수정
- 이유: migration의 목적은 호환 식별자를 안정적으로 심는 것이다. 과거 데이터는 결정적 값으로 채우고, 신규 데이터는 insert-time default를 쓰되, 수동 마이그레이션 세션에서만 binlog safety 제약을 우회하는 편이 현재 운영 방식과 가장 잘 맞는다

## 71) `user_pii` 초기 backfill에서 이메일/이름/생년월일을 SQL로 바로 암호화하려 들면 앱 포맷과 어긋날 수 있음
- 문제: 현재 애플리케이션의 PII 암호화는 Java `AesEncryptUtil` 포맷을 기준으로 동작하는데, migration SQL만으로 `email/name/birth_date`를 같은 포맷으로 안전하게 변환하려 하면 향후 복호화 호환성이나 키 관리 기준이 어긋날 수 있었음
- 해결: `V2026_04_27_03__add_user_core_split_tables.sql`에서는 `user_pii` 테이블과 `phone_enc` seed만 먼저 만들고, `email_enc/name_enc/birth_date_enc` backfill은 후속 dual-write 릴리스에서 앱 레벨 암호화로 채우도록 분리
- 이유: 스키마 준비와 암호화 포맷 전환은 분리하는 편이 안전하다. 특히 이미 서비스 코드가 가진 암호화 규칙이 있을 때는 SQL 편의 변환보다 애플리케이션 동일 경로를 재사용하는 것이 재현성과 복구 가능성 면에서 낫다

## 72) MySQL cross-schema 테이블은 JPA에서 `schema`보다 `catalog` 매핑이 더 직접 맞을 수 있음
- 문제: `youth_welfare_pii.user_pii` 테이블이 실제로 존재해도, `@Table(schema = "youth_welfare_pii")` 로만 매핑하면 Hibernate validate 단계에서 missing table로 판단할 수 있었음
- 해결: `UserPii` 엔티티를 `@Table(name = "user_pii", catalog = "youth_welfare_pii")` 로 바꾸고 통합 테스트로 검증
- 이유: MySQL에서 database와 schema 개념이 사실상 catalog로 취급되는 경로가 있다. cross-schema entity는 DB 엔진 용어에 맞춰 catalog 매핑을 우선 확인하는 편이 안전하다

## 73) `auth_users` 읽기 전환을 조회만 바꾸고 상태 동기화를 빼면 계정 잠금 기준이 stale 해질 수 있음
- 문제: 로그인/비밀번호 재설정 조회를 `auth_users.email_lookup_hash` 기준으로 바꾸기만 하고, 로그인 실패 횟수와 `locked_until` 갱신은 계속 legacy `users` 에만 남겨두면 다음 로그인부터 `auth_users` 의 잠금 상태가 stale 해져 잘못된 허용/차단이 발생할 수 있었음
- 해결: 로그인 성공/실패 후에는 legacy `users` 를 먼저 갱신하고, 즉시 `UserCoreSyncService.syncFromUser` 를 호출해 `auth_users` 의 `password_hash`, `login_fail_count`, `locked_until`, `is_active` 를 다시 맞추도록 고정
- 이유: dual-write 단계의 read cut-over 는 "어느 테이블에서 읽느냐"만이 아니라 "그 테이블이 언제 최신 상태로 유지되느냐"까지 포함한다. 인증처럼 상태 기반 분기가 있는 경로는 읽기 전환과 상태 재동기화를 한 작업으로 묶어야 재발을 막을 수 있다

## 74) 앱 레벨 PII backfill도 원본 `users` 값이 이미 비어 있으면 복구할 수 없음
- 문제: `user_pii.email_enc/name_enc/birth_date_enc` 를 앱 레벨 암호화로 채우더라도, 일부 row는 source of truth 인 `users.name` 또는 `users.birth_date` 가 이미 `NULL` 이어서 암호문을 다시 만들 수 없었음
- 해결: 백필 서비스는 누락 암호문만 채우되, 원본 값이 없는 row는 overwrite 시도 없이 `skippedCount` 로 남기고 검증 결과에 별도로 기록
- 이유: 이 단계의 backfill은 기존 원문이 남아 있는 범위만 안전하게 옮기는 작업이다. 이미 scrub 된 값까지 복원하려고 들면 잘못된 placeholder 데이터를 넣거나 탈퇴/테스트 fixture 상태를 오염시킬 수 있다

## 75) 비밀번호 재설정 메일 발송이 계속 `users.email` 을 읽으면 PII read path 전환 후에도 legacy 의존이 남음
- 문제: 로그인/비밀번호 재설정 조회는 이미 `auth_users` 기준으로 넘어갔는데, reset 메일 발송 주소만 계속 `users.email` 을 사용하면 `user_pii.email_enc` backfill 이후에도 인증 경로가 legacy PII 컬럼에 의존하게 되어 read cut-over 경계가 흐려질 수 있었음
- 해결: `AuthService.requestPasswordReset` 이 `auth_users -> user_key -> user_pii.email_enc` 경로에서 발송 주소를 읽고, `AesEncryptUtil` 로 복호화한 값으로 메일을 보내도록 변경
- 이유: PII 분리 단계에서는 "누가 사용자 식별을 담당하는가"와 "어디서 원문 연락처를 읽는가"를 같이 끊어야 한다. 비밀번호 재설정은 인증 전 기능이지만 실제 발송 주소는 PII 저장소에서만 읽도록 고정해야 이후 `users.email` 제거가 가능하다

## 76) `user_key` 컬럼은 migration만 `CHAR(32)` 로 맞추고 JPA 매핑을 기본값으로 두면 통합 테스트 부팅에서 바로 깨질 수 있음
- 문제: `chat_sessions`, `notifications`, `recommendation_logs`, `service_view_logs`, `users` 의 `user_key` 컬럼은 migration에서 `CHAR(32)` 로 생성됐는데, 엔티티 매핑을 `length = 32` 만 두면 Hibernate validate 가 `VARCHAR(32)` 로 기대해 애플리케이션 부팅이 실패할 수 있었음
- 해결: 새로 매핑한 `user_key` 필드 전부에 `columnDefinition = "CHAR(32)"` 를 명시하고 `./gradlew integrationTest --no-daemon` 로 schema validate 까지 확인
- 이유: `user_key` 는 앞으로 공용 식별자이기 때문에 한 테이블만 타입이 어긋나도 배포 즉시 부팅 실패로 이어진다. migration과 JPA 매핑을 항상 한 쌍으로 맞춰야 재발을 막을 수 있다

## 77) split-table 도입 뒤 테스트나 수동 정리에서 `users` 만 지우면 `auth_users/user_profiles/user_pii` orphan 이 남아 중복 충돌을 만들 수 있음
- 문제: `users` row 만 삭제하고 split table row 를 그대로 두면, 같은 이메일로 다시 fixture 를 만들 때 `auth_users.email_lookup_hash` unique 충돌이 발생할 수 있었음
- 해결: 고정 이메일 fixture 를 쓰는 `AdminSecurityIntegrationTest` 에서 `UserCoreSyncService` 기반 생성과 `auth_users/user_profiles/user_pii` 동시 정리 루틴을 추가
- 이유: dual-write 단계에서는 legacy 테이블 하나만 source of truth 라고 가정하면 안 된다. 테스트 fixture, 수동 운영 스크립트, admin bootstrap 모두 split table 동시 정리 기준을 따라야 재시도 가능성이 유지된다

## 78) `user_key` 기준 소유권/cleanup 으로 넘어간 뒤 직접 insert fixture 가 `user_key` 를 빼먹으면 새 경로 검증이 어긋날 수 있음
- 문제: `chat_sessions`, `notifications`, `recommendation_logs` 를 `user_key` 기준으로 조회/cleanup 하도록 바꾼 뒤에도 테스트나 수동 SQL fixture 가 `user_id` 만 채우고 `user_key` 를 비워두면, 로그아웃 cleanup, 채팅 소유권 검증, 알림 retry 가 실제 런타임 경로와 다르게 동작할 수 있었음
- 해결: 관련 integration/unit test 의 직접 insert fixture 를 `userId + userKey` 동시 기록 기준으로 정리하고, 새 코드에서는 `user_key` 누락 시 fallback 하지 않도록 고정
- 이유: cut-over 단계에서 가장 위험한 상태는 "코드는 새 식별자를 쓰는데 fixture 만 옛 식별자를 쓰는 경우"다. 테스트 데이터도 운영 경로와 같은 식별자 계약을 강제해야 재발을 막을 수 있다

## 79) legacy refresh token fallback 제거는 배포 시점에 구형 토큰을 즉시 무효화한다
- 문제: refresh token subject 숫자 fallback 과 `refresh:<userId>` Redis key fallback 을 제거하면, cut-over 이전 형식으로 발급된 refresh token 은 재발급에 실패하게 된다
- 해결: 현재 코드는 `user_key` subject 와 `refresh:<userKey>` 저장분만 허용하도록 정리했고, full 검증은 `./gradlew test --no-daemon`, `./gradlew integrationTest --no-daemon` 로 확인
- 이유: fallback 을 오래 끌수록 `user_id` 제거가 다시 어려워진다. 이 단계에서는 호환성보다 식별자 계약 단일화가 더 중요하고, 구형 refresh token 은 재로그인으로 회복 가능하므로 의도적 정리로 보는 편이 맞다

## 80) `@WebMvcTest(addFilters = false)` 슬라이스에서는 custom principal 로 바꿔도 `@AuthenticationPrincipal` 이 계속 `null` 일 수 있음
- 문제: JWT filter 에서 `AuthenticatedUser` principal 을 심도록 바꾼 뒤에도, 기존 `@WebMvcTest(addFilters = false)` 슬라이스 테스트는 Security filter chain 을 타지 않아 `@AuthenticationPrincipal` 이 계속 `null` 로 들어와 controller NPE 가 발생할 수 있었음
- 해결: controller 쪽 principal 해석을 `null` 안전하게 정리하고, WebMvc 슬라이스 테스트는 기존처럼 `null` principal 기준 mock 기대값을 유지한 채 별도로 filter/security 경로는 `AdminSecurityWebMvcTest` 와 integration test 로 검증
- 이유: principal 객체 전환 자체와 Spring MVC 슬라이스에서 보안 resolver 가 실제 runtime 과 다르게 동작하는 문제를 분리해야 한다. controller 를 null-safe 하게 두고, 인증 해석 검증은 filter 를 실제로 태우는 테스트에서 확인하는 편이 재발 방지에 더 안정적이다

## 81) `users.user_key` 가 DB default 로 채워지는 구조에서는 freshly saved `User` 엔티티가 즉시 `userKey` 를 들고 있다고 가정하면 안 됨
- 문제: `users.user_key` 는 DB default 와 `insertable = false, updatable = false` 매핑을 쓰기 때문에, 저장 직후 in-memory `User` 엔티티의 `getUserKey()` 는 아직 `null` 일 수 있었음. 이 상태에서 `user_recommendations` placeholder 나 북마크 fixture 가 엔티티 필드만 믿고 `userKey` 를 기록하면 누락 row 가 생길 수 있었음
- 해결: 추천/북마크 경로와 integration test fixture 는 `userRepository.findUserKeyById()` 로 다시 읽은 값만 사용해 `user_recommendations.user_key` 를 채우도록 고정
- 이유: DB default 기반 식별자는 "저장 성공"과 "엔티티 메모리 값 가시화"가 같은 시점이 아니다. cut-over 단계에서는 식별자 컬럼을 직접 참조하는 write path와 fixture가 항상 persisted 값을 다시 읽는 편이 안전하다

## 82) read path가 `user_key` 로 바뀌었다고 바로 모든 `user_id` 컬럼을 drop 하면 쓰기 경로가 깨질 수 있음
- 문제: `user_recommendations`, `chat_sessions`, `notifications` 같은 runtime 테이블은 이미 `user_key` 기준 read/write 로 넘어갔지만, `user_attributes` 와 `user_priorities` 는 아직 `deleteByUserId`, `findByUserId*`, `@ManyToOne User` 저장 경로가 남아 있어 모든 `user_id` 컬럼/FK를 한 번에 제거하면 프로필 수정, 우선순위 저장, 회원탈퇴 정리 로직이 바로 깨질 수 있었음
- 해결: legacy `user_id` drop 설계를 2단계로 분리해, 먼저 `user_attributes` / `user_priorities` 의 write/delete 경로를 `user_key` 기준으로 바꾸고 그 다음에 runtime 테이블의 `user_id` 인덱스/FK/컬럼 제거 SQL을 적용하도록 문서화
- 이유: read cut-over와 write cut-over는 같은 완료 조건이 아니다. 컬럼 제거는 "최신 저장/삭제 경로까지 새 식별자를 쓴다"는 것이 확인된 뒤에만 안전하다

## 83) `@Modifying` delete query 는 서비스 트랜잭션 밖에서 바로 호출하면 테스트 cleanup 에서 터질 수 있음
- 문제: `user_attributes`, `user_priorities` 정리 경로를 `deleteByUserKey` `@Modifying` JPQL로 바꾼 뒤, 통합 테스트 `@AfterEach` cleanup 에서 이를 직접 호출하자 `TransactionRequiredException` 이 발생했음
- 해결: 서비스 본문은 기존처럼 `@Transactional` 경계 안에서 `deleteByUserKey` 를 사용하고, 테스트 cleanup 은 `findByUserKey* -> deleteAll(...)` 경로로 분리해 정리
- 이유: 즉시 DELETE 보장은 쓰기 트랜잭션 안에서는 필요하지만, 테스트 정리 코드는 같은 제약을 공유하지 않는다. 운영 경로와 cleanup 경로를 같은 메서드로 억지로 맞추기보다 각 경계에 맞는 삭제 방식을 쓰는 편이 안전하다

## 84) runtime 테이블에서 `user_id` FK를 제거하면 테스트의 `userRepository.delete()` 정리 가정이 깨질 수 있음
- 문제: `chat_sessions`, `user_recommendations`, `recommendation_logs`, `notifications` 의 `user_id` FK를 제거한 뒤에도 기존 integration test cleanup 이 `userRepository.delete(user)` 만 호출하면, 런타임 row가 그대로 남아 다음 테스트의 서비스 삭제나 상태 검증을 오염시킬 수 있었음
- 해결: chat/recommendation 관련 integration test cleanup 을 `user_key` 기준 자식 row 정리 후 사용자 삭제 순서로 바꾸고, `ChatSessionRepository.findAllByUserKey`, `UserRecommendationRepository.findByUserKey`, `RecommendationLogRepository.findByUserKey` 같은 cleanup용 조회를 추가
- 이유: legacy FK 제거 이후의 정리 기준은 DB cascade가 아니라 애플리케이션 식별자 계약이다. 테스트도 운영과 같은 `user_key` 기반 정리 순서를 따라야 새 스키마에서 안정적으로 반복 실행된다

## 85) Compose에서 앱 DB 계정만 `root`에서 바꾸고 MySQL 초기화 grant를 같이 안 넣으면 신규 환경이 바로 부팅 실패할 수 있음
- 문제: `docker-compose.yml`과 `.env`에서 앱 datasource 계정을 `app_core_rw`로 바꾸기만 하면, 새 Docker 볼륨이나 새 운영 DB에는 해당 계정 자체가 없어 앱이 `Access denied`로 시작조차 못 할 수 있었음
- 해결: `deploy/mysql/init/z90-create-runtime-db-users.sh`를 추가해 신규 DB 초기화 시 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정과 최소 grant를 함께 생성하고, 기존 DB는 수동 전환이 필요하다는 기준을 배포/마이그레이션 문서에 같이 남겼음
- 이유: 런타임 `root` 제거는 애플리케이션 설정 변경만으로 끝나지 않는다. 계정 생성, 권한 부여, 앱 env 전환, 기존 DB 예외 처리를 한 묶음으로 다뤄야 같은 실수가 반복되지 않는다

## 86) MySQL `docker-entrypoint-initdb.d` 의 `.sh`는 실행 권한이 없으면 `source` 되어 shell option 이 상위 엔트리포인트에 새어 나갈 수 있음
- 문제: 초기화 스크립트에 `set -euo pipefail` 을 넣은 상태로 파일이 non-executable 이면 MySQL entrypoint 가 이 파일을 `source` 해서 읽고, `set -u` 가 상위 엔트리포인트까지 남아 `MYSQL_ONETIME_PASSWORD: unbound variable` 로 init 전체가 깨질 수 있었음
- 해결: `deploy/mysql/init/z90-create-runtime-db-users.sh` 를 executable 로 두고, smoke test 에서 entrypoint 로그가 `running /docker-entrypoint-initdb.d/z90-create-runtime-db-users.sh` 형태로 분리 실행되는 것을 확인했음
- 이유: Docker init 스크립트는 내용뿐 아니라 실행 방식도 배포 결과를 바꾼다. shell option 을 강하게 쓰는 스크립트는 반드시 독립 프로세스로 실행되게 해야 다른 init 단계에 부작용을 남기지 않는다

## 87) 기존 볼륨에서는 드러나지 않던 `schema.sql` 말단 쉼표가 fresh init 에서만 신규 DB 부팅을 막을 수 있음
- 문제: 로컬에 이미 생성된 MySQL 볼륨을 재사용하면 `schema.sql` 이 다시 돌지 않아 숨어 있었지만, fresh init smoke 에서는 `notifications`, `chat_sessions` 정의 끝의 말단 쉼표 때문에 SQL 1064 로 초기화가 중단됐음
- 해결: `backend/src/main/resources/db/schema.sql` 에서 해당 말단 쉼표 2건을 제거하고, 임시 MySQL 8.0 컨테이너로 fresh init 을 다시 검증했음
- 이유: 신규 서버/새 볼륨 부팅 경로는 기존 개발 DB 재사용 경로와 다르다. 배포/초기화 작업을 건드릴 때는 항상 fresh init smoke 를 같이 돌려야 숨은 schema 문법 오류 재발을 막을 수 있다

## 88) 기존 운영 DB에서 계정 전환 시 `CREATE USER IF NOT EXISTS`만 믿으면 예전 비밀번호나 과권한이 그대로 남을 수 있음
- 문제: 신규 볼륨 init 스크립트 방식에 익숙해져 기존 운영 DB에서도 `CREATE USER IF NOT EXISTS`만 실행하면, 이미 존재하던 `app_core_rw`류 계정의 비밀번호와 grant가 그대로 남아 실제 앱 cutover 때 인증 실패나 과권한 상태가 이어질 수 있었음
- 해결: 기존 운영 DB용 템플릿은 `CREATE USER IF NOT EXISTS` 뒤에 `ALTER USER`, `REVOKE ALL PRIVILEGES, GRANT OPTION`, 재부여 `GRANT`를 한 세트로 넣고, 별도 runbook에서 `.env` cutover와 `SHOW GRANTS` 검증까지 같이 수행하도록 정리했음
- 이유: 신규 init과 기존 운영 계정 보정은 성격이 다르다. 기존 DB는 “있으면 생성”이 아니라 “있어도 현재 기준으로 덮어쓰기”가 핵심이라, 비밀번호/권한 재동기화 절차를 문서와 SQL 템플릿에 동시에 고정해야 재발을 막을 수 있다

## 89) `notification_pii_ro` 분리는 계정만 바꾸면 끝나지 않음
- 문제: 기존 알림 대상 조회 SQL은 `user_profiles`, `users`, `auth_users`, `user_pii`를 한 번에 조인하고 있어, 그대로 `notification_pii_ro` datasource로 옮기면 보조 계정에 main schema SELECT 또는 과도한 권한을 다시 줘야 했음
- 해결: 알림 대상 조회를 `core 대상 메타데이터 조회`와 `user_pii.email_enc 조회` 두 단계로 분리하고, `notification_pii_ro` 는 `user_pii(user_key, email_enc)` 읽기만 담당하게 재구성
- 이유: 다중 datasource 분리는 credential 추가만이 아니라 query shape 분리까지 같이 해야 최소권한이 유지된다. cross-schema 조인을 남겨두면 결국 더 넓은 grant가 다시 필요해져 분리 효과가 사라진다

## 90) secondary datasource bean을 직접 추가하면 Spring Boot 기본 `dataSource` 자동 구성이 뒤로 물러날 수 있음
- 문제: `notification_pii_ro`, `app_pii_rw` datasource bean을 직접 추가하자 Spring Boot의 기본 `spring.datasource` 자동 구성이 빠지고, 컨텍스트가 secondary datasource들만 보고 `PlatformTransactionManager` 를 만들지 못하거나 잘못된 datasource를 주 datasource처럼 취급할 수 있었음
- 해결: `PrimaryDataSourceConfig` 로 `spring.datasource` 기반 `dataSource` bean 을 명시적으로 `@Primary` 로 등록하고, secondary datasource는 qualifier 기반 보조 경로로만 사용하도록 고정
- 이유: 다중 datasource는 secondary bean 몇 개를 더 만드는 것으로 끝나지 않는다. 기본 datasource를 어떤 bean이 책임지는지 명시하지 않으면 JPA/트랜잭션/자동 구성 조건이 쉽게 무너진다

## 91) `app_pii_rw` write 분리는 read 분리와 달리 cross-datasource transaction 전략이 먼저 필요함
- 문제: 프로필 sync나 `user_pii` backfill write 를 바로 `app_pii_rw` datasource로 옮기면, legacy `users` / `auth_users` / `user_profiles` 와 `user_pii` 가 서로 다른 connection pool에서 갱신되어 현재 `@Transactional` 경계만으로는 원자성을 보장할 수 없었음
- 해결: 이번 단계는 프로필 조회와 비밀번호 재설정 수신 주소 조회 같은 read path만 `app_pii_rw` 로 먼저 이동하고, write 경로는 별도 transaction 전략 정리 task 뒤로 미뤘음
- 이유: read path는 권한 분리를 바로 얻어도 consistency 리스크가 낮지만, write path는 실패 시 split-table 간 불일치가 바로 남는다. 그래서 read-first, write-later 순서가 안전하다

## 92) `user_pii` write 분리도 요청 경로와 admin batch 경로를 같은 난이도로 보면 일정이 불필요하게 커짐
- 문제: `UserCoreSyncService` 의 요청 dual-write 와 `UserPiiBackfillService` 의 admin batch write 를 같은 “write 분리”로 묶어 보면, backfill 처럼 독립 row update 로 충분한 경로도 cross-datasource 원자성 문제 때문에 같이 멈춰 버릴 수 있었음
- 해결: 이번 단계에서는 `UserPiiBackfillService` 의 target selection 은 기존 primary query 를 유지하되 실제 update 만 `app_pii_rw` 로 옮겨 admin batch write 를 먼저 분리하고, 요청 dual-write 경로는 별도 transaction 전략 task 로 남겼음
- 이유: 같은 write 라도 consistency 요구 수준이 다르다. 요청 경로는 원자성이 중요하지만, admin backfill 은 재실행 가능한 batch 성격이라 먼저 분리해 권한 축소를 앞당길 수 있다

## 93) persistent Docker DB를 쓰는 통합 테스트는 새 migration을 자동 적용하지 않아 schema 검증에서 바로 실패할 수 있음
- 문제: 저장소에는 Flyway 자동 적용이 없어서, `user_pii_sync_queue` 엔티티와 migration 파일만 추가한 뒤 곧바로 통합 테스트를 돌리면 기존 Docker MySQL이 옛 스키마를 유지한 채 `ddl-auto: validate` 단계에서 실패할 수 있었음
- 해결: `V2026_04_28_02__add_user_pii_sync_queue.sql` 을 테스트 Docker MySQL에 수동 적용한 뒤 통합 테스트를 재실행했고, [db-migration.md](./db-migration.md)에 기존 DB/통합 테스트 DB는 migration 수동 적용이 필요하다는 점을 명시
- 이유: 이 저장소의 schema 변경 완료 조건은 `schema.sql` 수정만이 아니라 “기존 DB용 migration SQL 반영 + persistent 테스트 DB 반영”까지 포함된다. 자동 적용이 없는 환경에서는 이 절차를 문서와 검증 루틴에 같이 고정해야 재발을 막을 수 있다

## 94) derived delete repository 메서드를 테스트 cleanup에서 직접 쓸 때는 트랜잭션 의미를 메서드 시그니처에 명시해야 함
- 문제: `UserPiiSyncQueueRepository.deleteByUserKey()` 를 `@AfterEach` cleanup에서 직접 호출하자, 서비스 트랜잭션 밖이라 `TransactionRequiredException` 이 발생했음
- 해결: `UserPiiSyncQueueRepository.deleteByUserKey()` 에 `@Modifying`, `@Transactional` 을 명시해 cleanup에서도 독립 write query로 실행되도록 고정
- 이유: split-table/queue 전환이 늘어날수록 테스트 정리 루틴도 전용 repository delete 메서드를 자주 쓰게 된다. 이 경로는 서비스 본문처럼 이미 트랜잭션 안에 있다고 가정하면 깨지기 쉬우므로, cleanup에서 직접 쓰는 write 메서드는 스스로 트랜잭션 의미를 가져야 안전하다

## 95) Spring Data derived query 이름에 `...StatusInOrderBy...` 를 쓰면 단일 enum 인자를 `IN` 조건으로 오해해 컨텍스트가 뜨기 전에 죽을 수 있음
- 문제: `UserPiiSyncQueueRepository` 에서 실패/대기 queue 정렬 조회를 `findByStatusInOrderBy...` 형태로 추가했는데, 실제 시그니처는 `UserPiiSyncQueueStatus` 단일 인자라 Spring Data가 `status IN (?)` 으로 해석하려다 `Collection argument` 예외로 컨텍스트 초기화 자체가 실패했음
- 해결: 단일 status 조회는 `findByStatusOrderBy...` 로 바꾸고, bulk replay는 서비스에서 `FAILED` 와 `PENDING` 조회를 따로 수행한 뒤 userKey snapshot을 합쳐 재처리하도록 정리했음
- 이유: repository 메서드명 파싱 오류는 컴파일로는 잡히지 않고 부팅 시점에만 터진다. enum 단일값 정렬 조회는 `In` 을 붙이지 않는 쪽이 안전하고, 여러 상태를 합치는 로직은 서비스 계층에서 명시적으로 조합하는 편이 디버깅도 쉽다

## 96) queue retry scheduler 를 바로 켜면 integration test 가 백그라운드 재처리와 경합해 상태 검증이 흔들릴 수 있음
- 문제: `user_pii_sync_queue` 자동 retry 를 `@Scheduled` 로 추가한 뒤 integration test profile 에서도 기본 delay 값을 그대로 쓰면, 테스트가 queue 상태를 조작하는 중간에 백그라운드 스케줄러가 먼저 실행되어 `FAILED/PENDING` 상태 검증이나 replay 순서 검증이 비결정적으로 흔들릴 수 있었음
- 해결: `application-integration.yml` 에서는 `USER_PII_SYNC_RETRY_FIXED_DELAY_MS`, `USER_PII_SYNC_RETRY_INITIAL_DELAY_MS` 를 충분히 크게 고정하고, 통합 테스트는 스케줄러 메서드를 직접 호출해 retry 시점을 명시적으로 제어하도록 정리했음
- 이유: 상태 기반 retry 로직은 "언제 실행됐는가"가 검증 결과 자체를 바꾼다. 테스트 환경에서는 background scheduler를 사실상 멈추고, 필요한 시점에만 직접 실행하는 편이 재현성과 디버깅 비용 모두에서 안전하다

## 97) queue 상태 조회 API가 실패 폭주 때 sample limit 없이 열려 있으면 운영 확인 자체가 불필요한 read 부하가 될 수 있음
- 문제: `user_pii_sync_queue` 운영 상태를 보기 위한 admin read API에서 실패 sample 개수를 무제한으로 받으면, 장애 상황처럼 실패 row가 많을 때 운영 확인 요청이 곧 큰 정렬/조회 부하로 바뀔 수 있었음
- 해결: `GET /api/admin/users/pii-sync-status` 의 `failedSampleLimit` 를 `1..20` 범위로 clamp 하고, 문서 기본값도 `5` 로 고정했음
- 이유: 장애 대응용 조회는 가장 바쁜 순간에 호출된다. 운영 read API는 필요한 정보만 작은 크기로 제한해야 장애 원인 확인 과정이 시스템 부하를 더 키우지 않는다

## 98) Compose 앱이 기본 datasource만 `db` 서비스명으로 덮어쓰고 secondary datasource는 `localhost` fallback을 타면 cut-over smoke가 컨테이너 안에서 즉시 깨질 수 있음
- 문제: `docker-compose.yml` 에서 `DB_URL` 만 `db:3306` 으로 강제하고 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 은 비워 두면, 로컬 `.env` 에 secondary URL 이 없거나 `localhost` 기준일 때 앱 컨테이너가 `app_pii_rw` / `notification_pii_ro` datasource를 컨테이너 자기 자신으로 붙으려 해 connection refused 를 일으킬 수 있었음
- 해결: Compose 앱 environment 에 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, secondary credential 기본값을 같이 명시해 secondary datasource도 기본적으로 `db` 서비스명과 앱 계정으로 뜨도록 고정했고, `deploy/smoke/user-pii-sync-cutover-smoke.sh` 로 이 경로를 바로 검증할 수 있게 함
- 이유: 다중 datasource를 넣은 뒤에는 primary datasource만 컨테이너 친화적으로 맞춰도 충분하지 않다. compose 환경은 secondary datasource까지 모두 서비스명 기준으로 덮어써야 실제 운영 smoke와 로컬 재현이 같은 경로를 탄다

## 99) `user_pii` cut-over smoke는 queue/dual-write만 보는 테스트처럼 보여도 실제로는 앱의 `AES_SECRET_KEY` 준비 상태를 함께 요구함
- 문제: `deploy/smoke/user-pii-sync-cutover-smoke.sh` 는 회원가입과 프로필 수정을 통해 request-path PII sync를 검증하므로, 앱이 `AES_SECRET_KEY` 없이 떠 있으면 queue 로직 이전에 암호화 단계에서 `C002` 500으로 중단될 수 있었음
- 해결: smoke 스크립트 사용 문서에 `AES_SECRET_KEY` 사전 확인을 명시하고, 검증 결과에도 local `.env` 의 빈 secret 때문에 full smoke가 중단될 수 있음을 남겼음
- 이유: PII cut-over smoke는 DB migration만의 문제가 아니라 앱 secret 주입까지 포함한 end-to-end 경로다. 운영 실행 전에 secret 상태를 먼저 확인해야 migration 문제와 암호화 환경 문제를 헷갈리지 않는다

## 100) secondary datasource를 도입한 뒤에도 primary JPA가 `user_pii` 엔티티/리포지토리를 계속 들고 있으면 최소권한 회수가 막힘
- 문제: 프로필 조회, 알림, backfill, request sync를 이미 `app_pii_rw` / `notification_pii_ro` 로 나눴어도 `UserPii` JPA 엔티티와 `UserPiiRepository` 가 기본 persistence unit에 남아 있으면, 런타임 코드가 다시 primary datasource로 `user_pii` 를 읽거나 쓰기 쉬웠고 `app_core_rw` 의 `user_pii` DML 권한도 자신 있게 회수하기 어려웠음
- 해결: `UserPii` / `UserPiiRepository` 를 제거하고, `UserPiiBackfillService` 는 primary `users` source 조회와 `app_pii_rw` 의 `user_pii` 누락 암호문 조회/수정 2단계로 재구성했으며, integration test도 같은 `UserPiiReadWriteRepository` 경로를 사용하도록 정리했음
- 이유: 다중 datasource 분리의 마지막 단계는 "코드가 그 권한을 정말 더 이상 쓰지 않는가"를 구조로 보장하는 것이다. primary JPA에 PII 엔티티가 남아 있으면 실수로 우회 경로가 다시 생기므로, 엔티티/리포지토리 자체를 제거하는 편이 재발 방지에 확실하다

## 101) `app_core_rw` 의 `user_pii` grant만 먼저 회수하면 secondary datasource fallback이 여전히 기본 계정을 바라봐 운영 경로가 즉시 깨질 수 있음
- 문제: grant 템플릿에서 `app_core_rw -> youth_welfare_pii.user_pii` 권한을 제거하더라도 `docker-compose.yml` / `application.yml` 의 secondary datasource username fallback 이 계속 `DB_USERNAME` 을 따라가면, 프로필 조회·비밀번호 재설정·알림·PII sync가 여전히 `app_core_rw` 로 붙으려 해 권한 오류가 발생할 수 있었음
- 해결: compose/app 설정의 secondary datasource 기본 username 을 `app_pii_rw` / `notification_pii_ro` 로 고정하고, `deploy/smoke/user-pii-sync-cutover-smoke.sh` 에 cross-schema query account scope preflight 를 추가해 `migration_admin` 또는 `DB_QUERY_*` 가 필요할 때 조기에 실패하도록 정리했음
- 이유: 최소권한 회수는 SQL 한 줄로 끝나지 않는다. grant 모델과 애플리케이션 fallback을 같이 바꾸지 않으면 코드 경계는 맞아도 실제 런타임 연결 계정이 예전 값으로 남아 바로 장애로 이어진다

## 102) secondary datasource 계정은 username만 분리해도 충분하지 않고, JDBC URL의 기본 database/schema도 `youth_welfare_pii` 로 바꿔야 함
- 문제: `app_pii_rw` / `notification_pii_ro` 권한을 `youth_welfare_pii.user_pii` 로만 줄인 뒤에도 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 계속 `.../youth_welfare` 를 가리키면, MySQL이 연결 시점에 `Access denied for user 'app_pii_rw'@'%' to database 'youth_welfare'` 로 거부해 앱이 reduced-grant 상태에서 부팅/요청 처리 중 바로 깨질 수 있었음
- 해결: `.env.example`, `docker-compose.yml`, `application.yml` 의 secondary datasource 기본 URL을 `youth_welfare_pii` schema로 교정하고, `deploy/smoke/run-local-pii-sync-cutover-smoke.sh` 로 fresh init + app boot + one-shot smoke까지 실제 검증했으며, 운영 runbook에도 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 전환 단계를 추가했음
- 이유: 최소권한 설계는 grant 범위와 connection target이 함께 맞아야 성립한다. SQL이 fully-qualified table을 써도 JDBC connection 자체는 기본 database에 먼저 붙기 때문에, URL이 잘못되면 권한 모델이 맞아도 연결 단계에서 즉시 실패한다

## 103) fresh init 검증용 DB seed가 runtime `DB_USERNAME` 을 따라가면 stale local `.env` 하나로 split-account 테스트가 다시 root 의존으로 돌아갈 수 있음
- 문제: `docker-compose.yml` 의 DB init용 `MYSQL_APP_USERNAME` 이 runtime `DB_USERNAME` 을 그대로 따라가면, 로컬 `.env` 가 아직 `root` 인 상태에서 fresh init 할 때 `app_core_rw` 대신 root 성격 계정만 만들어져 integration profile이나 reduced-grant smoke가 실제 운영 계정 구조를 검증하지 못할 수 있었음
- 해결: DB init용 기본값을 `MYSQL_APP_USERNAME=app_core_rw` 로 runtime `DB_USERNAME` 과 분리하고, `application-integration.yml` 도 `app_core_rw` / `app_pii_rw` / `notification_pii_ro` 기준으로 정리한 뒤 fresh init + `AuthRedisIntegrationTest` + container smoke로 다시 검증했음
- 이유: 런타임 앱이 어떤 계정으로 붙는지와 fresh init 시 어떤 계정을 seed할지는 별개다. init 계정 생성이 stale local env에 끌려가면 테스트가 실제 운영 설계가 아니라 개발자 개인 `.env` 상태를 검증하게 되므로, seed 기본값은 문서화된 split-account 기준으로 고정하는 편이 재발 방지에 안전하다

## 104) 운영 env 전환은 앱 startup validation만 믿으면 재기동까지 기다린 뒤에야 실수를 발견하게 됨
- 문제: `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, split datasource username을 잘못 넣어도 앱 startup validation은 재기동 후에야 터지므로, 운영 cutover 직전에 `.env` / secret store 값을 안전하게 검토하는 빠른 preflight가 없으면 배포 시점에만 문제를 발견하게 될 수 있었음
- 해결: `deploy/smoke/preflight-runtime-cutover-env.sh` 를 추가해 `.env` 또는 export된 env를 기준으로 필수 변수, 기대 username, core/PII schema 분리, secondary URL 오배치, 가능하면 `docker compose config` 렌더링까지 앱 기동 전에 확인하도록 정리하고 runbook/deployment 문서에 선행 단계로 반영했음
- 이유: 운영 전환 검증은 “앱이 실패하면 알 수 있다”가 아니라 “앱을 띄우기 전에 틀린 값을 걸러낸다”가 더 안전하다. 특히 secret store 갱신과 재기동 사이의 피드백 루프를 줄여야 cutover 시간을 짧게 유지할 수 있다

## 105) `.env` 를 shell `source`로 직접 읽으면 JDBC URL의 `&` 때문에 값이 잘리거나 background job이 생겨 cutover/smoke 명령이 조용히 잘못될 수 있음
- 문제: `.env` 의 `DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 에는 `allowPublicKeyRetrieval=true&characterEncoding=UTF-8...` 같은 `&` 가 들어가는데, 이를 `source .env` 로 읽으면 shell이 `&` 를 명령 분리자로 해석해 변수 값이 중간에서 끊기고 뒤쪽 문자열을 별도 job처럼 실행할 수 있었음
- 해결: `deploy/smoke/preflight-runtime-cutover-env.sh` 와 `deploy/smoke/user-pii-sync-cutover-smoke.sh` 가 `.env` 를 직접 파싱하도록 바꾸고, 문서 예시도 `ENV_FILE=.env ...` 형태로 교체했음
- 이유: `.env` 는 shell script가 아니라 key-value 파일이다. URL query string처럼 shell meta character가 포함될 수 있으므로, 운영 스크립트와 문서는 `source`에 기대지 않고 파일을 안전하게 직접 읽는 쪽이 재발 방지에 맞다

## 106) `ENV_FILE` 을 읽는 스크립트가 caller의 explicit env override보다 파일 값을 우선하면, stale `.env` 를 임시로 우회해야 하는 smoke/preflight에서 계정 override가 무시될 수 있음
- 문제: local `.env` 가 아직 `DB_USERNAME=root` 인 상태에서 `ENV_FILE=.env DB_QUERY_USERNAME=migration_admin ... deploy/smoke/user-pii-sync-cutover-smoke.sh` 처럼 override를 주더라도, 스크립트의 `.env` 파서가 나중에 파일 값을 다시 export해 caller가 준 `DB_QUERY_*` / `DB_MIGRATION_*` override를 덮어쓸 수 있었음
- 해결: `deploy/smoke/user-pii-sync-cutover-smoke.sh` 와 `deploy/smoke/preflight-runtime-cutover-env.sh` 의 `.env` 파서를 수정해, caller가 이미 넘긴 env key는 파일에서 다시 덮어쓰지 않도록 바꿨음
- 이유: 운영 cut-over나 local smoke에서는 `ENV_FILE` 을 기본값 묶음으로 쓰고 일부 key만 명시 override하는 경우가 자주 생긴다. 이때 가장 의도가 명확한 값은 caller가 명령 앞에 준 env이므로, precedence도 그 순서를 따라야 재시도와 우회가 단순해진다

## 107) pass/fail만 나오는 env preflight는 운영 secret cutover 직전에 “어떤 값으로 검증됐는지”를 바로 확인하기 어려워, key 이름 실수나 예상치 못한 override를 놓치기 쉬움
- 문제: split-account 전환 직전에는 preflight 통과 여부뿐 아니라 `DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 실제로 어떤 host/schema 조합으로 해석됐는지와, username/password key가 모두 채워졌는지를 운영자가 바로 눈으로 다시 확인할 필요가 있었음. pass/fail만 있으면 secret store 값과 effective env를 대조할 때 한 번 더 수작업이 필요했음
- 해결: `deploy/smoke/preflight-runtime-cutover-env.sh` 에 `PRINT_SUMMARY=true` 옵션을 추가해 redacted summary를 출력하도록 보강하고, runbook/deployment 문서에도 cutover 직전 이 모드 실행을 체크리스트로 반영했음
- 이유: 운영 전환은 “검증이 통과했다”보다 “무슨 값으로 검증이 통과했는지 확인했다”가 더 안전하다. 특히 split datasource처럼 key 수가 늘어난 경우에는 effective config를 짧게 요약해서 눈으로 확인할 수 있어야 재시도와 롤백 판단이 빨라진다

## 108) 운영 cutover 단계가 계정 runbook, migration 문서, deployment 문서에 흩어져 있으면 실제 전환 창에서 순서가 섞이거나 smoke를 빼먹기 쉬움
- 문제: split-account 전환은 `계정 SQL`, `migration`, `preflight summary`, `app 재기동`, `핵심 smoke` 가 모두 필요하지만, 세부 설명이 여러 문서에 나뉘어 있어 실제 운영 전환 때는 어떤 순서로 실행해야 하는지 다시 조합해야 했음
- 해결: `docs/runtime-cutover-checklist.md` 를 추가해 실제 cutover 창에서 따라갈 순서를 한 페이지로 압축하고, `deployment.md`, `db-account-cutover-runbook.md`, `README.md` 에서 바로 링크되도록 정리했음
- 이유: 운영 작업은 정보의 양보다 실행 순서의 명확성이 더 중요하다. 긴 설명 문서는 예외 처리에 좋지만, 전환 창에서는 한 페이지 checklist가 있어야 누락 없이 빠르게 진행할 수 있다

## 109) cutover 후 증적이 터미널 출력, 채팅, 메모에 흩어지면 rollback 판단과 사후 검토가 느려지고, 다음 전환 때 재사용할 기준도 남지 않음
- 문제: split-account cutover는 `SHOW GRANTS`, migration 적용 결과, preflight summary, health check, 핵심 API smoke, optional one-shot smoke까지 확인 대상이 많아서, 결과를 즉석에서만 보고 지나가면 성공/실패 판정 근거가 문서로 남지 않을 수 있었음
- 해결: `docs/runtime-cutover-log-template.md` 를 추가해 실제 운영 전환 직후 결과를 한 문서에 기록하도록 하고, checklist/runbook/deployment 문서에서 바로 링크되도록 정리했음
- 이유: 운영 전환은 “실행했다”보다 “무엇을 실행했고 어떤 결과였는지 남겼다”가 중요하다. 특히 rollback 여부를 빠르게 판단하거나 다음 차수 cutover를 반복할 때는 동일한 증적 형식이 있어야 비교와 회고가 쉬워진다

## 110) 운영 smoke 명령을 현장에서 다시 조합하면 cookie jar, Bearer token, bookmark path id 종류를 헷갈려 false negative를 만들기 쉬움
- 문제: cutover 직후 확인해야 할 로그인, refresh, 추천, 북마크, admin status는 단순해 보여도 `refresh_token` cookie 저장, `Authorization: Bearer` 헤더, 추천 bookmark path에 recommendation `id` 를 넣는 규칙처럼 자주 헷갈리는 포인트가 있어, 운영자가 그때그때 curl을 다시 만들면 smoke 자체가 틀릴 수 있었음
- 해결: `docs/runtime-api-smoke-commands.md` 를 추가해 공통 env, cookie jar, access token 추출, 추천 응답에서 recommendation `id` 추출, admin status 호출까지 copy-paste 가능한 명령 묶음으로 정리하고 checklist/log template에서 바로 링크되도록 반영했음
- 이유: 운영 smoke는 코드 이해를 시험하는 자리가 아니라 배포 안전성을 확인하는 절차다. 반복되는 인증/토큰/ID 타입 함정은 문서화된 명령으로 고정하는 편이 false negative를 줄이고 cutover 시간을 단축한다

## 111) 실행 로그 템플릿만 있으면 “어느 정도 상세도로 채워야 하는지”가 애매해, 실제 전환 때는 다시 짧거나 들쭉날쭉한 기록으로 흘러가기 쉬움
- 문제: `runtime-cutover-log-template.md` 로 기록 항목은 정리됐지만, 실제로는 어느 수준의 결과 문장과 redaction을 넣어야 하는지 감이 없으면 작업자마다 기록 품질이 달라질 수 있었음
- 해결: `docs/archive/runtime-cutover-log-sample.md` 를 추가해 redacted된 sample cutover 기록을 함께 두고, checklist/template 문서에서 바로 참고할 수 있게 링크를 연결했음
- 이유: 운영 기록도 코드처럼 예시가 있어야 품질이 안정된다. 템플릿만 있으면 최소 항목은 맞춰도 실제 서술 수준이 제각각이 되기 쉬우므로, sample을 함께 두는 편이 다음 차수 cutover 반복성과 비교 가능성을 높인다

## 112) 수동 migration 예시 순서가 문서마다 어긋나면 로컬/운영 리허설에서 “무엇을 먼저 적용해야 하는지”를 다시 판단하게 되어 실수 여지가 생김
- 문제: `runtime-cutover-checklist.md` 는 최신 2종 migration을 `V2026_04_28_01 -> V2026_04_28_02` 순서로 안내하고 있었지만, `db-migration.md` 의 수동 적용 예시는 older migration과 최신 migration이 뒤섞여 있어 local `migration_admin` 리허설 시 실제 적용 순서를 다시 해석해야 했음
- 해결: pre-28 schema 임시 MySQL 8.0에서 `migration_admin` 으로 `V2026_04_28_01 -> V2026_04_28_02` 를 직접 적용해 검증한 뒤, `docs/db-migration.md` 의 mysql/docker 예시를 dependency 기준 순서로 재정렬하고 최신 2종은 같은 순서로 명시했음
- 이유: 수동 cut-over 문서는 실행 예시끼리 같은 순서를 유지해야 작업자가 맥락 없이 복사해도 안전하다. 특히 최신 migration 2종처럼 같은 날 추가된 파일은 체크리스트, runbook, migration 가이드가 한 순서를 공유해야 재시도와 회고가 단순해진다

## 113) `docker exec` 로 SQL 파일을 리다이렉션할 때 `-i` 를 빼면 명령이 성공처럼 보여도 migration 본문이 컨테이너 mysql에 전달되지 않을 수 있음
- 문제: 로컬 smoke 중 `docker exec ... mysql ... < migration.sql` 형태로 수동 migration을 다시 태우다가 `-i` 를 빠뜨리면, host shell의 리다이렉션 파일이 컨테이너 stdin으로 전달되지 않아 `mysql` 이 빈 입력으로 종료하고도 겉보기에는 명령이 조용히 끝날 수 있었음
- 해결: 수동 migration 예시와 실제 실행 모두 `docker exec -i ... mysql ... < migration.sql` 형태로 맞추고, 적용 직후 `SHOW TABLES` / `SHOW COLUMNS` 같은 후속 검증 쿼리로 schema 변경이 실제 반영됐는지 바로 확인함
- 이유: 컨테이너 안의 mysql 클라이언트가 host 쪽 리다이렉션 내용을 읽으려면 stdin이 열린 상태여야 한다. 수동 cut-over는 실행 성공 여부보다 결과 schema를 즉시 검증하는 습관이 있어야 동일한 실수를 빨리 잡을 수 있다

## 114) 로그인 직후 즉시 `refresh` 하면 새 access token 문자열이 기존 로그인 token과 같을 수 있어, “토큰 값이 바뀌었는지”를 smoke 성공 기준으로 잡으면 false negative가 생길 수 있음
- 문제: admin refresh smoke 중 로그인 직후 바로 `POST /api/auth/refresh` 를 호출하자 응답은 성공이었지만 새 access token 문자열이 로그인 응답의 token과 같아, 단순 문자열 비교를 성공 기준으로 두면 refresh 실패로 오해할 수 있었음
- 해결: refresh smoke와 관련 문서의 확인 기준을 “refreshed token으로 보호 API를 다시 호출해 권한이 유지되는지”로 맞추고, `docs/runtime-api-smoke-commands.md` 에도 같은 주의를 추가했음
- 이유: 현재 JWT는 같은 초 안에서 같은 subject/userId/roles로 다시 발급되면 동일한 토큰 문자열이 나올 수 있다. 이 경우 핵심은 토큰 문자열 변화가 아니라 refresh 응답 성공과 그 token으로 실제 보호 API가 계속 통과하는지다

## 115) logout 직후 refresh 실패를 기대할 때는 Redis reuse 오류보다 cookie clear 이후의 `A001 INVALID_TOKEN` 경로가 먼저 보일 수 있음
- 문제: admin logout smoke에서 `POST /api/auth/logout` 뒤 바로 `POST /api/auth/refresh` 를 호출했더니, Redis에 저장된 refresh token이 지워졌으므로 재사용 탐지 성격의 오류를 기대하기 쉬웠지만 실제 응답은 `401`, `errorCode=A001` 이었음
- 해결: smoke 기대값과 문서를 `logout -> refresh cookie clear -> 직후 refresh는 A001` 기준으로 정리하고, 성공 판단은 이후 재로그인으로 admin 보호 API가 다시 회복되는지까지 포함하도록 맞췄음
- 이유: 현재 logout 응답은 `Set-Cookie` 로 `refresh_token` 자체를 먼저 비우므로, 클라이언트는 재호출 시 토큰이 없는 상태로 `/api/auth/refresh` 를 치게 된다. 이 경우 서버는 reuse 검출보다 앞단의 “토큰 없음/비어 있음” 경로에서 `INVALID_TOKEN` 을 반환하는 것이 자연스럽다

## 116) logout 은 refresh token만 회수하므로 이미 발급된 access token은 만료 전까지 보호 API에 계속 통과할 수 있음
- 문제: pre-28 migrated DB 기준 로컬 smoke에서 `POST /api/auth/logout` 뒤 refresh cookie는 정상적으로 비워지고 직후 `POST /api/auth/refresh` 도 `401 / A001` 로 막혔지만, logout 전에 받은 old admin access token으로 `GET /api/admin/users/pii-sync-status`, `POST /api/admin/users/pii-sync-replay?userKey=<USER_KEY>` 를 다시 호출하면 둘 다 계속 `200` 으로 통과했음
- 해결: 현재 동작을 `docs/phase-plan.md`, `docs/runtime-api-smoke-commands.md` 에 명시하고, “logout 후 access token 즉시 무효화 전략 검토/구현” 을 별도 hardening task로 작업 추적에 추가했음
- 이유: 현재 `JwtAuthenticationFilter` 는 bearer access token의 서명/만료만 검증하고 별도 Redis blacklist나 logout cutoff를 조회하지 않으며, `logout` 구현도 refresh token 삭제와 cookie clear에만 집중한다. 따라서 이미 발급된 access token은 만료 전까지 stateless 하게 유효한 것이 현재 설계상 자연스러운 결과다
