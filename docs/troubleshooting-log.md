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

## 117) 수집 저장이 `service_tags` 를 upsert-only 로 누적하면 외부 API에서 제거된 tag 가 DB에 영구 잔존할 수 있음
- 문제: `CollectItemSaver` 는 새 tag만 `upsert` 하고 기존 tag 삭제 경로가 없어, 정책 source의 키워드/대상/생애주기 값이 바뀌거나 빠져도 `service_tags` 에는 예전 값이 계속 남을 수 있었음
- 해결: 수집 저장 시 `service_id` 기준 기존 tag를 먼저 지우고, 현재 source에서 계산한 tag 집합을 `TagType + tagValue` 기준으로 dedupe 한 뒤 다시 저장하도록 변경했음. tag가 비면 기존 tag를 전부 제거하고 빈 목록으로 relevance 계산을 다시 수행하도록 맞췄음
- 이유: 추천/검색 필터는 현재 source snapshot에 수렴해야 한다. append-only tag 저장은 source가 변할수록 오염 데이터가 누적되므로, 목록 수집 save는 region처럼 tag도 “현재 상태로 교체”하는 쪽이 안전하다

## 118) 상세 수집이 `existsByServiceId` 만 보고 skip하면 upstream 상세 본문 변경이 영구 반영되지 않을 수 있음
- 문제: 복지로 상세 수집은 기존 `welfare_service_details` row가 있는지만 보고 바로 skip하므로, 외부 상세 API의 지원내용/신청방법/문의처가 바뀌어도 기존 row가 남아 있는 한 다시 fetch/merge 할 수 있는 경로가 없었음
- 해결: 기본 상세 수집은 그대로 두되, `BOKJIRO_DETAIL_REFRESH` 전용 source와 `/api/admin/collect/bokjiro-details-refresh` 수동 endpoint를 추가해 refresh 모드에서는 기존 row가 있어도 다시 fetch 후 같은 row id로 merge 저장하도록 분리했음
- 이유: 상세 수집은 호출 단가가 높아 기본 배치와 refresh 동작을 분리하는 편이 안전하다. missing-row 채우기와 full refresh를 같은 경로에 섞으면 호출량과 정합성 기대가 충돌하므로, 운영자가 의도를 명시할 수 있는 별도 경로가 필요하다

## 119) 관리자 수동 수집 경로가 source별 controller/service 메서드 fan-out으로 늘어나면 새 데이터 API 추가 때 endpoint 연결 누락이 다시 생기기 쉬움
- 문제: 수집 adapter registry로 본문 orchestration은 줄였지만, 관리자 수동 수집은 여전히 `/collect/youth`, `/collect/bokjiro-central` 식의 개별 controller 메서드와 `CollectService.collectYouth()` 같은 source별 public 메서드가 남아 있어 새 source를 붙일 때 같은 fan-out을 다시 추가해야 했음
- 해결: `CollectAdminController` 를 `/api/admin/collect/{sourceKey}` 단일 endpoint로 정리하고, `CollectSource` enum 에 path key / trigger label / success message를 올린 뒤 `CollectService.collect(CollectSource source)` 단일 진입점으로 dispatch 하도록 줄였음. invalid source는 `C001` 로 바로 거절하도록 테스트도 고정했음
- 이유: 신규 데이터 API 추가 난이도를 낮추려면 source metadata와 adapter 등록만으로 관리자 수동 실행까지 이어져야 한다. orchestration만 추상화하고 admin trigger fan-out을 남겨두면 실제 확장 시 누락 지점이 다시 controller/service로 분산된다

## 120) local persistent MySQL volume 이 현재 split-account 기본값과 어긋난 상태면 `docker compose up -d db redis` 직후 integration test 가 코드와 무관하게 `app_core_rw` 인증 실패로 막힐 수 있음
- 문제: 관리자 수동 수집 generic dispatch 검증 중 `docker compose up -d db redis` 뒤 `./gradlew integrationTest --tests com.example.welfare.integration.AdminSecurityIntegrationTest` 를 실행하자, 애플리케이션 context 초기화 단계에서 `Access denied for user 'app_core_rw'` 가 발생해 테스트가 기동조차 되지 않았음
- 해결: 이번 task의 코드 회귀 여부는 `CollectServiceTest`, `AdminSecurityWebMvcTest` 로 확인하고, integration 실패 원인은 local persistent MySQL volume 의 계정 상태가 현재 `application-integration.yml` / split-account 기본값과 drift 된 환경 문제로 분리 기록했음. 이 경우 fresh init smoke 또는 known password 기준 계정 재정렬 후 다시 integration 을 태워야 함
- 이유: Compose DB 컨테이너를 recreate 해도 volume 은 유지되므로, 예전 root/app 계정 비밀번호나 grant 상태가 남아 있으면 현재 문서/설정 기본값과 달라도 자동으로 맞춰지지 않는다. split-account 전환 이후에는 “컨테이너 재기동 = 계정 재초기화”라고 가정하면 재발하기 쉽다

## 121) 상세 수집의 `429` 중단, empty payload skip, partial success(save failure 후 계속) 규칙이 테스트로 고정돼 있지 않으면 리팩터링 중 제어 흐름이 쉽게 흔들릴 수 있음
- 문제: 상세 수집은 운영상 중요한 edge case 규칙이 이미 코드에 들어 있었지만, 기존 테스트는 “existing row skip” 과 “refresh update” 정도만 확인하고 있어 `429` 연속 중단 기준, empty payload 무시, 일부 저장 실패 후 다음 정책 계속 처리 같은 동작이 리팩터링 중 바뀌어도 바로 드러나지 않을 수 있었음
- 해결: `BokjiroDetailCollectServiceTest` 에 연속 `429` 임계치 중단, empty payload skip, save failure 후 다음 정책 계속 처리 3개 케이스를 추가해 `requested/saved/failed` 집계와 후속 호출 여부를 고정했음
- 이유: 신규 데이터 API 추가를 쉽게 만들려면 수집 파이프라인 구조를 바꾸더라도 기존 운영 계약은 테스트로 붙잡아야 한다. 특히 상세 수집은 호출 제한과 부분 성공 규칙이 얽혀 있어, 회귀가 나면 증상이 늦게 보이므로 단위 테스트로 먼저 막는 편이 안전하다

## 122) local DB 계정 drift 복구 스크립트가 inherited shell env 나 현재 `.env` 의 `DB_USERNAME=root` 를 그대로 따라가면, 정작 필요한 `app_core_rw` 계정은 복구되지 않고 integration 이 계속 `Access denied` 로 막힐 수 있음
- 문제: 로컬 Docker `mysql_data` volume의 split-account drift를 복구하려고 스크립트를 만들었지만, 초안은 기존 shell env 값을 보존하고 `DB_USERNAME` 을 그대로 app 계정명으로 써서, 작업 세션에 숨은 `DB_PASSWORD` 가 있거나 로컬 `.env` 가 아직 `DB_USERNAME=root` 인 경우 `app_core_rw` 대신 root만 다시 맞추는 잘못된 복구로 이어질 수 있었음
- 해결: `deploy/mysql/reconcile-local-runtime-db-accounts.sh` 는 기본적으로 `ENV_FILE` 값을 shell env보다 우선으로 읽고, 대상 username도 `app_core_rw / app_pii_rw / notification_pii_ro / migration_admin` 으로 고정하도록 바꿨음. 이후 현재 volume에 재적용해 `app_core_rw`, `app_pii_rw` TCP 로그인과 `AdminSecurityIntegrationTest` 통과까지 확인했음
- 이유: 로컬 drift 복구의 목적은 “현재 앱/runtime split-account 기준으로 다시 맞추기”이지, 이미 drift 된 `.env` runtime username을 그대로 재현하는 것이 아니다. hidden env나 legacy `root` username을 따라가면 복구 스크립트가 성공처럼 끝나도 integration 경로는 계속 깨질 수 있으므로, file precedence와 target username을 의도적으로 고정해야 한다

## 123) 신규 데이터 API 확장성을 위해 `온통청년` 대분류만 canonical schema로 유지하면, 범정부 서비스 공통 메타데이터와 공식 지원조건 코드를 충분히 흡수하지 못해 source가 늘수록 정규화가 다시 ad-hoc 해질 수 있음
- 문제: 현재 구조는 `온통청년` 이 가장 구조화가 잘 된 source라는 이유로 `unifiedCategory`, `categoryMain/categorySub`, 일부 `minIncome/maxIncome` 같은 축이 사실상 `온통청년` 중심으로 설계돼 있다. 하지만 외부 기준을 다시 확인해보니 `정부24/보조금24` 는 `serviceList`, `serviceDetail`, `supportConditions` 로 공통 서비스 메타데이터와 지원조건을 공식적으로 분리하고 있고, `온통청년` 운영 코드북도 별도의 `정책대분류/중분류/키워드/제공방법/취업·학력·특화 요건코드` 를 갖고 있어 단일 소스 축으로 다 덮는 방식은 장기적으로 맞지 않았음
- 해결: 정규화 구조 조사 문서 `docs/policy-normalization-research.md` 를 추가해 canonical 기준을 `범정부 공공서비스 core + 온통청년 taxonomy/codebook + 구조화 eligibility facts + AI enrichment` 4계층으로 재정리하고, 다음 작업을 `core / taxonomy / fact` 스키마 초안과 code table 설계로 분리했음
- 이유: 신규 API가 늘수록 “공식 공통축”과 “도메인 특화축”을 분리해야 mapping 비용이 내려간다. 공통 메타데이터는 `Gov24` 축으로, 청년정책 분류는 `온통청년` 축으로, source 고유 필드는 raw/AI enrichment로 분리해야 추후 추천 hard filter와 soft signal을 안정적으로 확장할 수 있다

## 124) 정규화 4계층 구조를 바로 기존 `WelfareService` 와 `ServiceTag` 를 대체하는 빅뱅 교체로 밀어붙이면, 추천 SQL·응답 계약·실시간 AI 입력이 한 번에 깨질 위험이 큼
- 문제: 현재 코드에서 `WelfareServiceRepository.findCandidates*` 는 `minAge/maxAge`, `minIncome/maxIncome`, `sourceType`, `status`, `unifiedCategory` 를 직접 쓰고 있고, `RetrievalService` / `RuleScoringService` / `YouthPolicyFilter` 는 `ServiceTag` 4종 enum에 강하게 묶여 있다. 또 `DefaultPriorityMatcher`, `PolicyDetailResponse`, `RecommendationResponse` 는 `unifiedCategory` 문자열을 그대로 노출하거나 비교하고, `RealtimeAiGateway` 도 제목·카테고리·요약만 읽는다. 이런 상태에서 sidecar 없이 새 canonical 구조로 바로 치환하면 추천 후보 추출, 후처리, 응답 계약, AI 호출이 동시에 흔들릴 수 있었음
- 해결: `docs/policy-normalization-research.md` 에 feasibility 검토를 추가해 전환 방식을 `sidecar 테이블 추가 -> 저장 경계 확장 -> read-model 추가 -> 추천 hard filter 일부 이관 -> AI enrichment` 순서의 점진 이행으로 고정하고, 후속 작업도 `service_taxonomies/service_facts` 스키마 초안, 추천 의존부 이행 순서 설계, `unifiedCategory` 호환 전략으로 쪼갰음
- 이유: 새 canonical 구조 도입 자체는 타당하지만, 현재 시스템은 추천 SQL과 응답 계약이 이미 운영 규약처럼 굳어 있다. 이런 상태에서 빅뱅 교체를 하면 “정규화 설계는 맞는데 런타임 기능이 깨지는” 상황이 생기므로, sidecar 기반 점진 이행으로 기존 read/write 계약을 한동안 병행 유지하는 편이 재발 방지에 안전하다

## 125) Gov24/보조금24는 `core/detail/facts` 는 강하지만 `청년정책 taxonomy` 를 직접 주지 않아서, 곧바로 현재 `unifiedCategory` / priority 체계를 대체하려 하면 추천 의미가 흐려질 수 있음
- 문제: 샘플 기반으로 다시 보니 Gov24 `serviceList/serviceDetail/supportConditions` 는 서비스 메타데이터, 서류/법령, 연령/소득/학력/취업/가구특성 같은 structured fact는 매우 잘 주지만, 현재 추천/우선순위가 쓰는 `주거/일자리/교육·직업훈련/금융·생활지원` 같은 청년정책 분류축을 직접 제공하지 않는다. `서비스분야` 하나만으로는 청년정책 major/mid category나 우선순위 의미를 그대로 복원하기 어려웠음
- 해결: `docs/policy-normalization-sample-spike.md` 에 Gov24 sample 결과를 별도 정리하고, 후속 작업을 `Gov24 service field / user type / benefit type -> compatibility unifiedCategory / youth taxonomy bridge` 규칙 초안 작성으로 분리했음. canonical에서도 Gov24 taxonomy는 official layer로 저장하되, 기존 priority/read-model 호환은 별도 bridge 또는 `system_derived` 분류층에서 처리하는 방향으로 고정했음
- 이유: hard filter 축과 도메인 taxonomy 축은 다르다. Gov24가 facts를 많이 준다고 해서 청년정책 분류까지 자동으로 대체할 수는 없으므로, official source field와 서비스 호환 분류를 같은 계층으로 섞지 않는 편이 재발 방지에 안전하다

## 126) 외부 코드북(`Gov24 supportConditions`, `온통청년` 운영 코드)을 Java enum으로 고정하면 source 코드 변경이 곧 배포 이슈가 되어 정규화 확장성이 다시 떨어질 수 있음
- 문제: 새 canonical 구조를 실제 스키마로 내리면서 보니, `JA0203`, `JA0327` 같은 Gov24 조건 코드와 온통청년 대분류/중분류/제공방법/취업·학력·특화 코드들은 source 문서 개정 시 추가/비활성/라벨 수정이 발생할 수 있다. 이걸 Java enum으로 박아두면 코드북 변경이 곧 애플리케이션 릴리스와 1:1로 묶이고, 코드/DB/문서가 다시 쉽게 어긋날 수 있었음
- 해결: `docs/policy-normalization-schema-draft.md` 에서 코드 저장 결정을 `normalization_code_sets`, `normalization_codes` DB code table 방식으로 고정하고, 서비스별 값은 `service_taxonomies` / `service_taxonomy_terms` / `service_facts` 에 authority와 함께 저장하는 구조로 정리했음
- 이유: 외부 코드북은 애플리케이션 상수라기보다 운영 데이터에 가깝다. 코드셋 메타데이터와 실제 코드값을 DB에 분리 보관해야 라벨 변경, parent 관계 추가, deprecated 처리, source 버전 추적을 무중단에 가깝게 관리할 수 있어 재발 방지에 안전하다

## 127) Gov24 공식 분류와 서비스 호환용 `compat_unified_category` 를 같은 계층으로 취급하면, official taxonomy와 derived bridge가 뒤섞여 추천/응답 의미가 흐려질 수 있음
- 문제: Gov24는 `서비스분야`, `사용자구분`, `지원유형` 같은 official taxonomy를 주지만, 현재 서비스는 여전히 `unifiedCategory` 와 priority matcher에 크게 의존한다. 이때 `Gov24 서비스분야` 를 곧바로 `unifiedCategory` 로 저장해버리면 official 필드와 시스템 호환 분류가 섞여, 나중에 “이 값이 source가 준 것인지 우리가 파생한 것인지”를 구분하기 어려워질 수 있었음
- 해결: `docs/policy-normalization-bridge-rules.md` 에서 `compat_unified_category` 는 `SYSTEM_DERIVED` bridge 로만 다루고, Gov24 official taxonomy는 `service_taxonomies` 의 별도 official layer로 저장하는 규칙을 고정했음
- 이유: external taxonomy와 compatibility read-model은 역할이 다르다. source truth와 시스템 파생값을 같은 칼럼 의미로 섞으면 디버깅과 후속 추천 튜닝이 어려워지므로, authority를 분리한 브릿지층으로 관리하는 편이 재발 방지에 안전하다

## 128) 복지로 text/detail fallback fact를 충분한 신뢰도 구분 없이 곧바로 retrieval hard filter에 쓰면, 본문 표현 차이만으로 정책 후보가 과도하게 탈락할 수 있음
- 문제: 복지로는 structured facts보다 설명문과 상세 본문 비중이 높아서, `미취업`, `대학생`, `1인가구` 같은 표현을 규칙 기반으로 어느 정도 추출할 수는 있다. 하지만 이런 fallback fact를 곧바로 hard filter에 쓰면 source 문구 차이나 모호한 안내문 때문에 후보가 과하게 빠지거나, 반대로 잘못 남을 위험이 있었음
- 해결: `docs/policy-normalization-bridge-rules.md` 에서 fallback fact는 저장은 허용하되 초기 hard filter는 `AGE` 만 허용하고, 나머지 `INCOME/EMPLOYMENT/EDUCATION/HOUSEHOLD/SPECIAL_GROUP` 은 `RULE_DERIVED` + confidence 기반 보조 signal로만 쓰도록 제한했음
- 이유: retrieval의 false negative는 추천 품질 저하를 넘어 “사용자가 받아야 할 정책이 아예 안 보이는” 문제로 이어진다. 복지로 text fallback은 유용하지만 source 문구 편차가 크므로, 초기에는 보수적으로 soft signal로만 소비하는 편이 재발 방지에 안전하다

