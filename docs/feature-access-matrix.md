# 웹 기능 접근표

문서군 진입점: [documentation-map.md](./documentation-map.md)

## 목적

현재 웹 기능이

- 비로그인 상태에서 가능한지
- 일반 로그인 사용자에게 가능한지
- 관리자 계정에서 가능한지

를 한 표에서 확인합니다.

판정 기준은 현재 `frontend` 라우팅/버튼 동작과 `backend` 보안 설정/컨트롤러 계약입니다.

## 접근 표

| 기능 | 비로그인 | 로그인 사용자 | 관리자 로그인 | 저장 방식 / 비고 |
|---|---|---|---|---|
| 메인 페이지 접근 | O | O | O | `/` 공개 |
| 메인 페이지 teaser 정책 보기 | O | - | - | 비로그인일 때만 노출, `/api/policies` 공개 조회 |
| 정책 목록 보기 | O | O | O | `/policies`, `/api/policies` 공개 |
| 정책 검색 보기 | O | O | O | `/api/policies/search` 공개 |
| 정책 상세 보기 | O | O | O | `/api/policies/{id}` 공개 |
| 정책 랭킹 보기 | O | O | O | `/api/policies/ranking` 공개 |
| 정책 필터/정렬/페이지네이션 | O | O | O | 필터 상태는 URL query string으로 유지 |
| 정책 필터 공유/새로고침 복원 | O | O | O | `searchParams` 기반, 서버 저장 아님 |
| 회원가입 | O | O | O | 단, 관리자 예약 이메일은 공개 signup 금지 |
| 로그인 | O | O | O | 공개 |
| 추천 목록 조회 | X | O | O | `/api/recommendations` 인증 필요 |
| 추천 새로고침 | X | O | O | `/api/recommendations/refresh` 인증 필요 |
| 개인 맞춤 재추천 | X | O | O | `personal=true` 도 인증 필요 |
| 추천 클릭 로그 반영 | X | O | O | 추천 진입 흐름 기반 |
| 정책 북마크 토글 | X | O | O | `/api/policies/{id}/bookmark` 인증 필요 |
| 추천 북마크 토글 | X | O | O | `/api/recommendations/{id}/bookmark` 인증 필요 |
| 내 북마크 목록 | X | O | O | `/api/users/me/bookmarks` 인증 필요 |
| 마이페이지 | X | O | O | 프론트에서 `RequireLogin` 적용 |
| AI 챗봇 화면 접근 | X | O | O | `/chat` 라우트 자체가 로그인 필요 |
| AI 챗 세션 조회/생성/대화 | X | O | O | `/api/chat/sessions/**` 인증 필요 |
| 관리자 대시보드 API | X | X | O | `/api/admin/**` + `ROLE_ADMIN` 필요 |
| 관리자 수집 실행 API | X | X | O | `/api/admin/collect/**` |
| 관리자 강제 로그아웃 API | X | X | O | `/api/admin/users/forced-logout` |
| 관리자 전용 웹 페이지 | X | X | X | 현재 별도 프론트 admin page 없음, 관리자 API 중심 |

표기:

- `O`: 현재 바로 사용 가능
- `X`: 현재 차단
- `-`: 같은 기능이 아니라 다른 화면/흐름으로 대체됨

## 저장/임시 상태 정리

### 1. 비로그인 상태에서 되는 “임시 유지”

현재 비로그인 상태에서 유지되는 것은 **정책 목록 필터/정렬/페이지네이션의 URL 상태**가 사실상 전부입니다.

예:

- `search`
- `category`
- `region`
- `subRegion`
- `income`
- `employ`
- `sourceType`
- `statusFilter`
- `sort`
- `page`
- `pageSize`

즉 브라우저 새로고침이나 URL 공유 시 같은 필터 조합이 복원됩니다.

다만 이것은:

- 서버 저장이 아니고
- 사용자별 임시 저장함이 아니며
- 북마크처럼 목록을 쌓아 두는 구조도 아닙니다.

### 2. 비로그인 상태에서 안 되는 것

현재 비로그인 상태에서는 아래 같은 “개인 상태 저장”은 없습니다.

- 임시 북마크
- 찜 목록
- 최근 본 정책 목록 저장
- 비로그인 추천 결과 저장
- 비로그인 챗 기록 저장
- 비로그인 사용자용 필터 preset 저장

### 3. 로그인 상태에서 저장되는 것

- 인증 상태와 사용자 기본 상태: `zustand persist`
- access token: `localStorage`
- 정책/추천 북마크: 서버 저장
- 추천 결과/로그: 서버 저장
- 챗 세션/메시지: 서버 저장
- 마이페이지 기본 필터 설정(`includeExpired`): auth store persisted state

## 관리자 계정 정리

현재 관리자 권한은 **별도 관리자 웹 UI** 가 아니라:

1. 일반 로그인 가능한 사용자 row가 있고
2. 그 이메일이 `SECURITY_ADMIN_EMAILS` allowlist 에 포함될 때
3. 로그인 JWT에 `ROLE_ADMIN` 이 들어가는 방식입니다.

즉 현재 관리자 계정은 “운영자 API 호출 권한 계정”에 가깝습니다.

## 현재 프로젝트 기준 결론

1. 비로그인은 공개 정책 조회와 URL 기반 필터 상태 유지까지만 가능합니다.
2. 추천, 챗, 북마크, 마이페이지 같은 개인화 기능은 모두 로그인 필요입니다.
3. 관리자 계정은 존재할 수 있지만, 현재 프론트에 관리자 전용 페이지는 없고 관리자 API 권한용입니다.

## 근거 파일

- [SecurityConfig.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java:39)
- [AuthAdminRoleService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthAdminRoleService.java:15)
- [RecommendationController.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/controller/RecommendationController.java:22)
- [ChatSessionController.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/chat/controller/ChatSessionController.java:20)
- [router/index.jsx](/home/minseok/youth-welfare/frontend/src/router/index.jsx:12)
- [RequireLogin.jsx](/home/minseok/youth-welfare/frontend/src/components/RequireLogin.jsx:5)
- [Header.jsx](/home/minseok/youth-welfare/frontend/src/components/Header.jsx:23)
- [FloatingNav.jsx](/home/minseok/youth-welfare/frontend/src/components/FloatingNav.jsx:8)
- [MainPage.jsx](/home/minseok/youth-welfare/frontend/src/pages/MainPage.jsx:63)
- [PoliciesPage.jsx](/home/minseok/youth-welfare/frontend/src/pages/PoliciesPage.jsx:116)
- [PolicyDetailPage.jsx](/home/minseok/youth-welfare/frontend/src/pages/PolicyDetailPage.jsx:137)
- [authStore.js](/home/minseok/youth-welfare/frontend/src/store/authStore.js:1)
