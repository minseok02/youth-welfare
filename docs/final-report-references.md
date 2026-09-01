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

8. Burke, R. "Hybrid Recommender Systems: Survey and Experiments." User Modeling and User-Adapted Interaction, 12, 331-370, 2002. https://doi.org/10.1023/A:1021240730564.
   - 보고서 활용 위치: 룰 기반 추천과 AI 보조 평가를 결합한 하이브리드 추천 설계 근거

9. Zhang, Y., and Chen, X. "Explainable Recommendation: A Survey and New Perspectives." Foundations and Trends in Information Retrieval, 14(1), 1-101, 2020. https://doi.org/10.1561/1500000066.
   - 보고서 활용 위치: 추천 사유 제공과 설명 가능한 추천의 필요성 설명

10. Lewis, P., et al. "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks." Advances in Neural Information Processing Systems 33, 2020. https://proceedings.neurips.cc/paper/2020/hash/6b493230205f780e1bc26945df7481e5-Abstract.html.
    - 보고서 활용 위치: 저장된 정책 정보를 검색해 챗봇 응답에 활용하는 설계 근거

11. Huang, L., et al. "A Survey on Hallucination in Large Language Models: Principles, Taxonomy, Challenges, and Open Questions." arXiv:2311.05232, 2023. https://doi.org/10.48550/arXiv.2311.05232.
    - 보고서 활용 위치: LLM 응답 한계, 환각 가능성, AI를 보조 기능으로 제한한 설계 근거

## 프로젝트 자료

12. Youth Welfare GitHub Repository, https://github.com/minseok02/youth-welfare, 접속일: 2026.07.13.
    - 보고서 활용 위치: 부록, 구현 결과 링크

13. YouthMoa 배포 사이트, https://youthmoa.kr, 접속일: 2026.07.13.
    - 보고서 활용 위치: 부록, 배포 결과 링크

## 정책·실태 보고서 및 안내자료

14. 국무조정실·한국보건사회연구원, "2024년 청년 삶 실태조사", 2025. https://www.kihasa.re.kr/library/10210/contents/7192348, 접속일: 2026.07.24.
    - 보고서 활용 위치: 프로젝트 필요성 및 배경, 청년 삶의 다영역 특성 설명

15. 국가데이터처 국가통계연구원, "청년 삶의 질 2025", 2025. https://mods.go.kr/board.es?act=view&bid=246&list_no=442421&mainXml=Y&mid=a10301010000, 접속일: 2026.07.24.
    - 보고서 활용 위치: 프로젝트 필요성 및 배경, 증거 기반 청년 정책 필요성 설명

16. 국무조정실, "제2차 청년정책 기본계획", 2026. https://www.opm.go.kr/_res/opm/etc/opm_youth_plan2.pdf, 접속일: 2026.07.24.
    - 보고서 활용 위치: 프로젝트 필요성 및 배경, 정책 정보 분산과 유사 사업 혼재 문제 설명

17. 고용노동부, "청년이라면 한 번쯤 꼭 읽어야 할 '알기 쉬운 청년 정책.zip'", 2026. https://www.moel.go.kr/policy/policydata/view.do?bbs_seq=20260700851, 접속일: 2026.07.24.
    - 보고서 활용 위치: 프로젝트 필요성 및 배경, 청년 정책 이해와 활용 지원 필요성 설명

## 본문 인용 연결 계획

| 본문 위치 | 인용 자료 |
|----------|----------|
| 프로젝트 필요성 및 배경 | 14, 15, 16, 17 |
| 프로젝트 필요성 관련 정책·연구 근거 표 | 14, 15, 16, 17 |
| 관련 기술 및 선행연구 요약 표 | 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 |
| 공공데이터 통합 및 정책 데이터 정규화 | 1, 2, 3, 4, 5 |
| 개인화 추천 및 하이브리드 추천 | 8 |
| 추천 사유 제공 | 9 |
| 챗봇 기반 정책 상담 | 10 |
| AI 응답 품질 및 한계 | 11 |
| OpenAI API 개인정보 최소화 | 6, 7 |
| API 사용 내역 표 | 1, 2, 3, 4, 5, 6 |