## 129) local Docker 앱 기준 live collect 검증은 `docker compose up app` 만으로는 충분하지 않고, split-account DB 계정과 prod profile secret이 모두 맞아야 의미 있는 적재 스냅샷이 나온다
- 문제: 실제 DB 적재 검증을 다시 하려 했을 때 local Docker 앱은 계정 drift와 빈 `AES_SECRET_KEY` 때문에 부팅/회원가입이 코드와 무관하게 막혔고, `.env` 를 쓰는 Compose 앱은 shell override만으로는 필요한 secret이 기대한 방식으로 반영되지 않아 live collect 검증 루프 자체가 흐려질 수 있었음
- 해결: 먼저 `deploy/mysql/reconcile-local-runtime-db-accounts.sh` 로 local MySQL split-account 계정을 복구한 뒤, prod profile `bootRun` 을 explicit env(`DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, `AES_SECRET_KEY`, dummy `OPENAI_API_KEY`) 기준으로 직접 띄워 `/actuator/health` 와 admin collect를 검증 기준으로 삼았음
- 이유: 실제 적재 검증의 목적은 “현재 수집/정규화 코드가 어떤 row를 만들었는지”를 보는 것이다. Compose/env 문제로 앱 자체가 다른 이유로 실패하면 정규화 판단과 환경 문제를 구분할 수 없으므로, live collect 검증 경로는 split-account와 prod-profile secret을 명시한 known-good boot 경로로 고정하는 편이 재발 방지에 안전하다

## 130) `고용24 채용정보` 나 `마이홈 공공주택 모집공고/단지/대기현황` 같은 listing형 source를 곧바로 `welfare_services` 에 flatten 하면 정책 row grain과 추천 의미가 같이 깨질 수 있음
- 문제: 신규 source 확장 논의를 실제 공개 source 기준으로 다시 보니, Work24 `채용정보/채용행사/공채속보` 와 MyHome `공공주택 모집공고/단지정보/예비입주자 대기현황` 은 “지원 제도 1건” 이 아니라 빠르게 변하는 listing/inventory 또는 상태 feed 성격이 강했다. 이를 기존 정책 row 테이블에 그대로 넣으면 북마크, CTR, 추천 후보, 정책 상세의 의미가 뒤섞일 수 있었음
- 해결: [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md) 에 source를 `정책형 / listing형 / reference형` 으로 먼저 분기하는 규칙을 추가하고, listing형 source는 `welfare_services` 가 아니라 별도 도메인(`job_listings`, `housing_recruitments`, `housing_complexes`, `housing_waitlist_stats`) 후보로 분리하는 방향을 고정했음
- 이유: source 확장성의 핵심은 mapper 추가보다 row grain 보존이다. 정책형 row와 listing row를 같은 canonical에 억지로 밀어 넣으면 단기적으로는 빨라 보여도, 추천/북마크/로그 의미가 무너져 이후 비용이 더 커진다

## 131) 실제 DB에서 복지로 주거/장학/일자리 title이 다수 `기타` 로 남는 상태라면, compat 분류를 title keyword 보정만으로 버티는 방식은 source가 늘수록 빠르게 한계에 부딪힌다
- 문제: 2026-04-29 실제 DB 스냅샷에서 `대전 청년 월세지원`, `대학생 학자금 대출이자 지원(경기도)`, `취업청년정착수당` 같은 `BOKJIRO_LOCAL` row가 다수 `unified_category=기타` 로 남아 있었다. 이 상태에서 신규 source를 더 붙이면 category 누수를 keyword rule 몇 개로 계속 메우게 되어 분류 기준이 다시 ad-hoc 해질 위험이 컸음
- 해결: 실제 DB 사례를 [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)에 남기고, `official taxonomy + compat_unified_category bridge + service_facts` 구조를 우선 강화하며, listing형 source는 아예 정책 canonical 밖으로 분리하는 쪽으로 다음 작업을 정리했음
- 이유: 실데이터에서 이미 누수가 보이는 상태면 rule patch를 더 쌓기보다 분류 계층 자체를 분리하는 편이 맞다. source가 늘수록 `기타` 예외처리 비용이 커지므로, 지금처럼 유저가 없는 시점에는 구조를 바로잡는 쪽이 장기적으로 안전하다

## 132) 기존 `ServiceTag.KEYWORD` 는 display keyword와 규칙 기반 constraint token(`COND_AGE_*`, `COND_INCOME_*`) 이 섞여 있어, canonical taxonomy term으로 그대로 옮기면 분류 계층이 오염될 수 있음
- 문제: `tagsFromYouth`, `tagsFromBokjiro*` 는 기존 검색/추천 보조를 위해 `KEYWORD` 안에 일반 키워드와 규칙 기반 constraint token을 함께 넣고 있다. 새 canonical aggregate 초안에서 이 값을 그대로 `taxonomyTerms` 로 재활용하면 `월세`, `청년주거` 같은 분류어와 `COND_AGE_MAX_34` 같은 eligibility token이 같은 계층으로 섞일 수 있었음
- 해결: `NormalizedPolicyAggregate` 매핑에서는 taxonomy term을 raw source 필드(`plcyKywdNm`, `lifeArray`, `intrsThemaArray`, `trgterIndvdlArray`) 기준으로 따로 만들고, 구조화 조건은 `facts` 로 분리해 저장하도록 `WelfareServiceMapper` 초안을 정리했음
- 이유: taxonomy와 facts를 mapper 단계에서부터 분리하지 않으면, 이후 `service_taxonomy_terms` / `service_facts` sidecar 저장 시점에 데이터 의미를 다시 추론해야 한다. 현재처럼 source field 기준으로 분리해 두는 편이 점진 이행과 후속 추천 read-model 전환에서 재발 방지에 안전하다

## 133) `NormalizedPolicyAggregate` 를 처음부터 JPA sidecar entity나 migration 스키마에 직접 맞춰 버리면, DTO 초안 단계부터 persistence 세부구조에 잠겨 점진 이행이 어려워질 수 있음
- 문제: 이번 단계에서 `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 실테이블이 아직 없는데 aggregate를 곧바로 entity 집합으로 만들면, collect mapper 리팩터링이 곧 DB migration 선행조건이 되고 `CollectItemSaver` 병행 연결 같은 작은 단계 진행이 막힐 수 있었음
- 해결: `NormalizedPolicyAggregate` 는 record 기반의 persistence-agnostic 내부 DTO로 두고, `WelfareServiceMapper` 에서 source별 canonical 값만 먼저 채우도록 초안을 분리했음. 실제 sidecar entity/SQL 연결은 다음 task로 남겼음
- 이유: 지금 목표는 big-bang 저장 전환이 아니라 source별 canonical 계약을 먼저 고정하는 것이다. DTO 계층을 저장 구현과 느슨하게 두어야 기존 `WelfareService` write path를 유지하면서도 mapper, saver, migration 을 작은 task로 나눠 이행할 수 있어 재발 방지에 안전하다

## 134) source adapter 가 canonical aggregate를 만들지 않고 saver 내부에서만 재구성하게 두면, 이후 sidecar 저장/상세 보강이 추가될 때 collect 경계가 다시 legacy entity 중심으로 굳어질 수 있음
- 문제: `NormalizedPolicyAggregate` 초안을 만든 직후에도 adapter가 여전히 raw DTO만 saver에 넘기고 saver가 legacy mapper만 호출하는 상태를 유지하면, canonical 구조는 테스트용 DTO로만 남고 실제 수집 경계는 계속 `WelfareService` 중심으로 굳을 수 있었음
- 해결: `Youth/BokjiroCentral/BokjiroLocal` source adapter가 mapper에서 만든 aggregate를 saver overload까지 같이 넘기도록 바꾸고, saver에서는 aggregate source identity가 실제 item과 일치하는지 검증하도록 정리했음
- 이유: sidecar 저장으로 가기 전 단계라도 “canonical aggregate는 collect 경계에서 이미 만들어진다”는 사실을 코드 경로에 남겨야 이후 `service_taxonomies/service_facts` 저장과 추천 이행이 작은 task로 이어진다. 그렇지 않으면 다음 단계에서 다시 adapter/saver 양쪽을 한 번에 뜯어야 해 재발 위험이 커진다

## 135) 복지로 list 수집만으로 canonical `detail/facts` 를 완결시키려 하면, 실제 상세 payload가 나중에 도착하는 구조와 충돌해 중복 규칙이나 잘못된 덮어쓰기가 생길 수 있음
- 문제: 이번 task에서 `BokjiroCentral/Local` adapter는 canonical aggregate를 병행 전달하게 되었지만, list 수집 시점에는 상세 payload가 없어 `detail` 대부분이 `null` 또는 list fallback 값만 가진다. 이 상태를 완결 canonical로 간주하면 나중에 `BokjiroDetailCollectService` 가 들어올 때 어느 필드를 authoritative 하게 덮어쓸지 다시 모호해질 수 있었음
- 해결: adapter 경로에서는 일단 `null detail payload` 기반 aggregate를 넘기고, 다음 작업을 `BokjiroDetailCollectService / detail refresh 경로에서 NormalizedPolicyAggregate detail/facts 후속 보강 연결` 로 분리해 기록했음
- 이유: 복지로는 list와 detail의 수집 cadence와 정보 밀도가 다르다. list 수집 경로는 canonical 초안/identity 확보까지, detail 수집 경로는 세부 facts 보강까지 담당을 나누는 편이 점진 이행과 덮어쓰기 규칙 관리에 안전하다

## 136) 복지로 상세 수집이 raw payload를 직접 `WelfareServiceDetail` / 서비스 fallback으로 저장하면, list collect가 만든 canonical 규칙과 detail collect 규칙이 쉽게 drift 날 수 있음
- 문제: `NormalizedPolicyAggregate` 를 collect flow에 태운 뒤에도 `BokjiroDetailCollectService` 가 여전히 `DetailPayload` 원문을 직접 `WelfareServiceDetail` 과 `applyDetailFallbacks` 에 넣고 있으면, list collect는 mapper canonical 규칙을 따르고 detail collect는 service 내부 규칙을 따르는 이중 경로가 남게 된다. 이렇게 되면 `AGE`, `APPLY_END_DATE`, `onlineApply` 같은 보강값이 mapper와 service에서 서로 다르게 해석될 수 있었음
- 해결: `WelfareServiceMapper.toNormalizedBokjiroDetail(service, detailPayload)` 를 추가하고, `BokjiroDetailCollectService` 가 aggregate `detail` 로 detail entity를 만들고 aggregate `facts` 로 서비스 fallback을 적용하도록 경로를 통일했음
- 이유: canonical 전환 중에는 “무엇을 source truth로 해석하는지”가 한 곳에 모여 있어야 한다. 복지로 상세처럼 list/detail cadence가 다른 source일수록 mapper를 단일 해석 경계로 두는 편이 후속 sidecar 저장과 추천 read-model 전환에서 재발 방지에 안전하다

## 137) 복지로 list aggregate 와 detail aggregate 가 같은 semantic fact를 다른 `fact_code` 로 내보내면, future `service_facts` upsert 에서 detail 이 list fallback 을 대체하지 못하고 중복 row가 누적될 수 있음
- 문제: 현재 canonical 초안에서는 복지로 list 쪽에 `TEXT_AGE`, detail 쪽에 `DETAIL_TEXT_AGE` 같은 phase-specific 코드가 남아 있다. 이 상태로 `service_facts` 를 저장하면 같은 `AGE` 슬롯이어도 서로 다른 row로 인식되어, detail collect 가 list fallback 을 교체해야 하는 경우에도 단순 중복 insert 로 끝날 위험이 있었음
- 해결: [policy-normalization-fact-merge-rules.md](./policy-normalization-fact-merge-rules.md) 를 추가해 `fact_code` 와 별도로 `fact_merge_key` 를 두고, `BK_AGE_ELIGIBILITY`, `BK_APPLY_END_DATE` 같은 stable logical key 기준으로 merge/upsert 하도록 규칙을 고정했음. 이어서 next task로 mapper fact code 를 stable merge key 체계로 정리하도록 작업 추적에 추가했음
- 이유: list/detail cadence 가 다른 source에서는 “무슨 fact인가”와 “어느 phase에서 나왔는가”를 같은 코드값에 섞으면 deterministic upsert 를 만들기 어렵다. logical merge key 를 먼저 고정해야 migration SQL, saver, read-model 이 같은 fact slot 개념을 공유할 수 있어 재발 방지에 안전하다

## 138) `fact_merge_key` 가 문서에만 있고 aggregate 계약에는 없으면, mapper 테스트는 통과해도 future sidecar saver 단계에서 같은 semantic fact slot 을 안정적으로 단언할 수 없음
- 문제: `policy-normalization-fact-merge-rules.md` 로 merge key 규칙을 먼저 고정했더라도, `NormalizedPolicyAggregate.Fact` 가 여전히 `factCode` 만 들고 있으면 현재 mapper 출력과 future `service_facts` saver 사이에 merge slot 정보가 사라진다. 이 상태에서는 list/detail 이 같은 semantic fact 를 내보내는지 테스트 레벨에서 고정할 수 없고, sidecar 저장 단계에서 다시 phase-specific 코드에 의존할 위험이 있었음
- 해결: `NormalizedPolicyAggregate.Fact` 에 `factMergeKey` 필드를 추가하고, `WelfareServiceMapper` 가 복지로 list/detail facts 에 공통 `BOKJIRO_RULE_AGE` / `BOKJIRO_RULE_APPLY_END_DATE` 와 stable `BK_AGE_ELIGIBILITY` / `BK_APPLY_END_DATE` merge key 를 함께 채우도록 정리했음. `NormalizedPolicyAggregateTest` 로 youth/복지로 facts 의 merge key 도 같이 검증하게 바꿨음
- 이유: canonical aggregate 는 future persistence 계약의 가장 가까운 전단계다. merge slot 정보가 aggregate부터 살아 있어야 migration SQL, saver upsert, retrieval/read-model 이 같은 semantic fact 개념을 일관되게 공유할 수 있어 재발 방지에 안전하다

## 139) merge 규칙이 문서와 mapper에만 있고 executable merge utility가 없으면, future `service_facts` saver 구현 시 precedence 로직이 다시 분산되어 drift 날 수 있음
- 문제: `fact_merge_key` 와 복지로 공통 fact code를 맞춘 뒤에도 실제 `service_facts` saver 가 아직 없으면, 나중에 저장 경로를 만들 때 `authority -> confidence -> sourceField` 우선순위를 각 saver/repository 에서 다시 손으로 구현하게 될 수 있다. 그러면 문서와 현재 테스트가 있어도 실제 upsert 동작이 미묘하게 달라질 위험이 있었음
- 해결: `NormalizedFactMergeSupport` 를 추가해 `fact_merge_key` 기준 merge/upsert precedence 를 코드 utility로 먼저 고정했고, `NormalizedFactMergeSupportTest` 에 `list -> detail overwrite`, `detail -> list no-op`, `set-like union` 케이스를 추가해 future saver 가 그대로 재사용할 계약을 마련했음
- 이유: merge 규칙은 “문서 + mapper + persistence” 3층에서 동일해야 한다. 저장 경로가 아직 없어도 precedence 자체는 executable utility와 테스트로 먼저 한 곳에 고정해 둬야 이후 sidecar saver 가 같은 계약을 재사용하며 구현돼 재발 방지에 안전하다

## 140) sidecar writer 훅이 collect list/detail 경로에 연결되지 않으면, 나중에 DB writer 를 붙여도 일부 aggregate 는 계속 legacy entity write path 에서만 끝나 canonical sidecar 가 부분 적재될 수 있음
- 문제: `NormalizedPolicyAggregate`, `factMergeKey`, `NormalizedFactMergeSupport` 를 준비해도 실제 collect 경로가 sidecar writer 를 호출하지 않으면, future `service_taxonomies/service_facts` 저장을 붙일 때 adapter/list/detail 경로 중 일부만 새 writer 를 타고 나머지는 계속 `WelfareService` / `WelfareServiceDetail` write 에서 끝날 수 있다. 그러면 source별 canonical 적재 커버리지가 다시 경로마다 갈라질 위험이 있었음
- 해결: `NormalizedPolicySidecarWriter` 인터페이스와 `DeferredNormalizedPolicySidecarWriter` 기본 구현을 추가하고, `CollectItemSaver` 와 `BokjiroDetailCollectService` 가 aggregate 저장 경로에서 모두 writer 를 호출하도록 연결했음. 테스트에서도 youth list path 와 bokjiro detail path 모두 writer 호출을 검증하도록 고정했음
- 이유: canonical 전환은 mapper 규칙만 맞춘다고 끝나지 않고, collect 실행 경로 전체가 같은 저장 훅을 지나야 한다. sidecar DB writer 가 아직 없어도 호출 지점을 먼저 고정해야 이후 persistence 구현을 한 곳에 꽂을 수 있어 재발 방지에 안전하다

## 141) `service_taxonomy_terms` 에서 `term_code` 를 nullable 로 두고 unique key 만 걸면, MySQL 이 `NULL` 중복을 허용해 term dedupe 계약이 깨질 수 있음
- 문제: `service_taxonomy_terms` 는 `(service_id, term_group, term_code, term_label, authority)` 기준으로 dedupe 해야 하는데, MySQL unique key 는 nullable 컬럼의 `NULL` 중복을 막지 않는다. 코드가 없는 taxonomy term 을 그대로 `NULL` 로 두면 SQL draft 상 unique key 가 있어도 동일 term 이 중복 적재될 위험이 있었음
- 해결: `V2026_04_30_01__create_policy_sidecars.sql` draft 와 `policy-normalization-schema-draft.md` 에서 코드가 없는 term 은 `term_code=''` 로 normalize 하도록 고정했고, `db-migration.md` 에도 이 규칙을 같이 기록했음
- 이유: canonical sidecar 는 term dedupe 를 DB 제약으로도 최대한 보조해야 한다. MySQL nullable unique semantics 를 초안 단계에서 반영하지 않으면, 나중에 persistence writer/backfill 에서 중복 정리 로직이 불필요하게 복잡해져 재발 방지에 불리하다

## 142) official codebook 이 아직 없는 단계에서 `YOUTH_MID` 나 `GOV24_*` 코드를 임의 생성해 seed 하면, 초기 backfill 은 돌아가도 이후 공식 import 와 코드 호환이 깨질 수 있음
- 문제: `normalization_code_sets / normalization_codes` seed/backfill draft 를 만들 때 `service_taxonomies` summary backfill 을 빨리 끝내려고 `YOUTH_MID` 나 `GOV24_SERVICE_FIELD` 코드를 내부 규칙으로 임의 생성해 넣어버리면, 나중에 온통청년/Gov24 공식 코드북을 가져올 때 같은 라벨이 다른 code 로 중복되거나 기존 summary row 와 호환되지 않을 위험이 있었음
- 해결: `V2026_04_30_02__seed_policy_normalization_codes.sql` draft 에서는 `SYSTEM_COMPAT_UNIFIED_CATEGORY`, `YOUTH_MAJOR` 만 대표 code seed 로 넣고, `YOUTH_MID`, `GOV24_*`, `GOV24_SUPPORT_CONDITION` 은 metadata set만 먼저 생성한 뒤 공식 code import/backfill SQL을 후속 task로 분리했음
- 이유: canonical code table 은 한 번 잘못 seed 하면 이후 backfill/persistence/read-model 전부가 그 코드를 따라가게 된다. 공식 근거가 없는 계층은 metadata까지만 먼저 열고 code 값은 공식 source 확보 후 넣는 편이 호환성과 재발 방지에 안전하다

## 143) 같은 온통청년 공개 코드정의서 안에서도 어떤 집합은 stable code 값이 있고(`jobCd`, `schoolCd` 등), 어떤 집합은 라벨/정렬만 공개(`정책중분류`)되어 있어 import 전략을 한 파일로 뭉개면 다시 임의 code 생성이 섞일 수 있음
- 문제: 이번에 official code import draft 를 이어서 만들 때, `jobCd=0013003`, `schoolCd=0049005` 처럼 실제 code 가 공개된 집합과 `정책중분류=취업/재직자/...` 처럼 번호+라벨만 보이는 집합을 같은 방식으로 `normalization_codes` 에 넣으려 하면 결국 `YOUTH_MID` 에 내부 surrogate code 를 급하게 발급하게 될 위험이 있었음
- 해결: `V2026_04_30_03__seed_policy_official_code_subsets.sql` 에서는 stable code 가 확인된 온통청년 집합과 representative `GOV24_SUPPORT_CONDITION` 만 `normalization_codes` 로 seed 하고, `YOUTH_MID` 는 `service_taxonomy_terms.term_code=''` label-only backfill 로만 처리하도록 분리했음
- 이유: source granularity 가 다른 집합을 한 import 전략으로 묶으면 공식 코드와 임의 코드가 같은 테이블에서 구분 없이 섞인다. 지금처럼 code-bearing subset 과 label-only subset 을 분리해야 후속 `YOUTH_MID` stable code 정책 결정이 독립적으로 가능하고 재발 방지에 안전하다

## 144) `YOUTH.category_sub` 를 raw string 그대로 `service_taxonomy_terms` 에 적재하면, multi-value와 non-official variant가 official 중분류 term과 한 row에 섞여 canonical taxonomy가 오염될 수 있음
- 문제: 로컬 DB를 다시 확인해 보니 `YOUTH.category_sub` 는 `취업`, `재직자` 같은 단일 official label만 있는 게 아니라 `취업,재직자`, `취업,창업`, `온·오프라인교육`, `문화활동 및 생활지원` 같은 multi-value/variant 문자열도 함께 들어 있었다. 이 값을 split 없이 그대로 `YOUTH_MID` term으로 적재하면 official 중분류 집합과 raw source noise가 같은 계층에 섞일 위험이 있었음
- 해결: `V2026_04_30_03__seed_policy_official_code_subsets.sql` 의 `YOUTH_MID` backfill 을 `split + trim + exact official label filter` 방식으로 바꾸고, 공개 시트의 17개 official label과 일치하는 token만 `service_taxonomy_terms(term_code='')` 에 적재하도록 수정했음. `온·오프라인교육`, `문화활동 및 생활지원` 같은 variant는 alias normalization 후속 task로 분리했음
- 이유: `YOUTH_MID` 는 아직 stable code 가 없기 때문에 term_label 자체가 canonical key 역할을 일부 대신한다. 따라서 raw combo string 을 그대로 넣지 말고, 최소한 official label inventory와 exact match 하는 값만 먼저 넣어야 taxonomy 정합성과 재발 방지에 안전하다

## 145) 공개 문서에 `srchPolyBizSecd=003002001,003002002` 예시가 보이더라도, 상세 metadata inventory 를 비로그인 상태에서 가져올 수 없으면 그 두 값만으로 `YOUTH_MID` stable code 체계를 확정하면 안 됨
- 문제: 온통청년 공개 API 문서 HTML에서는 `srchPolyBizSecd=003002001,003002002` 예시가 노출되지만, 실제 파라미터/응답 메타데이터를 주는 `/sur/link/openApiIntro/46`, `/sur/link/openInfoChcApi` 는 비로그인 상태에서 모두 `Unauthorized` 를 반환했다. 이 상태에서 보이는 두 코드만 근거로 `YOUTH_MID` 전체 stable code를 역추론해 seed 하면, 나머지 중분류와의 체계가 뒤틀릴 위험이 있었음
- 해결: 이번 단계에서는 `YOUTH_MID` stable code import 를 보류하고 `service_taxonomy_terms(term_code='')` label-only 전략을 유지하기로 정책을 고정했음. 후속 task는 로그인 가능한 testbed/live payload 에서 `srchPolyBizSecd` 전체 inventory 를 먼저 수집한 뒤 stable code mapping SQL 초안을 쓰는 것으로 다시 쪼갰음
- 이유: partial example 과 전체 inventory 는 다르다. 공개 페이지 예시 몇 개만으로 코드 체계를 미리 확정하면 이후 authenticated source 에서 실제 inventory 가 드러났을 때 기존 seed/backfill 과 충돌하기 쉬워 재발 방지에 불리하다

## 146) `온·오프라인교육`, `문화활동 및 생활지원` 같은 non-official `YOUTH_MID` variant를 성급히 official 단일 라벨로 접어버리면, taxonomy 의미 손실이나 잘못된 우선순위 브릿지가 생길 수 있음
- 문제: 로컬 DB split 결과를 보면 `온·오프라인교육` 11건, `문화활동 및 생활지원` 66건이 존재한다. 전자는 `온라인교육` 과 유사하지만 offline 범위를 포함할 수 있고, 후자는 `문화활동` 과 생활지원 축이 결합된 composite 표현일 수 있어 하나의 official 중분류로 단정하기 어렵다
- 해결: [policy-normalization-youth-mid-alias-rules.md](./policy-normalization-youth-mid-alias-rules.md) 에서 alias 처리 기준을 따로 고정하고, exact official token만 canonical `YOUTH_MID` 로 적재하며 non-official variant는 현재 단계에서 skip 하도록 명시했음. 별도 보존이 필요하면 future sidecar 에 `YOUTH_MID_RAW_ALIAS` 같은 term group을 둘지 후속 task로 분리했음
- 이유: stable code 가 없는 상태에서는 `term_label` 자체가 canonical key 일부 역할을 대신한다. 애매한 alias를 섣불리 official 단일 라벨로 접으면 이후 stable code import, 추천 브릿지, 분석 집계가 모두 왜곡될 수 있어 재발 방지에 불리하다

## 147) skipped `YOUTH_MID` alias를 raw payload에만 남기면, canonical sidecar 기준의 디버깅/backfill/read-model 경로가 다시 raw string 재파싱에 의존하게 됨
- 문제: `온·오프라인교육`, `문화활동 및 생활지원` 을 canonical `YOUTH_MID` 에 넣지 않는 것은 맞지만, 이 값을 `welfare_services.category_sub` / `raw_api_payloads` 에만 두면 나중에 sidecar 기준 분석, backfill, read-model 검증 때마다 raw string 을 다시 파싱해야 한다. 그러면 official exact token과 skipped alias의 구분 기준이 writer/backfill 마다 다시 흩어질 위험이 있었다
- 해결: skipped alias는 canonical `YOUTH_MID` 에 넣지 않되, future sidecar 에 `service_taxonomy_terms(term_group='YOUTH_MID_RAW_ALIAS', code_set_key=NULL, term_code='', term_label=<raw alias>, authority='OFFICIAL', source_field='category_sub')` 로 별도 보존하기로 정책을 고정했음. canonical taxonomy summary / recommendation read-model 은 이 term group을 기본적으로 읽지 않도록 같이 명시했음
- 이유: canonical taxonomy를 오염시키지 않으면서도 source가 실제로 준 비정규 label을 잃지 않는 것이 중요하다. raw alias를 별도 bucket으로 남겨두면 나중에 stable code inventory가 확보되었을 때 재매핑이 쉬워지고, collect writer/backfill/read-model 이 같은 기준을 재사용할 수 있어 재발 방지에 유리하다

## 148) `YOUTH_MID` raw alias bucket만 분리하고 summary `youthMid` 필드에 raw combo/alias 문자열을 그대로 남기면, sidecar term은 정규화돼도 summary read-model 이 다시 canonical taxonomy를 오염시킬 수 있음
- 문제: `YOUTH_MID_RAW_ALIAS` 를 `service_taxonomy_terms` 에 따로 보존하더라도, `taxonomy.youthMid` 나 `service_taxonomies.youth_mid_label` 이 기존처럼 raw `category_sub` 문자열(`취업,재직자`, `온·오프라인교육`)을 그대로 유지하면 summary/read-model 이 계속 비정규 값에 묶이게 된다. 그러면 canonical term과 summary가 서로 다른 기준을 보게 되어 후속 추천 브릿지와 검증 쿼리가 다시 흔들릴 위험이 있었다
- 해결: `WelfareServiceMapper.toNormalizedYouth()` 와 `V2026_04_30_02__seed_policy_normalization_codes.sql` 모두 `YOUTH_MID` summary 는 exact official 단일 token일 때만 채우고, comma 조합이나 raw alias가 섞이면 `NULL` 로 두도록 규칙을 맞췄다. 동시에 `DeferredNormalizedPolicySidecarWriter` 에 raw alias bucket이 있을 때 summary `youthMid` 가 비어 있어야 한다는 invariant 검증을 추가했다
- 이유: canonical taxonomy는 summary 와 repeated term row가 같은 정합성 규칙을 공유해야 한다. raw alias bucket을 도입한 뒤에도 summary 를 그대로 두면 정규화 이득이 절반만 남으므로, collect aggregate / backfill SQL / writer invariant 를 같은 기준으로 고정해 재발을 막는 편이 안전하다

## 149) `detail` aggregate 의 `taxonomyTerms` 가 비어 있다는 이유만으로 source별 known term group 을 전부 지우면, 복지로 list 수집이 적재한 taxonomy가 detail refresh 한 번에 사라질 수 있음
- 문제: 실제 JDBC sidecar writer 로 바꾸면서 source별 term group refresh 삭제를 추가했는데, 이 로직을 `BokjiroDetail` aggregate 에도 그대로 적용하면 detail aggregate 는 `taxonomyTerms` 가 비어 있으므로 기존 `LIFE_STAGE`, `INTEREST_THEME`, `TARGET_GROUP` row를 통째로 삭제해 버릴 수 있었다
- 해결: `DeferredNormalizedPolicySidecarWriter` 는 `taxonomyTerms` 가 비어 있는 aggregate 에 대해서는 taxonomy term refresh 자체를 건너뛰도록 했고, `DeferredNormalizedPolicySidecarWriterTest` 에서 detail aggregate(`BOKJIRO_CENTRAL`, empty `taxonomyTerms`)가 summary/facts upsert 는 수행하되 `DELETE FROM service_taxonomy_terms` 는 호출하지 않는 계약을 추가했다
- 이유: list collect 와 detail collect 는 canonical aggregate 의 역할이 다르다. detail 경로는 facts/detail enrichment 전용이고 list taxonomy를 authoritative 하게 대체하지 않으므로, 같은 sourceType 이라도 “term payload 가 비어 있는 detail aggregate” 는 delete semantics 를 다르게 가져가야 기존 수집 결과를 보존할 수 있어 재발 방지에 안전하다

## 150) actual sidecar writer 를 도입해도 아직 local/runtime schema 에 sidecar 테이블이 없으면 collect 호출 시 SQL 예외로 전체 수집이 깨질 수 있음
- 문제: `DeferredNormalizedPolicySidecarWriter` 를 실제 JDBC upsert writer 로 바꾼 뒤에도 sidecar 스키마는 아직 `schema.sql` 과 정식 `db/migration/` 에 들어가지 않았다. 이 상태에서 테이블 존재 여부를 확인하지 않고 곧바로 `service_taxonomies/service_facts` 에 쓰면, 현재 로컬/운영 DB 상당수는 해당 테이블이 없어 collect 경로 전체가 SQL 예외로 중단될 위험이 있었다
- 해결: writer 시작 시 `information_schema.tables` 를 조회해 `normalization_code_sets`, `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 네 테이블이 모두 있을 때만 actual upsert 를 수행하고, 없으면 debug 로그만 남기고 안전하게 skip 하도록 변경했다
- 이유: canonical sidecar 전환은 collect 코드와 DB rollout 이 완전히 동시에 끝나지 않는다. rollout 이전 단계에서 writer 가 no-op fallback 을 유지해야 현재 수집 흐름을 지키면서도, sidecar 테이블이 준비된 환경에서는 같은 코드가 즉시 actual persistence 로 전환될 수 있어 점진 이행과 재발 방지에 모두 유리하다

## 151) secondary datasource용 `NamedParameterJdbcTemplate` 만 수동 등록한 상태에서 primary named template를 명시하지 않으면, actual sidecar writer 가 켜지는 시점에 Spring context 가 `NoUniqueBeanDefinitionException` 으로 부팅 실패할 수 있음
- 문제: `DeferredNormalizedPolicySidecarWriter` 가 실제 JDBC upsert writer 로 전환되면서 `NamedParameterJdbcTemplate` 를 주입받기 시작했는데, 애플리케이션에는 `appPiiReadWriteNamedParameterJdbcTemplate`, `notificationPiiReadNamedParameterJdbcTemplate` 두 개만 명시 등록돼 있었다. 이 상태에서는 Spring Boot 기본 primary named template auto-config가 더 이상 유일 후보가 아니어서, local integration smoke에서 context 가 부팅 단계에서 실패했다
- 해결: `PrimaryDataSourceConfig` 에 primary datasource 기반 `primaryNamedParameterJdbcTemplate` bean을 명시적으로 추가해, collect sidecar writer 가 항상 core schema datasource를 사용하도록 고정했다
- 이유: secondary datasource를 늘리는 순간 “기본 named JDBC bean이 자동으로 하나 있을 것”이라는 가정은 깨질 수 있다. primary writer 가 어떤 datasource를 써야 하는지 코드로 못 박아야 actual collect path smoke와 runtime이 같은 wiring을 공유해 재발 방지에 안전하다

## 152) `fact_merge_key` 가 같고 authority/confidence/sourceField 우선순위도 같은 refresh fact는 incoming 값으로 덮어써야 하는데, 기존 merge 규칙이 동률에서 old value를 유지해 stale sidecar facts가 남을 수 있었음
- 문제: local MySQL smoke에서 같은 `source_id` 를 두 번 저장해 보니 `NormalizedFactMergeSupport` 가 동일 우선순위 tie에서 기존 fact를 유지하고 있어, `YOUTH_AGE_ELIGIBILITY`, `YOUTH_INCOME_MAX`, `YOUTH_APPLY_END_DATE` 같은 fact가 refresh 후에도 이전 값에 머무를 수 있었다
- 해결: `NormalizedFactMergeSupport` 의 tie-breaker를 `incoming wins on exact precedence tie` 로 바꾸고, `NormalizedFactMergeSupportTest.merge_overwritesExistingWhenRefreshHasSamePrecedence` 로 같은 merge key의 refresh overwrite 계약을 추가했다
- 이유: list/detail merge에서는 stronger source 우선순위가 필요하지만, 같은 source의 재수집 refresh에서는 최신 aggregate가 authoritative 해야 한다. 동률이면 새 값으로 덮어쓰도록 고정해야 sidecar가 stale해지지 않아 재발 방지에 안전하다

## 153) 같은 `service_id` 에 대해 tag replace를 연속 수행할 때 delete가 flush 되기 전에 insert batch가 들어가면 `service_tags.uq_st` 충돌이 날 수 있음
- 문제: local sidecar smoke에서 같은 청년정책 row를 두 번 저장했을 때 `service_tags` 교체 경로가 `deleteByServiceId -> saveAll` 로 이어지면서, delete가 DB에 먼저 확정되기 전에 같은 `(service_id, tag_type, tag_value)` insert가 들어가 `Duplicate entry ... for key 'service_tags.uq_st'` 가 발생했다
- 해결: `CollectItemSaver.replaceTags()` 에서 `tagRepository.deleteByServiceId(service.getId())` 직후 `tagRepository.flush()` 를 호출해 delete를 먼저 DB에 반영한 뒤 새 tag 집합을 저장하도록 바꿨다
- 이유: tag refresh는 sidecar smoke처럼 같은 row를 같은 테스트/수집 창에서 연속 갱신할 때 바로 드러난다. replace semantics를 기대하는 경로에서는 delete와 insert의 flush 순서를 명시적으로 고정해야 unique constraint 재발을 막을 수 있다

## 154) 복지로 detail refresh smoke를 작은 `maxCalls` 로 재현할 때는 실제 repository 전체 scan 대신 isolated target 집합을 주입하지 않으면 local source merge 경로를 안정적으로 고정하기 어렵다
- 문제: `collectBokjiroDetailsRefreshResult(maxCalls)` 는 central/local budget 을 함께 계산하고 repository 전체 `findBySourceType(...)` 결과를 순회한다. 로컬 DB에는 이미 복지로 row가 많이 적재돼 있어, sidecar merge smoke에서 특정 1건의 `BOKJIRO_LOCAL` list/detail 경로만 검증하려고 해도 전체 적재 데이터 순서와 `maxCallsPerApiPerRun` 분배에 따라 다른 row가 먼저 선택될 수 있었다
- 해결: integration smoke에서는 real DB에 list aggregate를 먼저 적재한 뒤, 테스트 안에서 isolated `BokjiroDetailCollectService` 를 새로 구성해 mocked `WelfareServiceRepository.findBySourceType(...)` 가 대상 local row만 반환하도록 제한했다. 이 smoke는 canonical sidecar merge 계약만 검증하고, detached entity에 기대는 legacy `welfare_services` fallback 필드 값까지 assertion 범위에 넣지 않도록 좁혔다
- 이유: 이번 단계의 목적은 운영 batch 전체가 아니라 `service_taxonomy_terms` 보존과 `service_facts` merge key overwrite가 actual JDBC sidecar writer에 연결되는지 확인하는 것이다. 전체 repository scan과 batch budget까지 동시에 테스트에 끌고 오면 sidecar contract 회귀와 unrelated source ordering이 섞여 재현성이 떨어지므로, isolated target 집합으로 contract를 먼저 고정하는 편이 재발 방지에 안전하다

## 155) 복지로 detail refresh budget 을 `central 먼저, 남으면 local` 로 고정하면 low `maxCalls` 에서 local source 가 구조적으로 0 budget이 되어 sidecar/backfill 검증과 실제 local 상세 갱신이 계속 굶을 수 있음
- 문제: 기존 `collectBokjiroDetailsResult(maxCalls, ...)` 는 `centralBudget = min(cap, maxCalls)`, `localBudget = min(cap, maxCalls - centralBudget)` 구조였다. 이 규칙에서는 `maxCalls=1`, `maxCalls=2` 같은 low budget에서 중앙 source target이 하나라도 존재하면 local은 항상 `0` budget이 되어, 실제 로컬 상세가 더 많이 쌓여 있어도 refresh와 backfill이 계속 미뤄질 수 있었다
- 해결: `BokjiroDetailCollectService` 가 먼저 중앙/지자체 target backlog를 읽고, `maxCallsPerApiPerRun` cap 안에서 backlog 비율대로 `centralBudget` / `localBudget` 을 나누도록 바꿨다. 한쪽 source가 비어 있으면 다른 쪽이 전체 budget을 가져가고, regression test로 `central 없음 -> local full budget`, `local backlog 우세 -> maxCalls=1 에서 local 호출` 을 고정했다
- 이유: 복지로 detail 수집은 중앙/지자체 모두 canonical sidecar enrichment의 source다. low budget 상황에서도 한쪽 source가 구조적으로 굶지 않게 해야 local 중심 데이터셋에서 refresh/backfill과 smoke가 의미 있게 유지되므로 재발 방지에 안전하다

## 156) 복지로 detail raw payload 를 그대로 JSON 저장하면 Lombok `isEmpty()` getter 때문에 `empty=false` 가 섞여, 나중에 `DetailPayload` 로 다시 읽는 backfill/replay 경로가 깨질 수 있음
- 문제: `BokjiroDetailClient.DetailPayload` 는 `isEmpty()` convenience method 를 갖고 있어 Jackson 직렬화 시 `empty=false` 프로퍼티가 함께 저장될 수 있었다. 이후 `raw_api_payloads` 에서 이 JSON을 다시 읽어 `DetailPayload` 로 역직렬화하면, 클래스에는 `empty` 필드가 없어 `Unrecognized field "empty"` 예외가 발생해 canonical sidecar backfill/replay가 실패했다
- 해결: `DetailPayload` 에 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 붙이고 `isEmpty()` 에 `@JsonIgnore` 를 추가해, 신규 저장에서는 `empty` 가 빠지고 기존 raw JSON 에 `empty=false` 가 남아 있어도 무시하고 다시 읽을 수 있게 맞췄다. 동시에 `NormalizedPolicySidecarBackfillServiceTest` 로 detail raw replay 경로를 고정했다
- 이유: raw payload 재사용 경로는 “예전 JSON도 읽히고, 앞으로 쌓일 JSON도 깨끗해야” 안전하다. helper getter 하나 때문에 backfill이 전부 막히면 stored payload의 가치가 사라지므로, 직렬화/역직렬화 양방향 계약을 같이 잠가 재발을 막는 편이 안전하다

## 157) canonical sidecar raw replay를 기존 `CollectSource` fan-out 에 섞으면, 외부 API fetch와 stored payload replay 책임이 뒤섞여 manual collect 경로가 다시 비대해질 수 있음
- 문제: `NormalizedPolicySidecarBackfillService` 를 수동 실행 경로로 노출할 때 기존 `/api/admin/collect/{sourceKey}` 와 `CollectSource` enum에 `BOKJIRO_SIDECARS_BACKFILL` 같은 값을 추가하면, 외부 API를 다시 호출하는 collect orchestration과 이미 저장된 `raw_api_payloads` 를 replay 하는 maintenance 경로가 같은 fan-out 체계에 섞이게 된다. 이 방식은 새 replay/backfill 기능이 늘 때마다 `CollectSource` 와 `CollectService` 책임을 다시 키울 위험이 있었다
- 해결: backfill은 `POST /api/admin/collect/bokjiro-sidecars-backfill` exact path로 별도 노출하고, `scope=all|list|detail` 만 받아 `NormalizedPolicySidecarBackfillService` 를 직접 호출하도록 분리했다. generic collect route 는 여전히 외부 API fetch source dispatch만 맡는다
- 이유: collect enum/registry는 “새 데이터를 외부에서 가져오는 경로”에 집중해야 한다. stored payload replay까지 같은 축에 태우면 orchestration 의미가 흐려지고 controller/service fan-out debt가 다시 커지므로, manual maintenance path를 명시적으로 분리하는 편이 재발 방지에 안전하다

## 158) builder-only nested DTO는 단위 테스트용 `ObjectMapper` 에서는 우연히 통과해도, 실제 Spring `ObjectMapper` 로 raw payload replay를 돌리면 생성자 부재로 전체 detail backfill 이 0건 실패할 수 있음
- 문제: `BokjiroDetailClient.DetailPayload` 는 `@Builder` 와 getter만 있고 기본 생성자가 없었다. unit test에서는 직렬화된 테스트 JSON을 같은 로컬 `ObjectMapper` 로 읽는 경로만 봐서 지나갔지만, local integration smoke에서 stored detail payload 190건을 Spring `ObjectMapper` 로 다시 읽자 `Cannot construct instance ... no Creators` 예외가 모든 row에서 발생해 `detailResult.upsertedCount=0` 이 됐다
- 해결: `DetailPayload` 에 `@NoArgsConstructor` / `@AllArgsConstructor` 를 추가해 field-based 역직렬화 경로를 열고, `NormalizedPolicySidecarBackfillDensityIntegrationTest` 로 실제 local DB의 stored detail payload replay가 다시 통과하는지 고정했다
- 이유: raw payload replay는 테스트 fixture가 아니라 실제 저장 JSON을 대상으로 한다. nested DTO가 builder-only 면 단위 테스트가 놓친 역직렬화 계약 차이가 local/운영 replay에서 한꺼번에 터질 수 있으므로, Spring context 기준 실데이터 smoke와 생성자 계약을 같이 잠가 재발을 막는 편이 안전하다

## 159) 복지로 detail raw payload 에서 `BK_APPLY_END_DATE` fact 가 0건이라고 해서 바로 extractor 버그로 단정하면, source payload 자체에 deadline signal이 없는 상태를 잘못 해석할 수 있음
- 문제: local density smoke 직후 `service_facts` 를 보니 `BK_AGE_ELIGIBILITY` 는 81건인데 `BK_APPLY_END_DATE` 는 0건이었다. 처음엔 detail extractor 누락으로 보일 수 있지만, 실제 `raw_api_payloads` 를 확인해 보니 detail `applyMethodDetail` non-null 은 77건이어도 date-like token은 0건이었다
- 해결: `NormalizedPolicySidecarBackfillDensityIntegrationTest` 는 `BK_APPLY_END_DATE > 0` 을 고정 assertion으로 두지 않고, raw payload 에 date-like `applyMethodDetail` 이 존재할 때만 deadline fact 존재를 요구하도록 바꿨다. 동시에 `phase-plan` 과 `db-migration` 에 현재 local snapshot 수치(`detail payload 190`, `age facts 81`, `deadline facts 0`)를 남기고 후속 보강 과제를 별도로 분리했다
- 이유: density 측정의 목적은 추출기 결함과 source signal 부재를 구분하는 데 있다. payload 자체에 날짜 단서가 없는데 deadline fact를 기대하면 smoke가 잘못된 요구사항을 테스트하게 되므로, 현재 source 특성을 문서와 테스트에 같이 고정해야 재발 해석이 흔들리지 않는다

## 160) 복지로 detail 의 다른 필드(`targetDetail`, `selectionCriteria`, `supportDetail`)에 date-like token이 조금 보인다고 해서 deadline fallback 을 바로 넓히면, 출생연도 범위나 혜택 적용기간을 신청마감으로 오인할 수 있음
- 문제: local raw detail payload를 더 확인해 보니 `targetDetail/supportDetail/selectionCriteria` 에도 date-like token이 `4 / 1 / 1` 건 있었다. 하지만 샘플을 보면 `1976.1.1 ~ 2005.12.31` 같은 출생연도 범위, `2024.7.1 ~ 2024.10.31` 같은 할인 적용기간이 섞여 있어 이 값을 `BK_APPLY_END_DATE` 로 쓰면 잘못된 신청마감 fact가 생길 위험이 컸다
- 해결: 현재 canonical collect path 에서는 `BK_APPLY_END_DATE` 를 optional fact 로 유지하고, fallback 범위를 `applyMethodDetail/supportDetail` 밖으로 넓히지 않기로 결정했다. 후속 작업은 live detail 응답에서 신청마감 explicit field가 실제로 있는지 재확인하는 것으로 분리했다
- 이유: 복지로 detail 날짜 텍스트는 “신청마감”과 “생년월일/기준기간”이 같은 포맷으로 섞여 있다. 신호가 애매한 필드까지 규칙 기반 fallback 을 넓히면 precision이 급격히 떨어지므로, 현재는 누락을 감수하고 오탐을 막는 편이 canonical fact 품질에 더 안전하다

## 161) 복지로 age range 는 첫 번째 bound 뒤에 `세` 가 빠진 비대칭 표기(`만 20 ~ 49세`)가 실제로 존재하므로, `\\d+세 ~ \\d+세` 형태만 가정하면 sidecar age fact coverage가 눈에 띄게 깎일 수 있음
- 문제: local 복지로 raw detail/backfill density가 `81 / 1335` 에서 멈춰 있던 원인을 다시 보니, 실제 본문에는 `만 20 ~ 49세 여성`, `15세~39세 청년`, `만 19세 이상 ~ 34세 이하` 처럼 첫 번째 나이 뒤 `세` 가 빠지거나 `이상 ~ 이하` 조합인 문구가 섞여 있었다. 기존 `TextConstraintExtractor.AGE_RANGE` 는 사실상 `\\d+세 ... \\d+세` 패턴만 안정적으로 잡아 이런 row가 누락될 수 있었다
- 해결: `AGE_RANGE` 정규식을 `(\\d{1,2})(?:\\s*세)? ... (\\d{1,2})\\s*세` 형태로 넓히고, `TextConstraintExtractorTest` 에 asymmetric range / 이상~이하 / 기존 세~세 케이스를 추가했다. 이후 local replay integration 을 다시 태운 결과 복지로 `BK_AGE_ELIGIBILITY` 는 `81 -> 99` rows(`BOKJIRO_CENTRAL 49`, `BOKJIRO_LOCAL 50`)로 증가했다
- 이유: 복지로/지자체 서술형 본문은 표기 일관성이 약해서 “첫 번째 bound에도 단위가 항상 붙는다”는 가정이 쉽게 깨진다. 텍스트 extractor는 대칭 표기만 가정하지 말고 실제 source snapshot에서 반복되는 비대칭 표기를 허용해야 coverage regress를 줄일 수 있다

## 162) 복지로 detail 한 번 실행의 `95/API` cap 은 안전장치로는 맞지만, 이 상태를 모른 채 sidecar density를 보면 extractor 문제가 아니라 stored detail coverage ceiling을 잘못 해석할 수 있음
- 문제: local DB를 다시 보니 복지로 서비스는 `1335`건인데 detail raw payload는 정확히 `190`건이었다. 이는 `95/API` cap 이 있는 기본 detail 수집을 한 번만 돌린 흔적과 맞아떨어지며, `raw detail 없는 서비스 1145건`, `detail row는 있지만 raw가 없는 서비스 0건` 이라 sidecar replay density는 본문 추출기뿐 아니라 detail fetch coverage ceiling에도 강하게 묶여 있었다
- 해결: `BokjiroDetailCollectService` 에 multi-round `collectBokjiroDetailGapFillResult(rounds, maxCallsPerRound)` 를 추가하고, `CollectAdminController` 에 `POST /api/admin/collect/bokjiro-details-gap-fill` exact admin path 를 노출해 per-run cap은 유지한 채 여러 라운드로 missing detail backlog 를 점진적으로 더 메울 수 있게 정리했다
- 이유: rate limit 안전장치와 backlog drain 속도는 같은 문제가 아니다. 단일 실행 cap 때문에 생긴 coverage ceiling을 extractor 한계로 오해하면 잘못된 regex/heuristic만 계속 손보게 되므로, “여러 라운드로 안전하게 더 가져오는 수동 경로”를 따로 두는 편이 재발 해석과 운영 절차 모두에 안전하다

## 163) `bokjiro-details-gap-fill` 로 raw detail coverage 를 늘려도 새 payload 수와 `service_facts` 증가는 1:1 이 아닐 수 있으므로, coverage 개선과 fact density 개선을 별도 지표로 봐야 함
- 문제: local live smoke에서 `collectBokjiroDetailGapFillResult(2, 20)` 를 태우자 detail raw payload 는 `190 -> 199`, missing detail service 는 `1145 -> 1136` 으로 개선됐지만, `service_facts` 는 `99 -> 103` 으로 `4`건만 늘었다. 새 raw payload `9`건 전부가 곧바로 age/deadline fact로 이어질 것이라고 가정하면 gap fill 효과를 과대평가하거나 extractor 문제를 잘못 짚을 수 있었다
- 해결: `phase-plan` 과 `db-migration` 에 gap fill 결과를 `raw detail coverage` 와 `service_facts density` 두 축으로 나눠 기록하고, 다음 작업도 `추가 라운드/예산 계획` 과 `fact 미생성 sample 분석` 으로 분리했다
- 이유: detail 확보와 fact 추출은 서로 다른 단계다. raw payload 가 늘어도 본문에 eligibility signal이 없으면 canonical facts 는 그대로일 수 있으므로, 운영/리팩터링 판단은 coverage와 density를 분리해 봐야 재발 해석이 흔들리지 않는다

## 164) gap fill 후 남은 no-fact 복지로 detail 대부분은 extractor 실패가 아니라 연령 eligibility 자체가 없는 일반복지 항목이므로, 소수 예외 패턴만 보고 regex를 과하게 넓히면 precision을 해칠 수 있음
- 문제: gap fill 이후 `detail raw payload 는 있지만 service_facts 는 없는 복지로 서비스` 를 다시 샘플링해 보니 `130`건 중 `income-like` 는 `40`, `date-like` 는 `2`, strict `age-like` residual candidate 는 사실상 `1`건(`청년내일저축계좌` 의 `만 15세~만 40세`)뿐이었다. 그 외 다수는 저소득층 위문, 보훈 행사, 장애인 수리 지원처럼 연령 eligibility가 없는 일반복지 항목이었다
- 해결: `phase-plan` 과 `db-migration` 에 no-fact sample 분포를 기록하고, 후속 작업을 광범위한 age regex 확대가 아니라 `second-bound 만` 패턴 처리 여부와 `income-like` soft fact 후보 검토로 좁혔다
- 이유: sample 분포를 보지 않고 “facts가 없으니 regex를 더 넓힌다”로 가면 일반복지 본문에서 잘못된 age/income fact가 더 생길 수 있다. 남은 미생성 집합의 대부분이 비연령 항목이라면 precision을 지키면서 소수 패턴만 선택적으로 다루는 편이 재발 방지에 안전하다

## 165) 복지로 age range 는 두 번째 bound 앞에도 `만` 이 붙는 실제 residual 패턴(`만 15세~만 40세`)이 있어, 첫 번째 bound만 보강한 상태로 멈추면 소수지만 반복 가능한 age fact 누락이 남을 수 있음
- 문제: gap fill 후 no-fact sample 분석에서 residual `age-like` candidate 는 사실상 `청년내일저축계좌` 한 건뿐이었지만, 본문 패턴이 `만 15세~만 40세` 였다. 기존 `AGE_RANGE` 는 첫 번째 bound 앞의 `만` 만 허용하고 두 번째 bound는 `40세` 형태만 잡았기 때문에, precision을 해치지 않는 좁은 보강 여지가 남아 있었다
- 해결: `TextConstraintExtractor.AGE_RANGE` 를 `(?:만\\s*)?(\\d{1,2})(?:\\s*세)? ... (?:만\\s*)?(\\d{1,2})\\s*세` 형태로 보강하고 `TextConstraintExtractorTest` 회귀 케이스를 추가했다. 이후 stored raw payload replay integration 을 다시 태워 local DB에서 복지로 `service_facts` 가 `103 -> 104`, `BK_AGE_ELIGIBILITY` 가 `BOKJIRO_CENTRAL 52`, `BOKJIRO_LOCAL 52` 로 증가한 것을 확인했다
- 이유: 광범위한 regex 확장은 피해야 하지만, 실측 sample 하나가 실제 청년 정책 행이고 패턴도 매우 좁게 정의될 수 있다면 그런 residual은 선택적으로 흡수하는 편이 precision/coverage 균형에 맞다. “남은 게 거의 없으니 아예 안 건드린다”와 “광범위하게 다 넓힌다” 사이에서 source snapshot에 맞는 최소 보강을 택하는 것이 재발 방지에 안전하다

## 166) 복지로 no-fact detail 의 income-like signal 은 숫자 threshold와 수급자/차상위 자격이 한 버킷에 섞여 있고, threshold 쪽도 분기 조건이 많아 현재 canonical `INCOME_*` hard fact 로 바로 승격하면 의미가 쉽게 깨질 수 있음
- 문제: `second-bound 만` 보강 뒤 남은 no-fact 복지로 detail 을 다시 분류해 보니 income/beneficiary soft candidate 가 `49`건이었고, 구성은 `beneficiary_only 28`, `threshold_like 13`, `low_income_label 7`, `won_threshold 1`, `other 2` 였다. `threshold_like 13` 건도 `신혼/2자녀/출산/맞벌이/우대형/일반형/개별심사` 같은 분기 케이스가 `4`건, 다중 `% 이하` threshold 가 `3`건, 다중 `만원 이하` threshold 가 `1`건 섞여 있어 단일 `INCOME_PCT` 또는 `INCOME_WON` 값으로 접기 어려웠다
- 해결: 이번 단계에서는 복지로 income-like row를 canonical hard fact 로 승격하지 않고, `phase-plan` 과 `db-migration` 에 “`beneficiary_only` 는 soft taxonomy 검토”, “`threshold_like` 는 optional soft signal 검토”로 작업을 분리해 기록했다
- 이유: 현재 canonical facts 는 추천 hard filter 로도 재사용될 수 있어, 조건 분기와 자격 라벨이 섞인 source 신호를 성급히 숫자 fact 로 평탄화하면 false negative/false positive 둘 다 커질 수 있다. 먼저 의미가 안정적인 soft bucket 으로 나눠 설계하는 편이 재발 방지와 추천 품질 측면에서 안전하다

## 167) 복지로 detail 본문에서 `기초생활수급자` / `차상위계층` 같은 explicit beneficiary label 은 canonical `TARGET_GROUP` soft taxonomy 로 보존할 가치가 있지만, 현재 sidecar writer refresh contract 로는 detail term 추가가 list taxonomy 를 지워버릴 수 있음
- 문제: local snapshot 재분류 기준 `beneficiary_only` bucket 은 `기초생활수급자` 계열 `24`, `차상위계층` 계열 `5` 로 의미가 비교적 안정적이었고 `TARGET_GROUP` soft taxonomy 후보로 적절했다. 하지만 현재 `DeferredNormalizedPolicySidecarWriter` 는 복지로 aggregate 에 taxonomy term 이 하나라도 있으면 `LIFE_STAGE`, `INTEREST_THEME`, `TARGET_GROUP` 전체 refresh group 을 삭제 후 재삽입한다. 그래서 detail aggregate 에 beneficiary `TARGET_GROUP` 만 추가하면 기존 list aggregate 의 `청년`, `1인가구` 같은 term 도 같이 사라질 수 있었다
- 해결: 이번 단계에서는 `기초생활수급자` / `차상위계층` whitelist 와 label normalize 규칙만 문서로 고정하고, 구현 선행조건으로 `phase-aware term refresh` 분리를 `phase-plan` 에 추가했다
- 이유: semantic decision 이 맞더라도 writer contract 가 그 source-phase 구분을 표현하지 못하면 실제 적재 시 side effect 가 더 크다. detail-derived soft taxonomy 는 list-derived taxonomy 와 공존해야 하므로, refresh 경계를 먼저 분리하는 편이 재발 방지에 안전하다

## 168) sidecar term refresh 를 `term_group` 단위로만 잡으면 복지로처럼 list phase와 detail phase가 같은 `TARGET_GROUP` 을 공유하는 source에서 detail soft taxonomy 한 줄을 넣는 순간 기존 list taxonomy 까지 같이 지워질 수 있음
- 문제: 복지로 list aggregate 는 `TARGET_GROUP(source_field=trgterIndvdlNmArray)` 로 `청년`, `1인가구` 같은 공식 term 을 저장하고, 향후 detail aggregate 는 `TARGET_GROUP(source_field=targetDetail/selectionCriteria)` 로 `기초생활수급자` 같은 derived term 을 넣게 된다. 기존 writer는 `term_group='TARGET_GROUP'` 전체를 delete 후 재삽입했기 때문에, detail phase upsert 한 번으로 list phase `TARGET_GROUP` 까지 같이 사라질 수 있었다
- 해결: `DeferredNormalizedPolicySidecarWriter` 의 term replace contract 를 incoming term의 `(term_group, source_field)` scope 로 좁혔다. unit test 에서 delete SQL/args 를 검증했고, integration 에서는 list aggregate 뒤 detail-derived `TARGET_GROUP` 를 직접 upsert 해도 기존 `청년`, `1인가구` 와 공존하는 것을 확인했다
- 이유: canonical term 은 source와 의미만 아니라 “어느 phase/list/detail 에서 왔는가”도 같이 관리해야 한다. 같은 `term_group` 안에서도 source_field가 phase 경계를 표현하고 있다면 refresh scope 도 거기에 맞춰 좁혀야, 향후 beneficiary whitelist 같은 detail soft taxonomy 를 안전하게 추가할 수 있다

## 169) 복지로 detail 본문에서 beneficiary soft taxonomy 를 넓게 잡으면 `취업취약계층`, `정보 소외계층`, `저소득 한부모가족` 같은 broad label 이 canonical `TARGET_GROUP` 으로 섞여 taxonomy 정밀도가 급격히 떨어질 수 있음
- 문제: local snapshot 재분류에서 `beneficiary_only` bucket 안에도 `기초생활수급자` / `차상위계층` 처럼 의미가 안정적인 label 과, `취업취약계층`, `정보 소외계층`, `저소득 한부모가족` 처럼 범위가 넓거나 다른 축과 겹치는 label 이 함께 나왔다. 이들을 동일 규칙으로 `TARGET_GROUP` 에 넣으면 복지로 detail soft taxonomy 가 broad vulnerable-group bucket 으로 오염될 위험이 있었다
- 해결: `WelfareServiceMapper.toNormalizedBokjiroDetail()` 는 `국민기초생활보장수급자`, `기초생활수급자`, `생계/의료/주거/교육급여 수급자`, `수급권자` 만 `기초생활수급자` 로, `차상위*` 표현만 `차상위계층` 으로 정규화해 `TARGET_GROUP(source_field=targetDetail/selectionCriteria, authority=SYSTEM_DERIVED)` term 으로 적재하고, broad label 은 계속 skip 하도록 unit/integration test 로 고정했다
- 이유: beneficiary soft taxonomy 의 목적은 hard income fact 대체가 아니라 “의미가 안정적인 취약계층 eligibility signal” 보존이다. canonical `TARGET_GROUP` 은 추천 read-model 에 재사용될 가능성이 있으므로, broad label 까지 같이 적재하면 precision 손실이 더 크다

## 170) 같은 테스트 클래스를 대상으로 `gradlew test` 와 `gradlew integrationTest` 를 병렬 실행하면 Gradle XML result writer 가 동일 결과 파일을 동시에 만지면서 가짜 실패를 낼 수 있음
- 문제: 이번 task 검증에서 `NormalizedPolicyAggregateTest` / `BokjiroSidecarMergeIntegrationTest` 를 두 Gradle 세션으로 거의 동시에 돌리자, 테스트 본문은 통과했는데 `Could not write XML test results ... TEST-com.example.welfare.integration.BokjiroSidecarMergeIntegrationTest.xml` 예외로 한 세션이 실패했다
- 해결: 같은 테스트 클래스나 동일 result directory 를 쓰는 검증은 병렬로 돌리지 않고, unit -> integration 순서로 순차 재실행해 결과를 확정했다
- 이유: 코드 결함과 빌드 산출물 write collision 은 원인이 다르다. 결과 파일 경합을 테스트 실패로 오해하지 않으려면 같은 타깃 검증은 순차 실행으로 고정하는 편이 안전하다

## 171) 복지로 beneficiary whitelist density 는 `payload 수 == service 수 == term 수` 로 1:1 대응하지 않을 수 있으므로 row count 와 distinct service count 를 분리해 봐야 함
- 문제: local draft migration 상태에서 beneficiary-like detail raw payload candidate 는 `42`건이었는데, replay 후 whitelist `TARGET_GROUP(source_field=targetDetail/selectionCriteria)` term 은 `59 rows / 42 services` 로 적재됐다. `기초생활수급자` `38`, `차상위계층` `21` 이라 일부 서비스는 두 label을 동시에 갖고 있었고, 단순 term row count 만 보면 coverage가 실제 서비스 수보다 커 보일 수 있었다
- 해결: beneficiary density 측정은 `NormalizedPolicySidecarBeneficiaryTermDensityIntegrationTest` 와 문서에서 항상 `term row count` 와 `distinct service count` 를 같이 기록하도록 정리했다
- 이유: beneficiary soft taxonomy 는 hard fact 한 줄과 달리 한 서비스에 복수 term 이 공존할 수 있다. row count 만 보면 중복으로 과대평가되고, service count 만 보면 label richness가 사라지므로 두 지표를 같이 봐야 재발 해석이 정확하다

## 172) 복지로 beneficiary whitelist overlap service 는 `기초생활수급자` 와 `차상위계층` 을 collapse 하기보다 multi-term 으로 유지해야 source 의미를 보존할 수 있음
- 문제: local snapshot 에서 beneficiary whitelist overlap service 가 `17`건 있었고, `여성청소년 생리용품 지원`, `통합문화이용권`, `자활근로(기초, 차상위)`, `재난적의료비 지원 사업` 처럼 source 자체가 두 집단을 함께 명시한 사례가 다수였다. 이를 하나의 상위 label로 collapse 하면 “두 집단 모두 대상”이라는 원문 의미가 사라질 수 있었다
- 해결: current canonical 정책은 `기초생활수급자` 와 `차상위계층` 을 상하위 collapse 하지 않고 multi-term 으로 그대로 유지하도록 문서에 고정했다. 대신 이후 recommendation/read-model 단계에서만 중복 가중치 dedupe 를 따로 설계하는 것으로 후속 작업을 분리했다
- 이유: beneficiary bucket 은 hard fact 가 아니라 soft taxonomy 이므로, source가 제공한 대상군 richness를 보존하는 편이 안전하다. collapse 는 나중 read-model에서 언제든 할 수 있지만, 저장 단계에서 잃은 정보는 되돌리기 어렵다

## 173) 복지로 beneficiary soft taxonomy 는 저장 단계에서 multi-term 을 보존하되, 추천 단계에서는 `BENEFICIARY_SUPPORT` 같은 dedupe bucket 으로 max-one bonus 를 주는 편이 current rule 구조와 가장 자연스럽게 맞는다
- 문제: beneficiary whitelist 를 multi-term 으로 남기면 원문 의미는 보존되지만, future canonical read-path 가 raw `TARGET_GROUP` term 두 개를 그대로 scoring 에 태우면 같은 혜택축에서 double-count 가 생길 수 있다. 반대로 저장 단계에서 collapse 하면 나중에 세부 라벨을 설명/배지/UI 에 쓰기 어렵다
- 해결: persistence 는 raw multi-term 유지, read-model 은 `BENEFICIARY_SUPPORT` dedupe bucket 별도 생성, scoring 은 bucket 기준 서비스당 최대 1회 bonus 라는 전략으로 문서에 고정했다. 현재 `RuleScoringService.targetGroupMatches(...)` 도 boolean 매칭 기반이라 동일 축 다중 라벨을 바로 2배 가산하지 않는다는 점을 근거로 future sidecar read-path 도 같은 `max-one bonus` 규칙을 유지하기로 했다
- 이유: 현재 추천 구조는 합계형 점수이지만 target group 매칭은 본질적으로 “해당 축 매칭 여부”에 가깝다. soft taxonomy 의 raw richness 는 read-model 밖에서 보존하고, scoring 에서는 bucket 단위로 dedupe 하는 쪽이 precision 과 explainability 를 같이 지키기 쉽다

## 174) beneficiary dedupe 는 repository SQL에서 바로 collapse 하기보다 canonical recommendation read-model projection 에서 raw term과 dedupe bucket을 함께 만드는 편이 retrieval/scoring/response 책임을 덜 섞는다
- 문제: beneficiary raw term을 SQL 단계에서 곧바로 `BENEFICIARY_SUPPORT` 하나로 접어버리면 explanation/UI용 raw label, source drift 분석, 향후 bucket 정책 변경 여지가 함께 사라진다. 반대로 raw term만 그대로 넘기면 scoring 단계에서 중복 가산 위험이 남는다
- 해결: `RecommendationCandidateProjection` 류의 canonical recommendation read-model projection 을 두고, 여기서만 `targetGroupsRaw`, `beneficiaryTerms`, `targetGroupBuckets` 를 동시에 구성하는 경계를 문서로 고정했다. retrieval 은 base entity query 뒤 projection hydrate, scoring 은 projection의 deduped bucket만 사용, response/UI는 raw term을 그대로 참조하는 구조다
- 이유: persistence / projection / scoring / response 책임을 분리하면 저장 단계는 source truth를 보존하고, 추천 단계는 scoring-friendly 구조를 소비하며, UI는 설명 가능성을 유지할 수 있다. 이 경계를 먼저 고정해야 이후 sidecar read-path 구현이 다시 raw table join과 rule logic을 뒤섞지 않는다

## 175) canonical read-model 초안은 실제 추천 파이프라인 연결 전에 repository 단계에서 bucket 조립 규칙을 먼저 고정해야 retrieval/service 경계가 다시 흔들리지 않음
- 문제: 문서로만 `RecommendationCandidateProjection` 경계를 정해두면, 실제 구현 시 `RetrievalService` 나 `RuleScoringService` 안에서 raw sidecar row를 즉석으로 묶거나 beneficiary bucket을 서비스 레이어에서 임시 조립하는 식으로 다시 책임이 흩어질 수 있었다
- 해결: `RecommendationCandidateProjection` DTO와 `CanonicalRecommendationReadModelRepository` 초안을 먼저 추가해 `service_id IN (...) -> projection hydrate -> BENEFICIARY_SUPPORT dedupe bucket 생성` 규칙을 repository 단계에서 코드로 고정했다. unit test로 `기초생활수급자` + `차상위계층` raw term이 있어도 bucket은 1개만 생기는 것을 검증했다
- 이유: retrieval/scoring 전환은 단계적으로 갈 수 있어도, projection 조립 위치는 초기에 잘못 잡으면 이후 리팩터링 비용이 커진다. 추천기가 raw term을 직접 만지지 않도록 repository boundary를 먼저 코드화하는 편이 재발 방지에 유리하다

## 176) canonical recommendation read-model 전환은 retrieval hydrate 와 scoring 소비를 한 번에 묶지 않고 분리해야 legacy 추천 품질 회귀를 좁은 범위로 막을 수 있음
- 문제: `RetrievalService` 에 canonical projection hydrate 를 붙이는 단계에서 곧바로 `RuleScoringService` 입력 구조까지 함께 바꾸면, 후보 SQL/후처리 필터 변경과 점수 회귀가 한 PR에 섞여 원인 분리가 어려워진다. 특히 지금 scoring/persistence 흐름은 아직 `List<WelfareService>` 를 기준으로 안정적으로 동작 중이라, 첫 연결부터 scoring까지 건드리면 실패 반경이 커질 수 있었다
- 해결: `RetrievedRecommendationCandidates` DTO를 추가해 retrieval 단계가 legacy `WelfareService` 후보와 canonical projection map 을 함께 반환하게만 바꾸고, `RecommendationFacade` 는 당분간 `candidates()` 만 꺼내 써서 scoring/persistence 경로를 그대로 유지했다. 다음 단계에서만 `RuleScoringService` 가 projection bucket을 병행 소비하도록 분리했다
- 이유: canonical read-model 전환은 retrieval, scoring, response 경계를 순차적으로 옮기는 편이 디버깅과 회귀 검증에 유리하다. hydrate 연결과 scoring 전환을 분리하면 “후보 추출 회귀”와 “점수 계산 회귀”를 각각 독립적으로 검증할 수 있다

## 177) canonical beneficiary bucket 은 별도 가산 슬롯을 새로 만들기보다 기존 `targetGroupMatches` boolean bonus 슬롯에 OR 로 연결해야 current rule score 의미를 덜 흔든다
- 문제: `BENEFICIARY_SUPPORT` bucket 을 scoring 에 연결할 때 새 보너스 항목으로 따로 더하면, 기존 `TARGET_GROUP` 보너스와 함께 같은 축을 이중 가산할 수 있었다. 특히 `기초생활수급자` 와 `차상위계층` raw term을 multi-term 으로 보존하는 현재 정책과 겹치면 “dedupe bucket 도 있고 broad target match 도 있다”는 이유로 점수 회귀가 생길 수 있었다
- 해결: 첫 단계 브리지는 `RuleScoringService.score(RetrievedRecommendationCandidates, ...)` 오버로드만 추가하고, canonical `BENEFICIARY_SUPPORT` bucket 은 기존 `targetGroupMatches(...)` boolean 슬롯에 OR 로 연결했다. 동시에 `beneficiaryTerms` 로 `기초생활수급자<=1`, `차상위계층<=3` threshold 를 좁게 걸어 서비스당 최대 1회 bonus 만 허용했다
- 이유: beneficiary bucket 의 목적은 새로운 축을 더 만드는 것이 아니라 raw multi-term 에서 중복 가산을 막으면서 기존 target-group 의도를 canonical read-model 로 옮기는 것이다. 기존 bonus 슬롯에 병행 연결하는 편이 legacy score 의미를 덜 흔들고 회귀 범위도 좁다

## 178) canonical broad term(`interestThemes`, `keywordTags`, `targetGroupsRaw`) 도 beneficiary와 마찬가지로 새 가산 항목을 만들기보다 legacy boolean matcher에 OR 로 연결해야 점수식 폭증을 막을 수 있음
- 문제: retrieval hydrate 뒤 projection term을 읽기 시작할 때, 기존 `ServiceTag` 기반 bonus 위에 canonical term bonus 를 새로 더하면 같은 관심분야/대상군 신호가 legacy tag 와 projection 양쪽에서 동시에 들어오는 순간 점수가 불필요하게 커질 수 있었다. 특히 `targetGroupsRaw` 는 sidecar backfill 이 진행될수록 legacy tag 와 겹칠 가능성이 높았다
- 해결: `RuleScoringService` 의 `interestThemeMatches`, `keywordMatches`, `targetGroupMatches` 가 projection 값을 별도 가산 슬롯으로 더하지 않고 기존 boolean matcher 안에서 OR 로만 읽도록 바꿨다. `factKeys` 는 이번 단계에서 `isDeadlineSoon` helper 경계에만 먼저 연결하고, bonus 규칙 자체는 기존 `apply_end_date` 의미를 유지했다
- 이유: canonical read-model 브리지는 “같은 의미의 신호를 다른 저장소에서 읽는 것”이지 새 점수 축을 추가하는 작업이 아니다. 기존 matcher 슬롯을 재사용해야 legacy path 와 canonical path 가 공존하는 동안에도 score inflation 없이 회귀를 좁게 통제할 수 있다

## 179) priority 가중치도 새 taxonomy 해석기를 따로 만들기보다 먼저 `compat_unified_category` / `applyEndDate` read-model summary 를 우선 읽게 연결해야 `DefaultPriorityMatcher` 회귀를 좁게 막을 수 있음
- 문제: `DefaultPriorityMatcher` 는 아직 `service.getUnifiedCategory()` 와 `service.getApplyEndDate()` 에 직접 묶여 있어, retrieval/scoring 이 canonical projection 을 병행 읽기 시작한 뒤에도 priority 가중치만 legacy entity 값을 계속 보면 read-model summary 와 우선순위 가중치가 서로 다른 값을 볼 수 있었다. 반대로 첫 단계부터 canonical taxonomy summary code/label 해석까지 matcher 안에 같이 넣으면 회귀 반경이 다시 커질 수 있었다
- 해결: `PriorityMatcher.matches(priority, service, projection)` 오버로드를 추가하고, `DefaultPriorityMatcher` 는 먼저 `RecommendationCandidateProjection.unifiedCategoryCompat` 와 `applyEndDate` 를 우선 읽도록만 좁게 바꿨다. `RuleScoringService.applyPriorityWeight(...)` 도 projection 을 함께 넘기게 맞췄고, canonical taxonomy summary code/label 직접 해석은 후속 task 로 분리했다
- 이유: priority 가중치는 현재도 `unifiedCategory` 호환 레이어를 전제로 동작한다. 첫 단계는 legacy entity와 canonical read-model summary 간 불일치를 줄이는 것이 목적이고, category code 체계 자체를 바꾸는 일은 별도 결정으로 분리해야 원인 분리가 쉽다

## 180) `service_taxonomies` summary가 아직 완전히 안정화되기 전에는 `DefaultPriorityMatcher` 가 canonical summary code/label을 직접 해석하지 말고 `compat_unified_category` 를 priority 호환 레이어로 유지하는 편이 안전함
- 문제: canonical read-model 브리지가 진행되면서 `DefaultPriorityMatcher` 를 바로 `youth_major_code`, `gov24_service_field_code` 같은 summary code/label 직독 방식으로 바꾸고 싶어질 수 있다. 하지만 현재는 `service_taxonomies` 실제 적재 coverage, `compat_unified_category` 와 summary code 간 drift, priority 옵션 코드와 summary code 매핑표가 아직 확정되지 않아, matcher가 이를 직접 읽기 시작하면 우선순위 의미가 조용히 바뀔 위험이 있었다
- 해결: 이번 단계에서 `compat_unified_category` 를 계속 priority 호환 레이어로 유지하기로 문서에 고정하고, `DefaultPriorityMatcher` 는 당분간 `RecommendationCandidateProjection.unifiedCategoryCompat` / `applyEndDate` 만 우선 읽게 유지했다. canonical summary code/label 직독 전환은 `service_taxonomies` summary 안정화, drift inventory, priority 매핑표가 준비된 뒤 별도 task로 넘겼다
- 이유: priority는 추천 점수의 배율을 직접 바꾸므로, category 해석 기준이 흔들리면 회귀가 조용히 커진다. 호환 레이어를 한동안 유지하면 현재 UX 의미를 보존한 채 canonical summary 데이터 품질을 먼저 검증할 수 있다

## 181) local `service_taxonomies` snapshot 기준 현재 drift는 “compat와 canonical 직접 충돌”보다 `youth_major_label` summary의 raw 복사/다중값 흔적이 더 큰 문제라, matcher를 summary 직독으로 옮기기 전에 summary 정제가 먼저 필요함
- 문제: 실제 local DB를 집계해 보니 `compat_unified_category_label` 은 `3634 / 3634` rows에서 채워졌지만 `youth_major_label` 은 `2298 / 3634`, `youth_mid_label` 과 `gov24_service_field_label` 은 `0` 이었다. 특히 `YOUTH` 에서 `compat=기타` + `youth_major_label` 채움이 `471`, `youth_major_label` comma 포함이 `102` 였고, 샘플을 보면 `일자리,일자리`, `주거,주거`, `금융･복지･문화` 같은 raw `category_main` 복사 흔적이 남아 있었다
- 해결: 이 결과를 별도 inventory 문서로 고정하고, 다음 작업을 `YOUTH category_main -> youth_major summary 정제 규칙`, `comma/duplicate collapse`, `summary 재적재 후 drift 재측정` 으로 다시 쪼갰다. 현재는 `compat_unified_category` 를 priority 호환 레이어로 유지하고 summary 직독은 계속 보류한다
- 이유: 지금 단계의 핵심 문제는 compat와 canonical이 서로 다른 카테고리를 가리키는 충돌보다, canonical summary 자체가 아직 single normalized summary로 안정화되지 않았다는 점이다. 정제 전 summary를 matcher에 바로 연결하면 drift보다 summary 품질 문제가 더 크게 우선순위를 흔들 수 있다

## 182) `service_taxonomies.youth_major_*` summary는 raw `category_main` 문자열을 그대로 복사하지 말고, single canonical major로 안정적으로 collapse 가능한 경우에만 채워야 함
- 문제: drift inventory를 더 들여다보니 `youth_major_label` 에는 `금융･복지･문화`, `참여･기반` 같은 raw label 뿐 아니라 `일자리,일자리`, `주거,주거`, `일자리,교육` 같은 comma-delimited 문자열도 그대로 들어가 있었다. 이 값을 summary에 남겨두면 `service_taxonomies` 가 “서비스당 1행 canonical summary” 역할을 못 하고 raw multi-value dump와 다를 바 없어졌다
- 해결: `YOUTH category_main -> service_taxonomies.youth_major_*` 규칙을 문서로 고정해, raw `category_main` 은 split/trim 후 canonical major 집합(`일자리`, `주거`, `교육`, `복지문화`, `참여권리`)으로 normalize 하고, distinct canonical code 가 `1`개일 때만 summary를 채우기로 했다. `2+` distinct code 가 나오면 summary는 `NULL` 로 두고 raw richness는 term/raw 계층에만 남기도록 정리했다
- 이유: summary row는 read-model과 matcher가 빠르게 읽는 single canonical 값이어야 한다. raw multi-value를 그대로 두면 drift보다 summary semantics 자체가 무너지므로, collapse 가능한 경우만 채우고 나머지는 `NULL` 로 두는 편이 안전하다

## 183) `DeferredNormalizedPolicySidecarWriter` 도 youth major summary 규칙을 그대로 따라야지, mapper에서 받은 raw `taxonomy.youthMajor()` 문자열을 다시 summary에 그대로 쓰면 drift가 즉시 재발함
- 문제: `YOUTH category_main -> youth_major_*` 정제 규칙을 문서로만 고정해 두고 writer가 계속 `taxonomy.youthMajor()` raw string을 그대로 `service_taxonomies` 에 적재하면, backfill이 아니라 live collect/upsert 경로에서 곧바로 `금융･복지･문화`, `주거,주거`, `일자리,교육` 같은 raw/duplicate/multi-major 문자열이 summary 컬럼에 다시 들어가 drift inventory가 재발할 수 있었다
- 해결: `DeferredNormalizedPolicySidecarWriter` 에 writer-side normalize helper를 추가해 comma split, punctuation normalize(`･ -> ·`), canonical label collapse를 먼저 적용하고, distinct canonical major가 `1`개일 때만 `youth_major_code`, `youth_major_label` 을 채우도록 바꿨다. variant normalize, duplicate collapse, multi-major null summary는 `DeferredNormalizedPolicySidecarWriterTest` 로 고정했다
- 이유: summary 정제 규칙은 backfill SQL뿐 아니라 live collect path에도 동일하게 걸려야 한다. writer가 raw summary를 다시 써 버리면 canonical read-model과 priority layer가 보는 값이 계속 흔들리므로, 적재 경계에서 single canonical summary invariant를 강제하는 편이 안전하다

## 184) draft backfill SQL도 writer와 같은 youth major collapse 규칙을 써야지, 한쪽만 정제하면 collect refresh와 초기 backfill이 서로 다른 summary를 남겨 drift가 반복됨
- 문제: writer는 single canonical major만 summary에 남기도록 정제됐지만, `V2026_04_30_02__seed_policy_normalization_codes.sql` 이 여전히 `ws.category_main` exact match / raw label 복사 방식이면 초기 backfill 직후에는 `금융･복지･문화`, `참여･기반`, `일자리,일자리` 같은 값이 들어가고, 이후 live collect refresh가 돌 때만 일부 row가 정제되는 식으로 경로별 summary semantics가 달라질 수 있었다
- 해결: draft SQL에 `JSON_TABLE` 기반 token split, punctuation normalize, canonical major collapse CTE를 추가해 `distinct canonical major = 1` 일 때만 `youth_major_code`, `youth_major_label` 을 채우도록 바꿨다. local YOUTH snapshot 재집계 결과 `2299 total / 2248 filled / summary_with_comma=0 / raw_variant_labels=0` 으로 정제 효과를 확인했다
- 이유: canonical summary는 “누가 적재했느냐”와 무관하게 같은 규칙으로 채워져야 한다. writer와 backfill이 다른 collapse 규칙을 쓰면 drift inventory가 데이터 품질 문제가 아니라 경로 차이 문제로 오염되므로, 적재 경로 둘 다 같은 single-major invariant를 공유해야 한다

## 185) `youth_major` summary를 실제로 재적재하고 나니 남은 drift의 본질은 raw multi-value가 아니라 `compat=기타` 와 canonical major 공존 문제였음
- 문제: 기존 inventory에서는 `youth_major_label` 의 comma/duplicate/raw variant가 너무 커서, `compat_unified_category` 와 canonical summary 사이의 진짜 남은 차이가 “summary 품질”인지 “호환 레이어 설계”인지 분리하기 어려웠다
- 해결: local DB `service_taxonomies` 의 `YOUTH` summary를 collapse 규칙으로 다시 써넣고 재집계했다. 그 결과 `youth_major_with_comma=0`, `raw_variant_labels=0` 으로 summary 품질 문제는 사라졌고, 대신 `compat=기타 + canonical youth_major 채움` 집합이 `421`건 남는다는 점이 핵심 잔여 drift로 분리됐다
- 이유: summary가 single canonical value로 안정화된 뒤에는 더 이상 “정제 먼저”가 아니라 “`compat` 를 계속 저장할지, YOUTH canonical major를 어디까지 priority/read-model에 반영할지”가 다음 설계 질문이 된다. 문제 성격이 달라졌으므로 후속 task도 summary 정제에서 호환 레이어 해석 정책으로 옮기는 편이 맞다

## 186) `compat=기타 + canonical youth_major 채움` 집합은 지금 바로 canonical major로 override 하지 말고, priority/read-model 에서는 secondary hint로만 남겨야 기존 제품 의미를 덜 흔든다
- 문제: summary 정제 후에도 `compat=기타 + youth_major 채움` 이 `421`건 남았기 때문에, 이를 보고 `DefaultPriorityMatcher` 나 response category를 즉시 canonical `복지문화/참여권리/교육` 등으로 치환하고 싶어질 수 있다. 하지만 현재 priority 옵션(`금융·생활지원`, `참여·기회`, `교육·직업훈련`)은 아직 canonical major와 1:1 브리지 표가 없고, `compat` 는 실제 제품 계약으로 동작 중이라 조용한 의미 변경이 생길 위험이 컸다
- 해결: 이번 단계에서는 `compat=기타` 를 canonical `youth_major` 로 자동 override 하지 않기로 고정했다. priority/scoring/response category는 계속 `compat_unified_category` 만 기준으로 유지하고, canonical `youth_major` 는 read-model에서 inventory/explanation/future experiment 용 보조 힌트로만 남긴다
- 이유: explicit bridge table 없이 canonical major를 legacy priority bucket으로 승격하면, 저장 계층의 정규화 성공이 곧바로 UX 의미 변경으로 번진다. 현재는 raw truth와 호환 레이어를 분리해 둔 장점을 유지하고, 별도 inventory와 매핑표가 준비된 뒤에만 bridge를 명시적으로 열어 두는 편이 안전하다

## 187) `compat=기타 + canonical youth_major` 집합도 한 덩어리로 보면 안 되고, major별 분포를 먼저 쪼개야 bridge table 검토 우선순위를 제대로 잡을 수 있음
- 문제: `421`건을 단순 총량으로만 보면 `canonical youth_major -> legacy priority` 브리지를 하나의 결정처럼 다루게 되지만, 실제로는 `복지문화 171`, `참여권리 130`, `교육 102`, `일자리 15`, `주거 3` 으로 분포가 크게 달랐다. 각 major 안의 `category_sub` 조합도 서로 달라, 같은 bridge 정책을 한 번에 적용하면 과도하게 일반화될 위험이 있었다
- 해결: local DB에서 major별 count, 대표 `category_sub`, 샘플 row를 다시 뽑아 별도 inventory 문서로 고정했다. 그 결과 bridge 검토 우선순위를 `참여권리`, `교육`, `복지문화` 3개에 집중하고, `일자리`, `주거` 는 duplicate collapse 잔여로 간주해 우선순위를 낮췄다
- 이유: bridge table은 저장 계층의 canonical major를 UX priority bucket으로 승격시키는 규칙이므로, 총량이 아니라 major별 의미 분포를 기준으로 검토해야 한다. 먼저 분포를 쪼개야 어디가 “실제 새 bridge 후보”이고 어디가 “정제 잔여”인지 구분할 수 있다

## 188) major별 분포를 봐도 bridge 승격 판단은 다시 row-level sample로 좁혀야 하고, `교육 / 참여권리 / 복지문화` 는 서로 다른 판정이 필요함
- 문제: `복지문화 171`, `참여권리 130`, `교육 102`처럼 큰 major만 놓고 보면 셋 다 bridge 후보처럼 보일 수 있다. 하지만 실제 sample을 보면 `교육` 은 `교육비지원/미래역량강화/온·오프라인교육` 중심으로 현재 `교육·직업훈련` bucket과 가깝고, `참여권리` 는 `청년참여`와 `정책인프라구축` 이 섞여 있으며, `복지문화` 는 `건강/문화활동/예술인지원` 이 많이 섞여 의미가 다르다
- 해결: row-level review를 통해 `교육` 은 “가장 유력한 후속 후보”, `참여권리` 는 “subset bridge만 조건부 검토”, `복지문화` 는 “현 시점 보류”로 판정을 갈랐다. 즉 bridge table이 필요하더라도 전집합 일괄 도입이 아니라 candidate별로 다른 정책을 써야 한다고 문서로 고정했다
- 이유: canonical major를 legacy priority bucket으로 승격시키는 규칙은 category label 하나만 맞는다고 끝나지 않는다. 실제 row-level 행동 가능성, 운영/인프라 성격 혼입 여부, 현재 priority bucket 의미를 함께 봐야 하므로 candidate별 판정을 분리하는 편이 안전하다

## 189) explicit bridge table은 candidate review가 끝났다고 바로 만드는 게 아니라, 실제 실험/전환이 시작될 때만 도입해야 함
- 문제: `교육` 이 가장 유력한 후보라는 결론이 나오면, 이를 이유로 `youth_major -> priority bucket` explicit bridge table을 미리 만들어 두고 싶어질 수 있다. 하지만 현재 review 결과는 “후보 우선순위”일 뿐이고, `참여권리` 는 subset 조건부, `복지문화` 는 보류라 전체 table 스키마를 먼저 확정하면 오히려 부분적으로만 유효한 규칙을 시스템 계약처럼 굳혀 버릴 위험이 있었다
- 해결: 이번 단계에서는 explicit bridge table을 만들지 않기로 고정했다. bridge table은 실제 전환 대상이 생길 때, 예를 들어 `교육 -> 교육·직업훈련` 단일 후보 실험이나 `청년참여` subset bridge를 시작할 때만 도입하는 artifact로 정의했다
- 이유: bridge table은 문서 보조물이 아니라 matcher/read-model/response 의미를 바꾸는 실행 계약이다. 확정되지 않은 후보들을 한 표에 먼저 넣으면 “지금은 안 쓴다” 해도 나중에 암묵 계약처럼 소비될 수 있으므로, 실제 사용 시점 직전에 가장 작은 범위로 도입하는 편이 안전하다

## 190) `교육 -> 교육·직업훈련` 은 bridge candidate 중 가장 안전하지만, 그래도 기본값 승격이 아니라 narrow experiment 후보로만 승인해야 함
- 문제: `교육` 집합은 sample 기준으로 가장 자연스러운 후보라 바로 `compat=기타` 를 `교육·직업훈련` 으로 승격하고 싶어질 수 있다. 하지만 여기에는 `교육비지원`, `미래역량강화`, `온·오프라인교육` 이 함께 섞여 있어, “교육 canonical major가 보이면 곧바로 제품 category도 바꾼다”로 가면 실험 없이 기본 동작을 바꾸는 문제가 생긴다
- 해결: 이번 단계에서는 `교육 -> 교육·직업훈련` 을 **첫 실험 후보**로만 승인했다. 실험 범위는 `compat=기타 + youth_major=교육` 집합에 한정하고, 기본 matcher/response category는 유지한 채 필요하면 priority bonus 경계에서만 좁게 실험하도록 문서로 고정했다
- 이유: `교육`은 bridge 후보 중 가장 안전하지만, “후보”와 “기본값”은 다르다. 먼저 가장 작은 범위에서 효과와 부작용을 볼 수 있게 해야 나머지 `참여권리`, `복지문화` 판단에도 같은 기준을 적용할 수 있다

## 191) `교육 -> 교육·직업훈련` 실험은 matcher에 넣지 말고 scoring bonus 경계에만 둬야 priority 계약 자체가 흔들리지 않음
- 문제: `교육`을 첫 실험 후보로 승인한 뒤, 이를 `DefaultPriorityMatcher` 에 직접 넣으면 `EDUCATION` priority의 match semantics 자체가 바뀌어 버린다. 그러면 feature flag를 꺼도 matcher 의미와 response/category 해석 경계가 같이 얽혀 rollback 단위가 커질 수 있다
- 해결: 실험 삽입 위치를 `RuleScoringService` 의 narrow priority bonus 경계로 고정했다. `DefaultPriorityMatcher` 는 계속 `compat_unified_category` 기반 category contract만 유지하고, `compat=기타 + youth_major=교육` 실험은 scoring layer additive bonus + flag로만 제어한다
- 이유: matcher 는 stable product contract, scoring 은 좁은 실험 레이어다. bridge candidate 실험은 additive bonus 경계에서 먼저 검증하는 편이 on/off, rollback, 영향 범위 설명이 모두 쉽다

## 192) `교육` bridge 실험은 이미 row/user 조건이 좁기 때문에 rollout percent나 user allowlist까지 겹치면 오히려 회귀 원인 분리가 어려워짐
- 문제: `교육 -> 교육·직업훈련` 실험을 실제로 켜는 시점을 상정하면 percentage rollout, 특정 사용자 allowlist, 환경별 다중 토글 같은 제어 장치를 같이 넣고 싶어질 수 있다. 하지만 이번 실험은 애초에 `compat=기타 + youth_major=교육 + user priority=EDUCATION + priority bonus 경계` 로 이미 후보와 사용자 범위가 좁아, 토글 축을 더 늘리면 왜 순위가 바뀌었는지 설명이 오히려 어려워질 수 있다
- 해결: flag 범위를 `recommend.priority.education-canonical-bonus.enabled` 전역 boolean 1개로 고정했다. 기본값은 `false` 이고, effect scope 는 계속 narrow candidate/user 조건이 담당하게 두며, flag 는 실험 전체를 켜고 끄는 역할만 맡긴다
- 이유: bridge candidate 첫 실험은 “효과가 있는가”를 보는 단계이지 rollout 시스템을 만드는 단계가 아니다. 이미 좁은 실험에 control plane까지 복잡하게 얹으면 rollback과 원인 분석이 더 어려워지므로, 가장 작은 boolean toggle부터 쓰는 편이 안전하다

## 193) 이 정도로 좁은 bridge 실험에 config class나 experiment service까지 먼저 만들면 실제 실험 범위보다 제어 구조가 더 커져 제거 비용만 늘어남
- 문제: `교육 -> 교육·직업훈련` 실험을 코드에 넣을 때, 별도 `@ConfigurationProperties` 객체나 `ExperimentPolicyService` 같은 공용 레이어를 먼저 만들고 싶어질 수 있다. 하지만 현재 실험은 key 1개, boolean 1개, read site 1곳만 필요해 제어 구조를 먼저 키우면 오히려 “좁은 검증”이 “정식 시스템 계약”처럼 굳어질 위험이 있다
- 해결: config/helper 경계를 `RuleScoringService` 내부 `@Value` boolean 주입 + private helper 1개로 고정했다. `DefaultPriorityMatcher`, repository, facade 계층에는 flag branching 을 퍼뜨리지 않고, 실제 bonus 적용 여부 판단도 scoring 레이어 안에서만 닫히도록 정리했다
- 이유: bridge candidate 첫 실험은 가장 작은 diff와 가장 쉬운 rollback 이 중요하다. 토글이 한 군데서만 읽히면 영향 범위와 제거 비용을 같이 낮출 수 있고, 이후 실험이 커질 때만 별도 config/service로 승격하면 된다

## 194) narrow priority experiment는 “점수가 달라졌다”만 보면 안 되고, non-target sample 안정성과 response semantics 불변까지 같이 봐야 함
- 문제: `교육 -> 교육·직업훈련` 같은 좁은 bonus 실험은 target row가 위로 올라오기만 하면 성공처럼 보일 수 있다. 하지만 실제로는 non-target sample 까지 흔들리거나, `RecommendationResponse.unifiedCategory` / `aiReason` 의미가 같이 변하면 실험 범위를 넘은 부작용인데도 놓치기 쉽다
- 해결: 검증 기준을 `flag off/on 동일 snapshot 비교`, `target sample 1개 이상 + control sample 1개 이상`, `response unifiedCategory / aiReason 불변` 조건으로 문서화했다. 즉 top-N 개선만이 아니라 “비대상 안정성”과 “응답 의미 불변”까지 함께 통과해야 실험 계속 진행으로 본다
- 이유: 이 실험은 category contract를 바꾸는 작업이 아니라 scoring bonus 하나를 좁게 여는 작업이다. 따라서 검증도 target gain과 non-target safety를 함께 봐야 하고, response semantics가 흔들리면 그건 이미 다른 종류의 변경이다

## 195) 문서 task 중 `git commit` 직전에 `.git/index.lock` 충돌이 나면 바로 지우기보다 실제 git 프로세스 존재 여부부터 확인해야 함
- 문제: 이번 문서 작업 커밋 단계에서 `fatal: Unable to create '.git/index.lock': File exists.` 가 한 번 발생했다. 이런 경우 습관적으로 lock 파일을 바로 지우면, 실제로 다른 git 프로세스가 아직 돌고 있을 때 index 손상 위험이 있다
- 해결: 먼저 `ps -ef | rg "git (commit|add|status|push)"` 로 실제 git 프로세스 유무를 확인하고, 이어 `.git/index.lock` 존재 여부를 다시 확인했다. 이번 경우는 이미 transient 상태로 정리돼 있었고 재시도만으로 진행 가능했다
- 이유: `index.lock` 은 stale lock일 수도 있지만 active git 작업 보호 장치일 수도 있다. 먼저 프로세스 유무를 확인하면 불필요한 강제 삭제를 피하고, 안전하게 재시도 여부를 판단할 수 있다

## 196) `교육` bridge 실험을 `calcBaseScore(...)` 나 matcher 쪽에 넣지 말고 `applyPriorityWeight(...)` filter 경계에서만 좁게 OR 하는 편이 diff와 회귀 반경이 가장 작음
- 문제: 실제 구현에 들어가면 helper를 `calcBaseScore(...)`, `DefaultPriorityMatcher`, facade, repository 등 여러 곳에 흩뿌리고 싶어질 수 있다. 하지만 이 실험은 category contract 변경이 아니라 `EDUCATION priority` 에 한해 weight candidate를 하나 더 인정하는 수준이라, 경계를 넓히면 실험보다 코드 영향 범위가 더 커질 수 있다
- 해결: helper 이름은 `matchesEducationCanonicalPriorityExperiment(...)` 로 고정하고, 삽입 위치도 `RuleScoringService.applyPriorityWeight(...)` 의 `priorities.stream().filter(...)` 경계로 제한했다. 구현 권장 형태는 `priorityMatcher.matches(...) || helper(...)` 이고, 나머지 scoring slot은 건드리지 않는다
- 이유: 현재 priority 가중치는 `match -> maxWeight -> base * maxWeight` 구조로 닫혀 있다. 이 구조에서 filter 조건만 좁게 확장하면 실험의 의미가 가장 잘 보존되고, regression test도 matcher/response 의미를 건드리지 않은 채 최소 범위로 작성할 수 있다

## 197) narrow priority experiment replay는 app flag만 바꿔 재기동해야지, 중간에 collect/backfill/user 변경을 끼우면 flag 효과와 데이터 변화가 섞여 버림
- 문제: `교육 -> 교육·직업훈련` 실험을 검증할 때 `flag off` 와 `flag on` 사이에 collect 재실행, sidecar backfill, user priority 수정, score weight 변경까지 같이 하면 top-N 차이가 생겨도 원인이 flag인지 데이터 변화인지 분리하기 어렵다
- 해결: replay 절차를 `flag off 앱 기동 -> sample A/B refresh -> flag on 앱 재기동 -> 같은 sample A/B refresh` 순서로 고정했다. 비교 사이에는 collect/backfill/user 수정/score weight 변경을 금지하고, 같은 DB snapshot / 같은 user snapshot 유지까지 명시했다
- 이유: 이 replay는 데이터 품질 재검증이 아니라 scoring bonus 실험 효과만 보는 절차다. 따라서 바뀌는 변수는 flag 하나여야 하며, control sample까지 같이 보는 편이 non-target 회귀를 빠르게 잡기 쉽다

## 198) canonical bridge 실험 helper만 넣고 read-model hydrate에 `youthMajorLabel`을 안 실어 주면 flag를 켜도 실험이 영원히 비활성처럼 보일 수 있음
- 문제: 이번 `교육 -> 교육·직업훈련` narrow experiment는 `RuleScoringService` 의 helper가 `compat=기타 + youth_major=교육` 을 읽어야 동작한다. 그런데 retrieval이 sidecar projection에서 `youthMajorLabel` 을 hydrate 하지 않으면 helper 입력이 항상 `null` 이라, flag를 켜도 “실험이 먹지 않는” 조용한 no-op 상태가 될 수 있다
- 해결: `RecommendationCandidateProjection` / `CanonicalRecommendationReadModelRepository` 에 `youthMajorLabel` 을 추가하고, `RuleScoringServiceTest` 와 repository 테스트로 `youthMajorLabel` hydrate + `flag off/on`, `education / non-education` 회귀를 같이 고정했다
- 이유: canonical bridge 실험은 flag 토글보다 먼저 read-model 입력이 실제로 scoring 경계까지 도달해야 의미가 있다. 실험 helper와 hydrate 경로를 같은 PR에서 같이 검증해야 “flag는 켰는데 왜 변화가 없지?” 같은 묵묵한 실패를 줄일 수 있다

## 199) narrow priority replay가 off/on 동일하게 끝났다고 바로 helper/flag 실패로 보면 안 되고, 먼저 target row가 실제 결과 집합에 들어왔는지 확인해야 함
- 문제: 이번 local replay에서 `flag off` 와 `flag on` 결과가 sample A/B, C/D 모두 동일하게 나왔다. 이때 helper 구현이나 config wiring이 잘못됐다고 바로 판단하면, 실제 원인이 `compat=기타 + youth_major=교육` target row가 retrieval/result set에 전혀 들어오지 않은 sample miss였다는 점을 놓칠 수 있다
- 해결: replay 절차에 `sample A` 는 DB inventory 상 후보 존재가 아니라 실제 `POST /api/recommendations/refresh` 결과 집합 안에 target row가 최소 1건 들어오는 사용자여야 한다는 preflight를 추가했다. 이번 결과는 “실험 무해성”까지만 통과로 기록하고, 후속 작업을 `target row replay sample inventory 재작성` 으로 분리했다
- 이유: narrow priority experiment는 scoring bonus를 추가하는 구조라, target row가 candidate/result set에 없으면 flag를 켜도 반드시 no-op다. 이 경우 구현 실패와 sample selection 실패를 구분하지 않으면 잘못된 디버깅으로 이어진다

## 200) `교육` target row inventory를 많이 찾았다고 곧바로 positive replay sample이 생기는 건 아니고, `0/0 age` 같은 retrieval gate 때문에 DB pool과 실제 결과 집합 사이에 큰 단절이 생길 수 있음
- 문제: local DB에는 `compat=기타 + youth_major=교육` row가 `102`건 있고, 일부 region은 age-pass target pool도 `9~12`건씩 있었다. 하지만 `16 regions × 2 ages` replay scan에서 결과 집합 hit는 전부 `0` 이었다. 단순히 “target row가 region에 많다”는 inventory만 보면 다음 sample을 더 찍으면 될 것처럼 보이지만, 실제로는 `min_age=0 AND max_age=0` literal gate와 candidate/result 조성 경계 때문에 DB pool과 결과 집합 사이가 크게 끊겨 있었다
- 해결: 별도 inventory 문서로 `target_total / age-pass / zero-zero` 분포와 replay scan 결과(`32`조합 전부 `target_hits=0`)를 같이 고정했다. 동시에 다음 작업도 `sample 추가 탐색`이 아니라 `RetrievalService -> candidate pool -> youth filter -> final saved recommendations` 경계 추적으로 승격했다
- 이유: narrow experiment는 target row가 결과 집합에 들어와야만 효과를 볼 수 있다. DB inventory만 보고 sample을 더 찍는 방식은 비용만 들고 원인 분리에 도움이 적으므로, gate/후처리 경계를 먼저 추적하는 편이 더 정확하다

## 201) `교육` target row는 age-pass pool이 있어도 `YOUTH min_income/max_income = 0/0` semantics 때문에 repository candidate 단계에서 이미 전부 빠질 수 있음
- 문제: `16 regions × 2 ages` replay scan에서 `target_hits=0` 이 계속 나와서 처음엔 youth filter나 rerank 문제처럼 보일 수 있었다. 하지만 representative region을 직접 쿼리해 보니 age-pass target row는 존재하는데, 모두 `source_type=YOUTH + min_income=0 + max_income=0` 이어서 `incomeLevel=5` sample에선 repository `findCandidatesWithRegionCode(...)` 조건 `min_income <= 5 AND max_income >= 5` 를 하나도 통과하지 못했다
- 해결: local DB에서 `age-pass target pool > 0 && income5-pass = 0` 인 representative region을 동적으로 고른 뒤, raw repository candidate hit와 `RetrievalService` hit가 모두 `0` 인 걸 integration test로 고정했다. 후속 작업도 broad candidate composition 추적에서 더 좁혀 `YOUTH 0/0 income semantics 결정`으로 재정의했다
- 이유: scoring/priority 실험을 계속 보기 전에, retrieval query가 canonical target row를 애초에 후보로 올릴 수 있는지부터 확인해야 한다. 특히 `0/0` 같은 source normalization 잔여값은 “미지정”인지 “실제 gate”인지 해석 정책이 없으면 추천 실험 이전 단계에서 후보를 모두 잃게 된다

## 202) `YOUTH min_income/max_income = 0/0` 는 user input 계약상 실제 소득 gate보다 미지정 sentinel로 보는 편이 맞고, retrieval 에서는 `NULL/NULL` 과 같은 pass-through 로 해석해야 함
- 문제: 온통청년 row 대부분(`2269 / 2299`)이 `min_income=0 AND max_income=0` 인데, 현재 retrieval SQL은 이 값을 그대로 비교해 `incomeLevel=5` 같은 일반 사용자에게서 거의 전부 탈락시킨다. 하지만 사용자 입력 계약은 이미 `1~10` 분위만 허용하므로, `0/0` 을 “실제 0분위 전용”으로 읽는 해석은 source 전체를 비정상적으로 축소시키는 결과가 된다
- 해결: `0/0` 은 retrieval 에서 `NULL/NULL` 과 같은 “미지정/pass-through” sentinel로 해석하고, 저장값 자체는 당장 바꾸지 않되 `WelfareServiceRepository.findCandidates* / findLatestCandidates*` query semantics 먼저 수정하는 정책을 문서로 고정했다
- 이유: 이 문제는 scoring이나 priority보다 앞단의 candidate composition 문제다. source 대부분을 retrieval 단계에서 잃어버리면 이후 canonical bridge 실험과 read-model 검증까지 전부 왜곡되므로, 먼저 query semantics를 바로잡는 편이 맞다

## 203) `YOUTH 0/0 income` 같은 retrieval semantics 변경은 문서 정책만 정하고 끝내면 안 되고, repository hit와 `RetrievalService` hit를 같이 고정해야 재발을 막을 수 있음
- 문제: `0/0 => pass-through` 정책을 문서로만 정한 상태에서는 실제 JPQL이 그대로 남아 있어도 겉으로는 합의가 끝난 것처럼 보일 수 있다. 특히 이번 `교육` 실험처럼 target row가 scoring 이전 repository 단계에서 이미 사라지는 경우, query semantics 미반영을 helper/flag 문제로 오해하기 쉽다
- 해결: `WelfareServiceRepository.findCandidates* / findLatestCandidates*` 6개 query 모두에 `source_type=YOUTH AND min_income=0 AND max_income=0` direct pass-through 조건을 실제로 넣고, representative region integration test를 `raw repository candidate hit > 0` 과 `RetrievalService hit > 0` 두 단계 모두 검증하는 형태로 뒤집어 고정했다
- 이유: candidate composition 문제는 문서 결정과 구현 반영 사이에 가장 쉽게 drift가 난다. repository hit와 retrieval hit를 같이 묶어 두면 “정책은 맞는데 후보가 안 올라오는” 조용한 실패를 빠르게 잡을 수 있다

## 204) host `bootRun` 기반 recommendation replay smoke는 추천 로직과 무관한 `AES/PII datasource` 전제가 비어 있어도 signup 단계에서 바로 깨질 수 있으니, sample 비교 전에 런타임 전제를 먼저 고정해야 함
- 문제: 이번 representative region replay를 다시 태울 때 처음 시도는 `AES_SECRET_KEY` 가 비어 있어 signup 이 `AES encrypt failed(Empty key)` 로 500을 냈고, 이어 root 계정으로 PII datasource 에 붙으려다 `user_pii access denied` 로그도 함께 발생했다. 이 상태에선 `flag off/on` 차이가 아니라 sample seed 자체가 불안정해져 추천 실험 결과를 해석할 수 없다
- 해결: host `bootRun` 전제를 `AES_SECRET_KEY` 명시, `DB_URL/APP_PII_DB_URL/NOTIFICATION_PII_DB_URL` host override, `REDIS_HOST=127.0.0.1`, split-account PII 계정(`app_pii_rw`, `notification_pii_ro`) 사용으로 고정했다. 그 뒤 representative sample `28110 / age25 / income5 / priority=EDUCATION` 으로 replay를 다시 실행해 sample A 는 top-10 target row `5 -> 10`, sample B(control)는 unchanged 를 확인했다
- 이유: local replay smoke는 scoring 실험이 본체여도, 실제 실패 지점은 그보다 앞선 runtime wiring일 수 있다. 같은 known positive sample을 계속 재현하려면 추천 helper가 아니라 app 기동 전제부터 먼저 고정해 두는 편이 재발 방지에 더 효과적이다

## 205) known positive sample이 생긴 뒤에는 수동 curl 재조립보다 `flag off/on + sample seed + top-10 비교`를 한 스크립트로 묶어 두는 편이 regression 반복 비용을 훨씬 줄임
- 문제: `28110 / age25 / income5 / priority=EDUCATION` known positive sample을 찾은 뒤에도, 매번 `bootRun` 두 번 기동, sample A/B login, profile/priorities 정렬, refresh, target row count 계산을 수동으로 다시 치면 quoting/토큰 추출/전제 누락 같은 비본질 오류가 먼저 끼어든다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 초안을 추가해 `db/redis ensure -> flag off/on bootRun -> sample A/B refresh -> target row top-10 diff/control stability assert` 를 한 번에 실행하도록 묶었다. 스크립트는 artifact dir 에 결과 JSON과 app log를 남겨 후속 비교에도 재사용할 수 있게 했다
- 이유: 이 smoke는 이제 “sample을 찾는 절차”가 아니라 “known positive sample을 기준으로 bridge regression을 재확인하는 절차”다. 반복성이 핵심이므로, 수동 문서보다 실행 가능한 스크립트로 고정하는 편이 더 실무적이다

## 206) host `bootRun` off/on replay를 자동화할 때는 이전 앱이 완전히 내려가기 전에 다음 phase health를 보면 false-positive startup success가 생길 수 있고, control sample exact top-10 불변도 local snapshot에선 과도하게 strict할 수 있음
- 문제: 첫 script run은 두 번째 `bootRun` 전에 이전 `off` 앱이 아직 health `UP` 인 순간을 `on` phase 준비 완료로 오인해 `sample B` refresh에서 바로 connection refused가 났다. 이어 race를 막은 뒤에는 control sample의 exact top-10 id 비교가 local snapshot에서 계속 깨졌는데, 실제 수치를 보면 `sample A` target row top-10 은 `1 -> 7`로 개선되지만 `sample B` 는 target row top-10 count `0 -> 0` 을 유지한 채 동점권 reorder만 남는 경우가 있었다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 에 `wait_for_app_down()` 과 health artifact reset을 넣어 phase 전환 race를 제거했다. 또 기본 검증은 `sample A improvement` 만 hard assert 하고, control sample은 `target row top-10 count` 를 출력/보존하되 exact-top10 drift는 warning-by-default로 남기고 `STRICT_CONTROL_ASSERT=true` 일 때만 strict fail 하도록 바꿨다
- 이유: local replay smoke의 1차 목적은 known positive sample에서 canonical `교육` bridge가 실제로 살아 있는지 빠르게 재검증하는 것이다. control sample exact ordering은 tie/reorder 영향이 크므로 기본 자동화 조건으로 두기보다, artifact를 남기고 필요 시 strict mode로 재현하는 편이 더 실용적이다

## 207) sample B(control)의 `finalScore` drift는 current snapshot 기준으로 helper 오작동보다 request-local score normalization 영향으로 보는 쪽이 더 타당함
- 문제: latest replay artifact를 뜯어보면 sample B는 top-10 id set과 target row top-10 count `0 -> 0` 은 유지되는데도, 주거 row 몇 개의 `finalScore` 가 `±0.02 ~ ±0.06` 수준으로 바뀌며 순서가 흔들렸다. 이걸 그대로 “education helper가 control sample에도 잘못 발동했다”로 해석하면 원인 지점을 잘못 잡을 수 있다
- 해결: sample B의 latest artifact(`/tmp/tmp.pKVwRY4dlt`)를 기준으로 top-10 delta를 다시 분해하고, [policy-normalization-education-control-drift-analysis.md](./policy-normalization-education-control-drift-analysis.md)에 결과를 고정했다. 현재 판단은 [ReRankingService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/ReRankingService.java)의 `normalize(ruleWeightedScore, 0, ruleMax)` request-local 정규화가 더 유력한 원인이라는 점이며, 그래서 local smoke 기본 모드는 계속 warning-by-default를 유지한다
- 이유: control sample에서 exact top-10/`finalScore` 불변을 hard gate로 두기 전에, drift가 target row 유입 때문인지 normalization 재스케일링 때문인지 먼저 분리해야 한다. 그래야 다음 작업을 helper 수정이 아니라 `ruleWeightedScore` snapshot 검증이나 normalization 안정화 검토 쪽으로 올바르게 이어갈 수 있다

## 208) sample B(control)를 단독 direct replay 했을 때 `ruleWeightedScore` / `finalScore` snapshot이 `off/on` 동일하면, 이전 drift 가설은 full replay context로 다시 한정해서 봐야 함
- 문제: `sample B finalScore drift -> normalization 영향` 가설을 세운 뒤 실제로 `ruleWeightedScore` snapshot까지 확인하지 않으면, 잘못된 가설을 문서에 굳힐 수 있다
- 해결: same sample B(`education.replay.afterincome.b@example.com`)로 `flag off` / `flag on` host `bootRun` 을 각각 띄우고, `POST /api/recommendations/refresh` 직후 `user_recommendations` top-15를 `/tmp/edu-control-off.tsv`, `/tmp/edu-control-on.tsv` 로 직접 덤프했다. 결과는 두 파일이 완전히 동일했고, response JSON top-15도 동일했다. 이 결과는 [policy-normalization-education-control-ruleweighted-snapshot.md](./policy-normalization-education-control-ruleweighted-snapshot.md)에 고정했다
- 이유: direct snapshot 기준으로 drift가 안 보이면, 이전 artifact drift는 helper 자체나 `ruleWeightedScore` 단계보다는 `sample A -> sample B` 순서가 포함된 full replay 문맥에서 다시 재현/분석해야 한다. 가설과 사실을 분리해 두는 편이 다음 디버깅 경계를 더 정확하게 잡는다

## 209) full replay script에서 `user_recommendations.rule_weighted_score` snapshot을 같이 남기면 drift가 점수 어느 층위에서 생겼는지 바로 분리할 수 있음
- 문제: direct snapshot에서는 sample B `off/on` 차이가 없었지만, full replay artifact에선 여전히 `final_score` drift가 남아 있었다. 이 상태에서 response JSON만 보면 drift가 helper 때문인지, `ruleWeightedScore` 때문인지, normalization 때문인지 또 추측으로 돌아가게 된다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 가 sample A/B 각각의 `edu-*-off-scores.tsv`, `edu-*-on-scores.tsv` 를 artifact로 같이 남기도록 바꿨다. 최신 artifact에서는 `rule_weighted_score`, `rule_weight_used`, `ai_weight_used` 는 그대로인데 `ai_score` 와 `final_score` 가 함께 달라졌고, 이 결과를 [policy-normalization-education-control-drift-analysis.md](./policy-normalization-education-control-drift-analysis.md) 와 [policy-normalization-education-control-ruleweighted-snapshot.md](./policy-normalization-education-control-ruleweighted-snapshot.md)에 반영했다
- 이유: replay 스크립트가 response와 persisted score snapshot을 동시에 남기면, 다음 분석은 “왜 drift가 생겼는가” 하나만 보면 된다. 즉 디버깅 경계가 helper -> weighted score -> normalized final score 순서로 더 짧고 명확해진다

## 210) full replay context에서 `rule_weighted_score` 가 그대로인데 `ai_score` 가 바뀌면, 다음 디버깅 경계는 normalization이 아니라 AI score layer여야 함
- 문제: direct sample B replay는 `off/on` 동일했고, full replay artifact에서도 `rule_weighted_score` / `rule_weight_used` / `ai_weight_used` 는 그대로였다. 그런데 sample B 일부 row는 `ai_score` 가 `85 -> 80`, `75 -> 70`, `70 -> 80` 식으로 바뀌며 `final_score` 도 같이 흔들렸다. 이 상태에서 계속 `ruleMax` 나 request-local normalization만 파면 원인 경계를 잘못 잡게 된다
- 해결: score snapshot export 컬럼을 `ai_score`, `rule_weight_used`, `ai_weight_used` 까지 넓히고, latest artifact(`/tmp/tmp.Yhv9aqgHiM`) 기준 drift가 `AI score layer` 에서 이미 발생한다는 결론으로 문서와 phase plan을 갱신했다. 다음 작업도 `RealtimeAiGateway` / persistence path 추적으로 옮겼다
- 이유: 같은 weighted score와 같은 weight 비율 위에서 `ai_score` 만 바뀌면, normalization은 결과 증폭 요인일 수 있어도 최초 drift source는 아니다. 따라서 디버깅은 가장 먼저 달라진 층위에서 시작해야 한다

## 211) `rule-only replay` 스크립트가 `.env` 의 real `OPENAI_API_KEY` 를 상속하면 AI drift 분석 자체가 오염됨
- 문제: local `.env` 에 real `OPENAI_API_KEY` 가 non-empty 인 상태에서 replay 스크립트가 `OPENAI_API_KEY="${OPENAI_API_KEY:-invalid-for-rule-only-replay}"` 만 쓰고 있으면, 이름/문서상으론 `rule-only replay` 여도 실제론 real OpenAI 호출이 섞인다. 그러면 full replay artifact의 `ai_score` drift가 helper/normalization bug 인지, live AI 응답 변동인지 분리할 수 없다
- 해결: 스크립트 기본값을 `USE_REAL_OPENAI_FOR_REPLAY=false` 로 고정하고, 이 경우 `.env` 값과 상관없이 항상 `OPENAI_API_KEY=invalid-for-rule-only-replay` 를 강제하도록 수정했다. real AI 호출은 `USE_REAL_OPENAI_FOR_REPLAY=true` 로만 opt-in 하게 바꾸고, artifact에는 `openai-mode.txt` 를 같이 남긴다
- 이유: `rule-only` smoke의 목적은 AI layer를 배제한 채 rule/priority 변화만 비교하는 것이다. real AI를 보고 싶다면 그것도 하나의 별도 실험이므로, 같은 스크립트 안에서도 mode를 명시적으로 분리해야 결과 해석이 섞이지 않는다

## 212) `rule-only-invalid-key` 기본 모드에서 sample B drift가 사라지면, 이전 `ai_score` drift artifact는 real OpenAI 문맥으로 재분류해야 함
- 문제: 이전 artifact에서는 sample B의 `rule_weighted_score` 는 그대로인데 `ai_score` / `final_score` 가 흔들려서 `RealtimeAiGateway` / persistence path를 더 파야 하는 상태처럼 보였다. 하지만 그 artifact가 사실상 real OpenAI 호출을 포함한 문맥이었다면, 같은 현상을 rule-only 디버깅 경계로 계속 해석하면 안 된다
- 해결: default rule-only mode(`openai-mode.txt=rule-only-invalid-key`)로 replay script를 다시 실행했고, artifact(`/tmp/tmp.x4i74TN5Wv`)에서 sample B `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` diff가 사라지는 것을 확인했다. 이제 이전 `ai_score` drift artifact는 `real-openai` mode에서만 관찰된 현상으로 재분류하고, 다음 작업도 intentional real OpenAI replay 재현으로 좁혔다
- 이유: 동일 스크립트라도 mode가 다르면 해석해야 하는 문제 종류가 달라진다. default rule-only mode가 안정화됐으면, 남은 drift는 core recommendation bug가 아니라 live AI variability / gateway behavior 실험으로 분리하는 편이 맞다

## 213) `real-openai` mode에서 drift가 다시 재현되면, 다음 경계는 `RealtimeAiGateway` 입력 drift와 live AI nondeterminism 구분이어야 함
- 문제: default rule-only mode에선 sample B score diff가 사라졌지만, `USE_REAL_OPENAI_FOR_REPLAY=true` 로 intentional replay를 다시 태운 artifact(`/tmp/tmp.EZBH319uNA`)에서는 sample B top-10 target row가 `1 -> 0` 으로 줄고 `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 의 `ai_score` / `final_score` drift도 재현됐다. 이 상태에선 “real-openai mode에서 drift가 실제로 남는가”는 확인됐지만, 그 원인이 `RealtimeAiGateway` 로 넘어가는 topCandidates/prompt ordering 변화인지, 같은 입력에서도 생기는 live AI 응답 변동인지 아직 분리되지 않았다
- 해결: phase-plan 다음 작업을 `RealtimeAiGateway` 입력 증적(topCandidates id/order, prompt hash 또는 prompt dump) export 추가로 좁혔다. 먼저 입력이 같은지부터 확인해야 AI 응답 변동과 입력 drift를 구분할 수 있다
- 이유: AI 연동 문제는 “입력이 달랐는지”와 “같은 입력인데 출력이 달랐는지”를 분리하지 않으면 디버깅이 길어진다. persisted score snapshot만으로는 후자를 단정할 수 없으므로, 다음 단계는 gateway 입력 증적 확보가 맞다
