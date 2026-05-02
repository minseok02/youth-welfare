# 신규 Policy Source 코드 진입점

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

## 목적

이 문서는 새 source를 붙일 때

- 어디서 collect가 시작되고
- raw payload가 어디에 저장되고
- canonical sidecar가 어디서 쓰이고
- recommendation 경계는 어디까지 연결되는지

를 코드 기준으로 빠르게 찾기 위한 문서입니다.

즉 “무슨 구조를 원하나” 가 아니라
“실제로 어느 클래스를 열어야 하나” 를 정리합니다.

## 전체 흐름

현재 기준 신규 정책형 source의 코드는 아래 순서로 이어집니다.

1. admin/manual collect 진입
2. `CollectService` adapter dispatch
3. source adapter / source client 호출
4. raw payload 저장
5. `CollectItemSaver` 로 `welfare_services` 저장
6. `NormalizedPolicySidecarWriter` 로 sidecar 저장
7. 필요 시 backfill / replay
8. recommendation read-model 에서 canonical projection 조회

## 1. collect 진입점

### admin entry

- [CollectAdminController.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/controller/CollectAdminController.java)

여기서 보는 것:

- `POST /api/admin/collect/{sourceKey}`
- `POST /api/admin/collect/all`
- `POST /api/admin/collect/bokjiro-sidecars-backfill`
- `POST /api/admin/collect/bokjiro-details-gap-fill`

새 source를 수동 수집 경로에 연결하려면:

- `CollectSource`
- `CollectSourceAdapter`
- `CollectService`
- `CollectAdminController`

경계를 같이 봅니다.

### scheduled/batch entry

- [CollectService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectService.java)

핵심:

- `collectAll()` 은 새벽 2시 배치
- `collect(source)` 는 source별 단일 실행
- adapter registry 기반 dispatch

즉 새 source는 `CollectService` 본문에 case를 늘리는 구조가 아니라,
`CollectSourceAdapter` 추가로 연결하는 구조입니다.

## 2. source adapter registry

핵심 파일:

- [CollectSource.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectSource.java)
- [CollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectSourceAdapter.java)
- [AbstractListCollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/AbstractListCollectSourceAdapter.java)

현재 예시 adapter:

- [YouthCollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/YouthCollectSourceAdapter.java)
- [BokjiroCentralCollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/BokjiroCentralCollectSourceAdapter.java)
- [BokjiroLocalCollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/BokjiroLocalCollectSourceAdapter.java)
- [BokjiroDetailCollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/BokjiroDetailCollectSourceAdapter.java)
- [BokjiroDetailRefreshCollectSourceAdapter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/BokjiroDetailRefreshCollectSourceAdapter.java)

새 source가 list형 정책 source라면 보통:

- `gateway client`
- `dto`
- `CollectSource`
- `CollectSourceAdapter`

를 먼저 추가합니다.

## 3. raw payload 저장 경계

핵심 파일:

- [RawApiPayload.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/entity/RawApiPayload.java)
- [RawApiPayloadRepository.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/repository/RawApiPayloadRepository.java)
- [RawApiPayloadService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/RawApiPayloadService.java)

의미:

- list/detail/category raw payload 보존
- source별 `sourceType`, `sourceId`, `apiCategory` 로 저장
- 나중에 backfill/replay 가능

신규 source에서 먼저 봐야 할 것:

- raw를 어느 시점에 저장할지
- list/detail 을 같은 external id 로 묶을 수 있는지
- raw 저장 실패가 collect 전체를 깨지 않게 할지

## 4. collect 결과 저장 경계

핵심 파일:

- [CollectItemSaver.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java)
- [WelfareServiceMapper.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java)

의미:

- DTO -> `WelfareService`
- region/tag 저장
- source별 row upsert
- search youth relevance refresh
- canonical sidecar writer 호출

신규 정책형 source를 실제 `welfare_services` 로 넣는다면,
대부분 여기까지 연결돼야 합니다.

즉 source별 collect 구현이 끝났다는 말은:

- client 호출 성공
- raw 저장 성공
- `WelfareServiceMapper` 또는 동급 mapper 존재
- `CollectItemSaver` 경계까지 연결

을 뜻합니다.

