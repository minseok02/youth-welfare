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
