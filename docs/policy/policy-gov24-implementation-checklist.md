# Gov24 작업과 검증 체크리스트

현재 구현/보류 계약은 [Gov24 데이터 계약](policy-gov24-lane-closeout.md), 공통 신규 source 절차와 코드 진입점은 [온보딩 가이드](policy-source-onboarding-checklist.md)를 확인합니다. 아래 절차는 list/detail/support가 이미 구현된 코드에서 변경 범위를 나눠 검증하기 위한 기준입니다.

## 작업 범위

시작할 때 목표, 이번에 변경할 경로/파일, 제외할 경로, 앱/DB 대상과 완료 조건을 기록합니다. 과거 “1차에는 list만 구현” 단계를 현재 미구현 목록으로 읽지 않습니다.

- identity는 title이 아닌 `서비스ID`입니다. 지원조건을 별도 정책 row로 만들지 않습니다.
- adapter/client·payload snapshot·mapper/saver·sidecar를 확인하고 새 source 분기를 controller/service에 흩뿌리지 않습니다. enum·registry·batch의 실제 source별 분기는 함께 점검합니다.
- 공식 label과 내부 stable code를 구분하며 raw summary를 유지합니다. taxonomy 정리가 추천 가중치·hard eligibility·배치 확대까지 자동 승인하지 않습니다.

## 변경 경로별 완료 조건

| 변경 | 확인할 코드/데이터 | 완료 조건 |
| --- | --- | --- |
| 설정·source 등록 | `PUBLIC_DATA_PORTAL_API_KEY`, CollectSource·SourceType·registry·WelfareSourceTypeSupport | env 비밀 노출 없이 기동·권한·source 경로 확인 |
| List | Gov24Client·Gov24ServiceListDto·Gov24CollectSourceAdapter·mapper/list binding | 실제 요청·snapshot·정책 upsert·부분 실패 집계 일치 |
| Detail | 서비스ID별 client/DTO/collect service | DETAIL snapshot·본문·링크·기관·법령 보강, 기존 필드 소유권 유지 |
| Support | Gov24SupportConditionsDto·fetch/save·fact extractor | SUPPORT snapshot·코드/연산자/단위·missing/NO_OP 판정 일치 |
| Taxonomy·지역 | term/summary slot·내부 코드·region 추론 | raw label 보존·managed token·unknown 처리·기존 보정과 재수집 회귀 확인 |
| 소비 경로 | API filter·read-model·priority/soft bonus·화면 | GET 목록/POST 검색·상세 discovery·추천 회귀, hard gate 오용 없음 |

한 경로의 smoke 성공으로 다른 경로의 구현·전건 적재까지 완료 처리하지 않습니다. 후속 embedding 실패와 list diff 누락은 수집 응답 성공과 별도 판정합니다.

## 실행과검증

[환경 안내](../core/environment-guide.md)에서 실행 위치·앱·DB·Redis와 권한을 확인합니다. 아래 acceptance/backfill smoke는 데이터 변경·계정/fixture 생성·외부 호출을 포함할 수 있으며 정기 read-only 조회로 실행하지 않습니다. 스크립트의 실제 입력과 승인한 작업 범위를 먼저 확인합니다.

```bash
# 저장 상태·shape·결측 조회도 대상/인증 및 artifact 영향을 먼저 확인합니다.
bash deploy/smoke/run-local-gov24-quality-audit.sh
bash deploy/smoke/run-local-gov24-region-coverage-audit.sh
bash deploy/smoke/run-local-gov24-taxonomy-validation.sh
```

명시적으로 승인한 격리 검증에서만 다음 중 필요한 항목을 선택합니다.

```bash
bash deploy/smoke/run-local-gov24-acceptance-suite.sh
bash deploy/smoke/run-local-gov24-region-backfill-smoke.sh
bash deploy/smoke/run-local-gov24-collect-embedding-boundary-smoke.sh
```

지역 snapshot replay의 endpoint 형태는 `POST /api/admin/collect/gov24-sidecars-backfill?scope=regions&limitPerSource=0`이며, list 필수 slot 결측 복구는 `scope=list&missingOnly=true&limitPerSource=0`입니다. 모두 DB 쓰기이고 `limitPerSource=0`의 전체 처리 범위를 확인해야 합니다. 승인된 admin 인증·요청 origin과 앱/DB 격리가 없으면 호출하지 않습니다. 절차를 복사해 바로 운영에서 실행하지 않습니다.

공통 회귀는 [테스트 실행 기준](../core/testing.md)을 따릅니다. active-baseline/legacy-repair 묶음은 이름과 달리 repair·실제 외부 호출을 포함할 수 있으므로 일반 사전 조회로 자동 실행하지 않습니다. codebook 변경은 live/Java/SQL inventory와 이전/새 code 대응·backfill/rollback을 확인하고, hard eligibility 변경은 공식 근거·단위·누락/unknown·호환 테스트를 별도로 설계합니다.

## 자료 수신과 후속 판단

- 외부 codebook 자료: API 버전, field/code/공식 label, taxonomy 3축과 supportConditions를 따로 확인합니다.
- runtime 오류: 신규 재현·결측·schema drift를 먼저 분리하고 현재 [미해결 수집 문제](../current-state.md#최근-로컬-수집에서-확인된-미해결-문제)를 확인합니다.
- 완료 기록: 실행 시각·커밋·환경/대상·요청/저장/실패·후속 detail/support/embedding·artifact·검증/미검증·다음 액션을 남깁니다.
- 외부 자료 대기와 무관한 버그·보안·데이터 보존 수정은 과거 reopen 금지 문구 때문에 보류하지 않습니다.
