# 결과보고서 참고문헌 후보

이 문서는 결과보고서의 `참고문헌` 절에 사용할 자료 후보를 정리한다.
최종 제출 문서에서는 학교 양식에 맞춰 번호식 또는 저자-연도식으로 변환한다.

## 공공데이터 및 API 문서

1. 한국고용정보원 온통청년, "오픈(OPEN) API 이용방법", https://www.youthcenter.go.kr/cmnFooter/openapiIntro/oaiGuide, 접속일: 2026.07.13.
   - 보고서 활용 위치: 공공데이터 통합, 온통청년 정책 데이터 수집 설명
   - 연결 구현: `YouthApiClient`

2. 한국고용정보원, "온통청년 청년정책API", 공공데이터포털, https://www.data.go.kr/data/15143273/openapi.do, 접속일: 2026.07.13.
   - 보고서 활용 위치: 외부 API 사용 내역 표, 정책 데이터 출처 설명
   - 연결 구현: `YouthApiDto`, `WelfareServiceMapper`

3. 한국사회보장정보원, "중앙부처복지서비스", 공공데이터포털, https://www.data.go.kr/data/15090532/openapi.do, 접속일: 2026.07.13.
   - 보고서 활용 위치: 복지로 중앙부처 복지서비스 목록/상세 수집 설명
   - 연결 구현: `BokjiroCentralClient`, `BokjiroDetailClient`

4. 한국사회보장정보원, "지자체복지서비스", 공공데이터포털, https://www.data.go.kr/data/15108347/openapi.do, 접속일: 2026.07.13.
   - 보고서 활용 위치: 복지로 지자체 복지서비스 목록/상세 수집 설명
   - 연결 구현: `BokjiroLocalClient`, `BokjiroDetailClient`

5. 행정안전부, "대한민국 공공서비스(혜택) 정보", 공공데이터포털, https://www.data.go.kr/data/15113968/openapi.do, 접속일: 2026.07.13.
   - 보고서 활용 위치: Gov24 공공서비스 목록, 상세, 지원조건 수집 설명
   - 연결 구현: `Gov24Client`

## AI 및 플랫폼 문서

6. OpenAI, "Data controls in the OpenAI platform", https://developers.openai.com/api/docs/guides/your-data, 접속일: 2026.07.13.
   - 보고서 활용 위치: OpenAI API 사용 시 데이터 처리 및 개인정보 최소화 설명
   - 연결 구현: `docs/core/openai-runtime-contract.md`

7. OpenAI, "Usage policies", https://openai.com/policies/usage-policies/, 접속일: 2026.07.13.
   - 보고서 활용 위치: AI 기능의 보조적 활용, 안전한 사용 기준 설명

## 선행연구

8. Burke, R. "Hybrid Recommender Systems: Survey and Experiments." User Modeling and User-Adapted Interaction, 12, 331-370, 2002. https://link.springer.com/article/10.1023/A:1021240730564
   - 보고서 활용 위치: 룰 기반 추천과 AI 보조 평가를 결합한 하이브리드 추천 설계 근거

9. Zhang, Y., and Chen, X. "Explainable Recommendation: A Survey and New Perspectives." arXiv:1804.11192, 2018. https://arxiv.org/abs/1804.11192
   - 보고서 활용 위치: 추천 사유 제공과 설명 가능한 추천의 필요성 설명

10. Lewis, P., et al. "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks." arXiv:2005.11401, 2020. https://arxiv.org/abs/2005.11401
    - 보고서 활용 위치: 저장된 정책 정보를 검색해 챗봇 응답에 활용하는 설계 근거

11. Huang, L., et al. "A Survey on Hallucination in Large Language Models: Principles, Taxonomy, Challenges, and Open Questions." arXiv:2311.05232, 2023. https://arxiv.org/abs/2311.05232
    - 보고서 활용 위치: LLM 응답 한계, 환각 가능성, AI를 보조 기능으로 제한한 설계 근거

## 프로젝트 자료

12. Youth Welfare GitHub Repository, https://github.com/minseok02/youth-welfare, 접속일: 2026.07.13.
    - 보고서 활용 위치: 부록, 구현 결과 링크

13. YouthMoa 배포 사이트, https://youthmoa.kr, 접속일: 2026.07.13.
    - 보고서 활용 위치: 부록, 배포 결과 링크

## 본문 인용 연결 계획

| 본문 위치 | 인용 자료 |
|----------|----------|
| 공공데이터 통합 및 정책 데이터 정규화 | 1, 2, 3, 4, 5 |
| 개인화 추천 및 하이브리드 추천 | 8 |
| 추천 사유 제공 | 9 |
| 챗봇 기반 정책 상담 | 10 |
| AI 응답 품질 및 한계 | 11 |
| OpenAI API 개인정보 최소화 | 6, 7 |
| API 사용 내역 표 | 1, 2, 3, 4, 5, 6 |
