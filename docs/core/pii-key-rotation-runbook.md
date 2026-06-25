# PII Key Rotation Runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

`youth_welfare_pii.user_pii` 와 `user_pii_sync_queue` 에 저장된 앱 레벨 암호문을 어떤 범위까지 운영자가 회전할 수 있는지 고정합니다.

이 문서의 계약은 아래 검증으로 같이 고정합니다.

- `bash deploy/smoke/verify-pii-key-rotation-runbook.sh`
- `PiiKeyRotationRunbookContractTest`
- `AesEncryptUtilTest`
- `UserPiiBackfillServiceTest.rotateLegacyEncryptedFields`

## PII_KEY_ROTATION_CURRENT_SCOPE

현재 코드가 지원하는 작업은 **legacy cipher rotation** 입니다.

- 읽기: `AesEncryptUtil.decrypt(...)`
  - `v2:` prefix가 있으면 `AES/GCM/NoPadding`
  - prefix가 없으면 legacy `AES/CBC/PKCS5Padding`
- 쓰기: `AesEncryptUtil.encrypt(...)`
  - 항상 `v2:Base64(nonce+ciphertext+tag)` GCM payload 저장
- 운영 API: `POST /api/admin/users/pii-encryption-rotation`
  - `user_pii` 의 legacy CBC 암호문을 현재 `AES_SECRET_KEY` 로 복호화
  - 같은 `AES_SECRET_KEY` 로 `v2:` GCM 암호문을 다시 저장
  - `user_pii_sync_queue` 의 legacy payload도 같은 방식으로 재저장

즉 이 경로는 **암호화 포맷 교체**입니다.
현재 운영 키를 새 키로 바꾸는 기능이 아닙니다.

## PII_KEY_ROTATION_FORBIDDEN_DIRECT_AES_SECRET_SWAP

현재 코드에서 `AES_SECRET_KEY` 를 바로 교체하는 것은 금지합니다.

이유:

- `AesEncryptUtil` 은 단일 `aes.secret-key` 로만 decrypt/encrypt 합니다.
- `v2:` GCM payload에는 key id가 없습니다.
- 기존 `v2:` 암호문은 예전 `AES_SECRET_KEY` 로만 복호화됩니다.
- 새 `AES_SECRET_KEY` 로 앱을 재기동하면 기존 `v2:` 암호문 복호화가 실패합니다.
- `POST /api/admin/users/pii-encryption-rotation` 은 old key와 new key를 동시에 받지 않으므로 key migration에 쓸 수 없습니다.

따라서 운영자가 하면 안 되는 순서:

1. 새 `AES_SECRET_KEY` 를 서버 env에 반영
2. 앱 재기동
3. `/api/admin/users/pii-encryption-rotation` 실행

이 순서는 기존 PII를 읽지 못하게 만들 수 있습니다.

## PII_LEGACY_CIPHER_ROTATION_RUNBOOK

legacy CBC 암호문을 현재 키의 `v2:` GCM 포맷으로 정리할 때만 아래 순서를 사용합니다.

### 1. 사전 조건

- `AES_SECRET_KEY` 는 변경하지 않습니다.
- 앱이 현재 키로 기존 PII를 정상 복호화할 수 있어야 합니다.
- 운영 DB 백업 또는 snapshot을 먼저 확보합니다.
- 운영자 admin token을 준비합니다.
- `APP_PII_DB_URL` 과 `NOTIFICATION_PII_DB_URL` 이 `currentSchema=youth_welfare_pii` 를 가리키는지 확인합니다.

### 2. 실행 전 계수 확인

PostgreSQL 예시:

```sql
SELECT
  count(*) AS total_rows,
  count(*) FILTER (WHERE email_enc IS NOT NULL AND email_enc NOT LIKE 'v2:%') AS legacy_email,
  count(*) FILTER (WHERE name_enc IS NOT NULL AND name_enc NOT LIKE 'v2:%') AS legacy_name,
  count(*) FILTER (WHERE birth_date_enc IS NOT NULL AND birth_date_enc NOT LIKE 'v2:%') AS legacy_birth_date,
  count(*) FILTER (WHERE phone_enc IS NOT NULL AND phone_enc NOT LIKE 'v2:%') AS legacy_phone
FROM youth_welfare_pii.user_pii;

SELECT
  count(*) AS total_rows,
  count(*) FILTER (WHERE email_enc IS NOT NULL AND email_enc NOT LIKE 'v2:%') AS legacy_email,
  count(*) FILTER (WHERE name_enc IS NOT NULL AND name_enc NOT LIKE 'v2:%') AS legacy_name,
  count(*) FILTER (WHERE birth_date_enc IS NOT NULL AND birth_date_enc NOT LIKE 'v2:%') AS legacy_birth_date,
  count(*) FILTER (WHERE phone_enc IS NOT NULL AND phone_enc NOT LIKE 'v2:%') AS legacy_phone
FROM user_pii_sync_queue;
```

