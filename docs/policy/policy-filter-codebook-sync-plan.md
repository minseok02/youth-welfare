# Policy Filter Codebook Sync Plan

## 목적

정책 검색 필터의 option/codebook 값이 프론트와 백엔드에 나뉘어 있는 상태를 어떻게 관리할지 고정합니다.

현재 결론은 공개 runtime API를 즉시 추가하지 않고, 기존 상수와 contract test를 유지하면서 전환 조건을 명시하는 것입니다.

## POLICY_FILTER_CODEBOOK_CURRENT_CONTRACT

현재 정책 필터 codebook의 기준 소스는 아래처럼 분리되어 있습니다.

- 프론트 UI option source: `frontend/src/lib/policyFilterOptions.js`
- 프론트 계약 테스트: `frontend/src/lib/policyFilterOptions.test.js`
- 백엔드 Gov24 service field source: `Gov24ServiceFieldSupport.managedLabels()`
- 백엔드 Gov24 user type source: `Gov24UserTypeSupport.managedTokens()`
- 백엔드 Gov24 benefit type source: `Gov24BenefitTypeSupport.managedTokens()`
- 백엔드 계약 테스트: `Gov24PolicyFilterSupportTest`

이 계약은 `Gov24 서비스분야`, `Gov24 사용자구분`, `Gov24 지원유형`, source option mapping, 정렬 option, status filter mapping을 보호합니다.

## POLICY_FILTER_CODEBOOK_NOT_RUNTIME_API_YET

`GET /api/policies/filter-codebooks` 같은 공개 runtime API는 지금 만들지 않습니다.

보류 이유는 다음과 같습니다.

- 필터 UI는 첫 렌더에서 동기적으로 option을 구성해야 합니다.
- 현재 값은 운영자가 자주 바꾸는 설정값이 아니라, stable curated UX label과 managed token입니다.
- 프론트/백엔드 drift는 이미 `policyFilterOptions.test.js`와 `Gov24PolicyFilterSupportTest`가 잡습니다.
- 기존 `OfficialCodebookReadService`와 `/api/reference/official-codes`는 admin/reference 용도의 공식 코드북 조회이며, 공개 정책 검색 필터 option API로 읽지 않습니다.
- runtime API를 추가하면 캐시, fallback, loading 상태, 장애 시 필터 비활성화 기준까지 새 운영 표면이 생깁니다.

## POLICY_FILTER_CODEBOOK_GENERATED_CONSTANTS_CANDIDATE

중복을 줄일 때의 1순위 후보는 runtime API가 아니라 build-time generated constants 입니다.

전환 조건은 아래 중 하나입니다.

- `Gov24 serviceField/userType/benefitType` 외에 정책 필터 codebook 축이 추가됩니다.
- 같은 option 값이 한 달 안에 두 번 이상 바뀝니다.
- 프론트와 백엔드 계약 테스트가 같은 drift를 반복해서 잡습니다.
- 백엔드 support class 또는 resource JSON에서 프론트 JS/JSON artifact를 생성해도 UI의 동기 렌더 계약을 유지할 수 있습니다.

생성물은 repository에 checked-in 합니다. 프론트는 계속 동기 import를 사용하고, CI는 generator 실행 결과와 checked-in artifact가 같은지 확인합니다.

## POLICY_FILTER_CODEBOOK_PUBLIC_API_CANDIDATE

공개 runtime API는 아래 조건이 생길 때 다시 검토합니다.

- codebook이 tenant, region, admin 설정에 따라 runtime에서 달라집니다.
- 운영자가 배포 없이 option을 변경해야 합니다.
- 공식 코드북 import가 안정화되어 canonical code와 UX label을 runtime에서 함께 내려야 합니다.
- 필터 축이 충분히 많아져 프론트 bundle 상수로 관리하는 비용이 API 장애 처리 비용보다 커집니다.

후보 shape는 다음을 기준으로 합니다.

```http
GET /api/policies/filter-codebooks
```

```json
{
  "version": "policy-filter-codebooks-v1",
  "sourceOptions": [],
  "gov24": {
    "serviceFields": [],
    "userTypes": [],
    "benefitTypes": []
  },
  "sortOptions": [],
  "statusFilters": []
}
```

runtime API로 전환하더라도 프론트에는 마지막 정상 응답 또는 checked-in fallback이 있어야 합니다.

## POLICY_FILTER_CODEBOOK_REOPEN_CONDITIONS

이 문서는 아래 사건이 생기면 다시 엽니다.

- Gov24 안정 공식 codebook import가 들어와 현재 managed label/token을 대체할 수 있습니다.
- `policyFilterOptions.test.js` 또는 `Gov24PolicyFilterSupportTest`가 같은 종류의 drift를 두 번 이상 잡습니다.
- 정책 검색 필터에 새 source-specific 축이 추가됩니다.
- `/api/reference/official-codes`의 사용자가 admin/reference를 넘어 공개 필터 option까지 요구합니다.

## POLICY_FILTER_CODEBOOK_VERIFICATION

현재 계약 검증은 아래 명령으로 닫습니다.

```bash
bash deploy/smoke/verify-policy-filter-codebook-sync-plan.sh
cd backend && ./gradlew test --tests com.example.welfare.global.config.PolicyFilterCodebookSyncPlanContractTest --tests com.example.welfare.policy.support.Gov24PolicyFilterSupportTest
cd frontend && npm run test:unit
```