## 5. canonical sidecar 저장 경계

핵심 파일:

- [NormalizedPolicyAggregate.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/NormalizedPolicyAggregate.java)
- [NormalizedPolicySidecarWriter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/NormalizedPolicySidecarWriter.java)
- [DeferredNormalizedPolicySidecarWriter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java)
- [NormalizedFactMergeSupport.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/NormalizedFactMergeSupport.java)

의미:

- canonical taxonomy summary 저장
- taxonomy terms 저장
- fact merge / upsert
- sidecar table이 없으면 안전하게 skip

새 source에서 canonical을 붙일 때는:

- mapper에서 `NormalizedPolicyAggregate` 를 어떻게 만들지
- `CollectItemSaver` 가 writer까지 aggregate를 넘기는지
- sidecar summary와 term/fact 중 어디까지 official field로 채울지

를 이 경계에서 봅니다.

## 6. detail refresh / backfill / replay

핵심 파일:

- [BokjiroDetailCollectService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/BokjiroDetailCollectService.java)
- [NormalizedPolicySidecarBackfillService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/NormalizedPolicySidecarBackfillService.java)

의미:

- detail payload 추가 적재
- 저장된 raw payload 기준 sidecar backfill
- canonical replay / gap-fill

새 source에서도 아래를 미리 분리해서 생각하는 게 맞습니다.

- live collect
- detail refresh
- raw 기반 sidecar backfill

한 번에 다 묶지 않습니다.

## 7. sync log / 운영 가시성

핵심 파일:

- [ApiSyncLog.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/entity/ApiSyncLog.java)
- [ApiSyncLogStatusConverter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/entity/converter/ApiSyncLogStatusConverter.java)
- [ApiSyncLogRepository.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/repository/ApiSyncLogRepository.java)
- [ApiSyncLogService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/ApiSyncLogService.java)

의미:

- source별 수집 시작/성공/실패 기록
- requested/saved/failed 집계
- manual collect 결과 추적

신규 source를 붙이면 collect 성공 여부는 먼저 여기서 보게 됩니다.

## 8. recommendation read-model 연결 경계

핵심 파일:

- [CanonicalRecommendationReadModelRepository.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java)
- [RecommendationCandidateProjection.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/dto/RecommendationCandidateProjection.java)
- [RetrievalService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RetrievalService.java)
- [RuleScoringService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)
- [DefaultPriorityMatcher.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/DefaultPriorityMatcher.java)

의미:

- `welfare_services + sidecars` 를 recommendation projection으로 읽음
- taxonomy terms / fact keys / youth major summary 를 projection에 hydrate
- retrieval/scoring/matcher 는 점진 이행 중

새 source를 붙여도 recommendation 까지 바로 연결하는 건 아닙니다.

체크:

- source가 정책형이라 추천 후보에 들어갈 수 있는가
- compat category를 current contract에 맞게 유지할 수 있는가
- canonical sidecar를 read-model hint로 먼저만 쓸 것인가

## 9. 신규 source를 붙일 때 실제로 여는 파일 순서

정책형 source 기준 추천 순서:

1. [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
2. [CollectSource.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectSource.java)
3. 기존 adapter 예시 1개
4. [RawApiPayloadService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/RawApiPayloadService.java)
5. [WelfareServiceMapper.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java)
6. [CollectItemSaver.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java)
7. [DeferredNormalizedPolicySidecarWriter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java)
8. 필요 시 [CanonicalRecommendationReadModelRepository.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java)

listing형이면:

- `CollectSourceAdapter`
- `RawApiPayloadService`
- 별도 listing domain 설계

까지만 보고 `welfare_services` 경계는 열지 않는 게 기본입니다.

## 10. 요약

1. collect 시작점은 [CollectAdminController.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/controller/CollectAdminController.java), [CollectService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectService.java) 입니다.
2. raw payload 경계는 [RawApiPayloadService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/RawApiPayloadService.java) 입니다.
3. `welfare_services` 저장 경계는 [CollectItemSaver.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java) 입니다.
4. canonical sidecar 경계는 [DeferredNormalizedPolicySidecarWriter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java) 입니다.
5. recommendation 연결은 [CanonicalRecommendationReadModelRepository.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java) 부터 봅니다.
