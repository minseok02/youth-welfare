# `YOUTH_MID` live payload inventory

2026-04-30 기준 온통청년 live 목록 응답(`GET /go/ythip/getPlcy`)을 실제로 끝까지 스캔해 `mclsfNm` / code-like field 분포를 다시 확인했다.

## 결론

- live payload에는 `srchPolyBizSecd` 라는 필드가 직접 노출되지 않았다.
- 대신 `plcyMajorCd`, `jobCd`, `schoolCd`, `sbizCd` 같은 code-like field는 보이지만, `YOUTH_MID` stable code를 대신할 정도로 좁고 안정적인 축은 아니었다.
- 따라서 현재 단계에서는 `YOUTH_MID` 를 계속 `label-only` 로 유지하고, stable code mapping은 보류한다.

## 확인 범위

- 기준 시점: `2026-04-30`
- 대상: authenticated 온통청년 live 목록 응답
- 페이지 수: `24`
- 총 row 수: `2299`
- distinct `mclsfNm`: `33`

## live `mclsfNm` inventory

아래 값들이 실제 live payload에서 관측됐다.

- `건강`
- `교육비지원`
- `권익보호`
- `기숙사`
- `문화활동`
- `문화활동 및 생활지원`
- `미래역량강화`
- `미래역량강화,온라인교육`
- `예술인지원`
- `예술인지원,문화활동 및 생활지원`
- `온·오프라인교육`
- `온·오프라인교육 ,미래역량강화`
- `온라인교육`
- `재직자`
- `재직자,권익보호`
- `재직자,취업`
- `전월세 및 주거급여 지원`
- `정책인프라구축`
- `정책인프라구축,청년참여`
- `주택 및 거주지`
- `주택 및 거주지,전월세 및 주거급여 지원`
- `창업`
- `창업,취업`
- `청년국제교류`
- `청년참여`
- `청년참여,정책인프라구축`
- `취약계층 및 금융지원`
- `취업`
- `취업,미래역량강화`
- `취업,재직자,권익보호`
- `취업,재직자,취업`
- `취업,창업`
- `취업,취업`

즉 local DB `category_sub` 에서 보이던 comma-delimited combo와 non-official variant가 live payload에도 그대로 존재한다.

## code-like field 재확인

live row에는 다음 필드들이 함께 노출됐다.

- `plcyMajorCd`
- `jobCd`
- `schoolCd`
- `sbizCd`

하지만 이 값들은 다음 이유로 `YOUTH_MID` stable code 대체 축으로 쓰기 어렵다.

- 서로 다른 `mclsfNm` 에도 같은 broad/default-like code가 반복됐다.
- 일부 field는 comma-delimited multi-code 형태로도 들어왔다.
- `mclsfNm` semantic과 1:1 대응하는 stable mid-category key로 보이지 않았다.

대표적으로 scan 중 여러 `mclsfNm` 에서 다음 broad code가 반복 관측됐다.

- `plcyMajorCd=0011009`
- `jobCd=0013010`
- `schoolCd=0049010`
- `sbizCd=0014010`

## 현재 유지 정책

- canonical `YOUTH_MID` 는 exact official label token만 적재
- comma-delimited combo는 split 후 official token만 적재
- non-official variant는 `YOUTH_MID_RAW_ALIAS` 로만 보존
- `service_taxonomies.youth_mid_label` summary는 exact official 단일 label일 때만 채움
- `normalization_codes(YOUTH_MID)` stable code import / mapping SQL은 보류

## 의미

이 live inventory로 `YOUTH_MID` label inventory 자체는 더 강하게 확인됐지만, stable code inventory는 여전히 확보되지 않았다. 따라서 다음 단계는:

1. authenticated testbed나 별도 metadata source에서 실제 `srchPolyBizSecd` inventory를 확보한다.
2. 그 전까지는 `YOUTH_MID` 를 label-only taxonomy로 유지한다.
