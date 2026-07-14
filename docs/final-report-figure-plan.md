# 결과보고서 도표 및 구현 화면 삽입 계획

이 문서는 결과보고서에 삽입할 도표와 구현 화면의 위치, 목적, 제작 기준을 정리한다.

## 도표 제작 기준

- 도표는 코드 내부 클래스 전체를 보여주기보다 보고서 독자가 이해할 수 있는 계층 구조로 단순화한다.
- 각 도표는 본문 설명 직후 배치하고, 캡션에는 그림이 설명하는 핵심을 1~2문장으로 적는다.
- 색상이나 장식보다 정보 구조를 우선한다.
- 최종 제출용 문서에서는 Mermaid 또는 draw.io 원본을 PNG로 변환해 삽입한다.

## [그림 1] 전체 시스템 아키텍처

삽입 위치: `제2장 제2절 1. 전체 시스템 구조`

목적: 사용자가 웹 프론트엔드에 접근하면 백엔드 API가 정책 검색, 추천, 챗봇, 알림, 관리자 기능을 처리하고, PostgreSQL/Redis/외부 API/OpenAI와 연동되는 전체 구조를 설명한다.

```mermaid
flowchart LR
    U[사용자 브라우저] --> FE[React/Vite Frontend]
    A[관리자 브라우저] --> FE
    FE --> NGINX[nginx]
    NGINX --> API[Spring Boot API]
    API --> DB[(PostgreSQL 16 + pgvector)]
    API --> REDIS[(Redis / ElastiCache Valkey)]
    API --> PUBLIC[공공데이터 API]
    API --> OPENAI[OpenAI API]
    API --> MAIL[SMTP Mail]
    API --> PUSH[Web Push]

    PUBLIC --> YOUTH[온통청년]
    PUBLIC --> BOKJIRO[복지로]
    PUBLIC --> GOV24[Gov24]
```

## [그림 2] 정책 데이터 통합 구조

삽입 위치: `제2장 제2절 2. 정책 데이터 통합 구조`

목적: 서로 다른 공공 API 응답이 내부 통합 정책 모델로 정리되어 검색, 추천, 챗봇, 관리자 기능에서 재사용되는 구조를 보여준다.

```mermaid
flowchart TB
    Y[온통청년 API] --> C[수집 Client]
    B1[복지로 중앙 API] --> C
    B2[복지로 지자체 API] --> C
    G[Gov24 API] --> C

    C --> RAW[Raw Payload 저장]
    C --> MAP[정규화 Mapper]
    MAP --> WS[welfare_services]
    MAP --> REG[service_regions]
    MAP --> TAG[service_tags]
    MAP --> DETAIL[welfare_service_details]
    MAP --> FACT[service_facts / taxonomy sidecar]

    WS --> SEARCH[정책 검색]
    WS --> RECO[맞춤 추천]
    DETAIL --> CHAT[챗봇 상담]
    FACT --> ADMIN[관리자 품질 관리]
```

## [그림 3] AI 개인화 추천 파이프라인

삽입 위치: `제2장 제2절 4. AI 개인화 추천 파이프라인 설계`

목적: 추천이 AI 단독 판단이 아니라 후보 추출, 룰 점수, 우선순위 가중치, AI 보조 평가, 최종 점수 정렬의 순서로 이루어짐을 설명한다.

```mermaid
flowchart LR
    P[사용자 프로필] --> R[후보 정책 추출]
    S[정책 데이터] --> R
    R --> RULE[룰 기반 점수 계산]
    RULE --> PRI[우선순위 가중치 적용]
    PRI --> TOP[상위 후보 선별]
    TOP --> AI[OpenAI 보조 평가 및 추천 사유 생성]
    AI --> MERGE[룰 점수 + AI 점수 결합]
    PRI --> MERGE
    MERGE --> FINAL[final_score 정렬]
    FINAL --> SAVE[추천 결과 저장]
    SAVE --> VIEW[사용자 추천 화면]

    AI -. 실패/키 없음 .-> FALLBACK[rule-only fallback]
    FALLBACK --> FINAL
```

## [그림 4] 챗봇 기반 정책 상담 구조

삽입 위치: `제2장 제2절 5. 챗봇 기반 정책 상담 구조`

목적: 챗봇이 사용자의 질문을 직접 자유 생성으로 처리하지 않고, 정책 후보 검색과 evidence 기반 답변 구조로 동작함을 보여준다.

```mermaid
flowchart LR
    Q[사용자 질문] --> REDACT[민감정보 최소화/정제]
    REDACT --> RET[정책 후보 검색]
    RET --> EVID[정책 상세 및 evidence 구성]
    EVID --> AI[OpenAI 답변 생성]
    AI --> CHECK[후보 정책 allowlist 검증]
    CHECK --> ANS[답변 + 관련 정책 참조]
    AI -. 실패/파싱 실패 .-> FB[정책 후보 기반 fallback]
    FB --> ANS
```

