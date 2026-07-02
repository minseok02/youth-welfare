# Frontend SEO Current State

문서군 진입점: [README.md](./README.md)

## 목적

이 문서는 프론트엔드의 검색엔진 최적화(SEO)와 SNS 공유 미리보기가 현재 어떤 상태인지,
어떤 파일에서 무엇을 근거로 설정하는지를 빠르게 확인하기 위한 current-state 문서입니다.

## 배경

기존 [index.html](../../frontend/index.html) 은 Vite 초기 템플릿 값을 그대로 두고 있어
SEO/공유 관점에서 아래 문제가 있었습니다.

- `<html lang="en">` — 한국어 서비스인데 언어가 영어로 표기됨
- `<title>frontend-react</title>` — 개발 기본값이 검색결과·브라우저 탭에 그대로 노출
- `meta description` 없음 — 검색결과 설명 스니펫이 비어 있음
- Open Graph 태그 없음 — 카카오톡·SNS 링크 공유 시 미리보기 카드가 생성되지 않음
- `robots.txt` 없음 — 크롤러 접근 규칙 부재

## 현재 결론

- 문서 메타데이터와 공유 미리보기, 크롤러 규칙을 모두 정적으로 제공합니다.
- SPA(단일 index.html) 구조라 문서 메타는 [index.html](../../frontend/index.html) 한 곳에서 관리합니다.
- 라우트별 동적 메타(정책 상세 등)는 현재 범위 밖이며, 필요 시 후속 과제로 둡니다.

## 구현 내용

### 1. 문서 메타데이터 — [index.html](../../frontend/index.html)

- `<html lang="ko">` : 한국어 서비스임을 브라우저·검색엔진·스크린리더에 명시
- `<title>` : 실제 서비스명 기반 제목 (검색결과·탭 표기)
- `<meta name="description">` : 검색결과 설명 스니펫
- `<meta name="theme-color" content="#2563eb">` : 모바일 브라우저 주소창 색상

### 2. SNS 공유 미리보기 (Open Graph) — [index.html](../../frontend/index.html)

- `og:type` = website
- `og:title` / `og:description` : 공유 카드 제목·설명
- `og:locale` = ko_KR

### 3. 크롤러 접근 규칙 — [robots.txt](../../frontend/public/robots.txt)

- 기본 정책: 전체 크롤링 허용 (`User-agent: *` / `Allow: /`)
- 색인 제외 경로: 로그인 전용·관리·일회용 토큰 경로
  - `/mypage`, `/alerts`, `/chat` (로그인 필요)
  - `/admin` (관리자)
  - `/reset-password`, `/notifications/unsubscribe` (일회용 토큰, 색인 무의미)
- 색인 대상: 공개 페이지 (`/`, `/guide`, `/support`, `/login`, `/signup`, `/terms`, `/privacy`, `/policies`, `/policies/:id`)

> 경로 근거: [router/index.jsx](../../frontend/src/router/index.jsx)

## 남은 과제 (후속)

- 라우트별 동적 title/description (정책 상세 페이지 등) — react-helmet 계열 도입 검토
- sitemap.xml 생성 (배포 도메인 확정 후)
- OG 이미지(`og:image`) 추가 — 공유 카드 썸네일
