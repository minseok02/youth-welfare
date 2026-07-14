# 결과보고서 구현 화면 캡처

재생성 전제:

- 백엔드 API: http://127.0.0.1:8082
- 프론트엔드: http://127.0.0.1:5173

재생성:

```bash
REPORT_SCREENSHOT_EMAIL=... REPORT_SCREENSHOT_PASSWORD=... node docs/final-report-assets/screenshots/capture-screenshots.mjs
node docs/final-report-assets/screenshots/capture-admin-dashboard.mjs
```

`screen-15-admin-dashboard.png`는 실제 관리자 계정 없이 기존 e2e fixture를 사용해 브라우저 요청만 mock 처리한 보고서용 캡처이다.