### 3. legacy rotation 실행

```bash
curl -fsS -X POST "${APP_BASE_URL}/api/admin/users/pii-encryption-rotation" \
  -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  -H "Content-Type: application/json"
```

응답에서 아래를 확인합니다.

- `failedCount=0`
- `userPiiProcessedCount` 와 `userPiiUpdatedCount` 차이가 예측 범위 안에 있음
- `queueProcessedCount` 와 `queueUpdatedCount` 차이가 예측 범위 안에 있음

### 4. 실행 후 검증

1. 실행 전 계수 SQL을 다시 실행해 legacy count가 `0`인지 확인합니다.
2. `GET /api/admin/users/pii-sync-status?failedSampleLimit=5` 로 failed queue가 늘지 않았는지 확인합니다.
3. `bash deploy/smoke/run-local-pii-sync-cutover-smoke.sh` 를 실행합니다.
4. 대표 사용자로 프로필 조회, 비밀번호 재설정 수신 주소 조회, 알림 발송 대상 조회가 정상인지 확인합니다.

### 5. rollback 기준

아래 중 하나면 즉시 rollback 또는 장애 대응으로 전환합니다.

- `failedCount > 0`
- rotation 뒤 프로필/비밀번호 재설정/알림 대상 이메일 복호화 실패
- `pii-sync-status` failed count 증가
- 앱 로그에 `AES decrypt failed` 반복

rollback은 DB snapshot restore가 기본입니다.
동일 키를 유지한 legacy rotation은 암호문 포맷만 바꾸므로, 새 키를 도입하지 않은 상태에서는 env rollback으로 해결되지 않습니다.

## PII_KEY_ROTATION_FUTURE_DUAL_KEY_CONTRACT

실제 `AES_SECRET_KEY` 교체를 하려면 먼저 코드가 아래 계약을 지원해야 합니다.

필수 구현:

- `AES_ACTIVE_KEY_ID`: 새 암호문에 쓸 key id
- `AES_DECRYPT_KEYS`: key id별 decrypt key 목록
- 새 암호문 payload에 key id 저장
- old key decrypt + active key encrypt 재암호화 job
- key id별 잔여 암호문 계수 export
- active key 전환 전 dry-run decrypt 검증
- old key 제거 전 모든 PII 암호문이 active key id로 재저장됐는지 확인

권장 순서:

1. old key만 가진 현재 앱에서 legacy CBC 잔여분을 먼저 `v2:`로 정리
2. dual-key 지원 코드 배포
3. `AES_DECRYPT_KEYS` 에 old/new key를 모두 등록하고 `AES_ACTIVE_KEY_ID` 는 old로 시작
4. dry-run decrypt 검증
5. `AES_ACTIVE_KEY_ID` 를 new로 바꿔 새 write만 new key로 저장
6. re-encrypt job으로 old key 암호문을 new key로 재저장
7. key id별 잔여 old count가 `0`인지 확인
8. old key 제거 후 앱 재기동
9. profile/password-reset/notification smoke 통과 확인

이 dual-key 계약이 구현되기 전까지는 `AES_SECRET_KEY` 값을 운영에서 교체하지 않습니다.

## PII_KEY_ROTATION_EVIDENCE

작업 기록에는 아래를 남깁니다.

- 실행 일시와 배포 버전
- `AES_SECRET_KEY` 변경 여부: legacy rotation에서는 반드시 `unchanged`
- 실행 전 legacy count
- API 응답의 `userPiiProcessedCount`, `userPiiUpdatedCount`, `queueProcessedCount`, `queueUpdatedCount`, `failedCount`
- 실행 후 legacy count
- `pii-sync-status` 결과
- 실행한 smoke 명령과 결과
- rollback 필요 여부