## [그림 5] 사용자 서비스 흐름

삽입 위치: `제2장 제2절 6. 사용자 서비스 흐름`

목적: 검색, 상세 조회, 프로필 설정, 추천, 챗봇, 북마크, 알림이 하나의 사용자 경험으로 이어짐을 보여준다.

```mermaid
flowchart TB
    START[서비스 접속] --> SEARCH[정책 검색/필터]
    SEARCH --> DETAIL[정책 상세 조회]
    DETAIL --> BOOKMARK[북마크/최근 본 정책]
    START --> LOGIN[회원가입/로그인]
    LOGIN --> PROFILE[프로필 및 우선순위 설정]
    PROFILE --> RECO[맞춤 추천 조회]
    RECO --> DETAIL
    DETAIL --> CHAT[AI와 신청 준비하기]
    CHAT --> DETAIL
    RECO --> ALERT[알림함/추천 알림]
    ALERT --> DETAIL
```

## [그림 6] 관리자 운영 흐름

삽입 위치: `제2장 제2절 7. 관리자 운영 흐름`

목적: 관리자가 수집 상태, 정책 오류 제보, 중복 후보, 링크 검토, 추천/알림 상태를 확인해 플랫폼 신뢰성을 관리하는 흐름을 보여준다.

```mermaid
flowchart TB
    ADMIN[관리자 로그인] --> DASH[관리자 대시보드]
    DASH --> COLLECT[수집 상태 확인]
    DASH --> ERROR[정책 오류 제보 확인]
    DASH --> DUP[중복 정책 후보 확인]
    DASH --> LINK[정책 링크 검토]
    DASH --> RECO[추천 상태 확인]
    DASH --> NOTI[알림 상태 확인]
    COLLECT --> ACTION[검토/재수집/운영 조치]
    ERROR --> ACTION
    DUP --> ACTION
    LINK --> ACTION
    RECO --> ACTION
    NOTI --> ACTION
```

## [그림 7] 배포 및 운영 구조

삽입 위치: `제2장 제2절 8. 배포 및 운영 구조`

목적: 제출/시연 기준의 ALB + EC2 2대 + RDS + ElastiCache 구조와 스케줄러 중복 실행 방지 설계를 설명한다.

```mermaid
flowchart LR
    USER[사용자] --> ALB[Application Load Balancer]
    ALB --> EC21[EC2 Web Node 1]
    ALB --> EC22[EC2 Web Node 2]
    EC21 --> APP1[nginx + Spring Boot]
    EC22 --> APP2[nginx + Spring Boot]
    APP1 --> RDS[(RDS PostgreSQL)]
    APP2 --> RDS
    APP1 --> CACHE[(ElastiCache Valkey)]
    APP2 --> CACHE
    APP1 --> SCH[Scheduler 활성]
    APP2 --> SCHOFF[Scheduler 비활성]
```

## 구현 화면 캡처 계획

| 그림 번호 | 화면 | 경로 | 캡처 목적 |
|----------|------|------|----------|
| 그림 8 | 메인 화면 | `/` | 서비스 첫 화면과 주요 추천/탐색 진입점을 보여준다. |
| 그림 9 | 정책 검색 및 필터 화면 | `/policies` | 키워드, 지역, 분야, 상태 필터 기반 정책 탐색 기능을 보여준다. |
| 그림 10 | 정책 상세 화면 | `/policies/{id}` | 지원 대상, 신청 기간, 지원 내용, 신청 방법, 공식 링크 제공을 보여준다. |
| 그림 11 | 마이페이지 | `/mypage` | 사용자 프로필, 개인화 기준, 우선순위, 알림 설정을 보여준다. |
| 그림 12 | 맞춤 추천 결과 | `/` 또는 추천 섹션 | 추천 정책, 추천 사유, 최종 점수 기반 정렬 결과를 보여준다. |
| 그림 13 | 챗봇 상담 | `/chat` | 자연어 질문, 정책 참조, 신청 준비 코칭 흐름을 보여준다. |
| 그림 14 | 알림함 | `/alerts` | 추천 알림과 사용자 재방문 지원 기능을 보여준다. |
| 그림 15 | 관리자 대시보드 | `/admin/dashboard` | 정책 품질, 수집, 추천, 알림 운영 상태를 보여준다. |

## 캡처 기준

- 기본 해상도: 데스크톱 1440px 폭
- 필요 시 모바일 화면은 부록 또는 발표자료용으로 별도 캡처
- 실사용 개인정보가 보이지 않도록 smoke/test 계정으로 촬영
- 관리자 화면은 민감한 운영 값이 보이지 않는 범위로 캡처
- 캡션에는 화면이 증명하는 기능을 명확히 작성
