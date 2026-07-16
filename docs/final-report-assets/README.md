# 결과보고서 이미지 삽입 가이드

아래 PNG 파일을 HWP 결과보고서의 동일한 그림 번호 위치에 삽입한다.

## 설계 도표

| 그림 번호 | 파일 | 삽입 위치 |
| --- | --- | --- |
| 그림 1 | `diagrams/figure-01-system-architecture.png` | 제2장 제2절 1. 전체 시스템 구조 |
| 그림 2 | `diagrams/figure-02-policy-data-integration.png` | 제2장 제2절 2. 정책 데이터 통합 구조 |
| 그림 3 | `diagrams/figure-03-recommendation-pipeline.png` | 제2장 제2절 4. AI 개인화 추천 파이프라인 설계 |
| 그림 4 | `diagrams/figure-04-chatbot-structure.png` | 제2장 제2절 5. 챗봇 기반 정책 상담 구조 |
| 그림 5 | `diagrams/figure-05-user-service-flow.png` | 제2장 제2절 6. 사용자 서비스 흐름 |
| 그림 6 | `diagrams/figure-06-admin-operation-flow.png` | 제2장 제2절 7. 관리자 운영 흐름 |
| 그림 7 | `diagrams/figure-07-deployment-topology.png` | 제2장 제2절 8. 배포 및 운영 구조 |

## 구현 화면

| 그림 번호 | 파일 | 삽입 위치 |
| --- | --- | --- |
| 그림 8 | `screenshots/screen-08-main.png` | 제2장 제4절 정책 통합 검색 및 주요 화면 설명 앞 |
| 그림 9 | `screenshots/screen-09-policy-search.png` | 제2장 제4절 정책 통합 검색 기능 |
| 그림 10 | `screenshots/screen-10-policy-detail.png` | 제2장 제4절 정책 상세 조회 기능 |
| 그림 11 | `screenshots/screen-11-mypage-profile-priority.png` | 제2장 제4절 사용자 인증, 프로필 및 개인화 기준 관리 기능 |
| 그림 12 | `screenshots/screen-12-recommendations.png` | 제2장 제4절 AI 기반 개인 맞춤형 추천 기능 |
| 그림 13 | `screenshots/screen-13-chatbot.png` | 제2장 제4절 챗봇 기반 정책 상담 및 신청 준비 코칭 기능 |
| 그림 14 | `screenshots/screen-14-alerts.png` | 제2장 제4절 알림 및 사용자 재방문 지원 기능 |
| 그림 15 | `screenshots/screen-15-admin-dashboard.png` | 제2장 제4절 관리자 대시보드 및 정책 품질 관리 기능 |

## 재생성

```bash
node docs/final-report-assets/diagrams/render-diagrams.mjs
REPORT_SCREENSHOT_EMAIL=... REPORT_SCREENSHOT_PASSWORD=... node docs/final-report-assets/screenshots/capture-screenshots.mjs
node docs/final-report-assets/screenshots/capture-admin-dashboard.mjs
```

`screen-15-admin-dashboard.png`는 실제 관리자 계정 없이 기존 e2e fixture를 사용해 브라우저 요청만 mock 처리한 보고서용 캡처이다.
