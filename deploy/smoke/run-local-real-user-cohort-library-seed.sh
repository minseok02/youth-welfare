#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_DOMAIN="${SMOKE_EMAIL_DOMAIN:-realuser.app}"
COHORT_FILTER="${COHORT_FILTER:-all}"
RUN_READINESS_AFTER="${RUN_READINESS_AFTER:-true}"
RUN_BLOCKER_AUDIT_AFTER="${RUN_BLOCKER_AUDIT_AFTER:-true}"

ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-real-user-cohort-library-seed"
TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

SEEDED_USERS_OUTPUT="${ARTIFACT_DIR}/seeded-users.tsv"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/cohort-library-summary.txt"
READINESS_OUTPUT="${ARTIFACT_DIR}/readiness.out"
BLOCKER_OUTPUT="${ARTIFACT_DIR}/review-gate-blocker.out"
PROFILE_MANIFEST_OUTPUT="${ARTIFACT_DIR}/cohort-profiles.tsv"

RUN_READINESS_AFTER="$(smoke_normalize_bool "${RUN_READINESS_AFTER}")"
RUN_BLOCKER_AUDIT_AFTER="$(smoke_normalize_bool "${RUN_BLOCKER_AUDIT_AFTER}")"

smoke_require_command bash
smoke_require_command python3

matches_filter() {
  local cohort="$1"
  if [[ "${COHORT_FILTER}" == "all" ]]; then
    return 0
  fi
  python3 - "${COHORT_FILTER}" "${cohort}" <<'PY'
import sys

allowed = {part.strip().lower() for part in sys.argv[1].split(",") if part.strip()}
cohort = sys.argv[2].strip().lower()
sys.exit(0 if cohort in allowed else 1)
PY
}

extract_key_value() {
  local output_file="$1"
  local key="$2"
  python3 - "${output_file}" "${key}" <<'PY'
import sys

output_file, key = sys.argv[1], sys.argv[2]
prefix = f"{key}="

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith(prefix):
            print(line[len(prefix):])
            break
PY
}

cat > "${PROFILE_MANIFEST_OUTPUT}" <<'EOF'
cohort	email	name	birth_date	sido	sgg	income_level	employment_status	household_type
housing	realuser.housing01@realuser.app	주거가	1999-01-15	서울특별시	관악구	2	구직자	1인 가구
housing	realuser.housing02@realuser.app	주거나	2000-03-11	서울특별시	동작구	3	미취업	1인 가구
housing	realuser.housing03@realuser.app	주거다	1998-07-22	경기도	수원시	4	아르바이트	청년부부
housing	realuser.housing04@realuser.app	주거라	1997-11-09	경기도	고양시	2	재직자	신혼부부
housing	realuser.housing05@realuser.app	주거마	2001-05-03	인천광역시	부평구	1	구직자	1인 가구
housing	realuser.housing06@realuser.app	주거바	1996-12-14	인천광역시	남동구	3	프리랜서	1인 가구
housing	realuser.housing07@realuser.app	주거사	2002-09-18	부산광역시	부산진구	2	대학생	자취
housing	realuser.housing08@realuser.app	주거아	1995-04-27	대전광역시	유성구	4	재직자	1인 가구
housing	realuser.housing09@realuser.app	주거자	2003-02-06	광주광역시	북구	2	대학생	부모와 거주
housing	realuser.housing10@realuser.app	주거차	1994-10-30	세종특별자치시	세종시	5	자영업자	청년부부
education	realuser.edu01@realuser.app	교육가	2004-03-08	서울특별시	서대문구	4	대학생	부모와 거주
education	realuser.edu02@realuser.app	교육나	2001-08-19	서울특별시	광진구	5	대학원생	1인 가구
education	realuser.edu03@realuser.app	교육다	2002-01-25	경기도	성남시	6	대학생	기숙사
education	realuser.edu04@realuser.app	교육라	1999-06-12	인천광역시	연수구	4	구직자	부모와 거주
education	realuser.edu05@realuser.app	교육마	2003-11-02	대구광역시	수성구	3	대학생	자취
education	realuser.edu06@realuser.app	교육바	2000-09-21	부산광역시	금정구	5	대학원생	1인 가구
education	realuser.edu07@realuser.app	교육사	1998-05-16	광주광역시	동구	4	미취업	부모와 거주
education	realuser.edu08@realuser.app	교육아	2004-12-01	울산광역시	남구	6	대학생	부모와 거주
education	realuser.edu09@realuser.app	교육자	2001-02-14	충청남도	천안시	5	아르바이트	1인 가구
education	realuser.edu10@realuser.app	교육차	1997-07-29	전라북도	전주시	3	대학원생	청년부부
job	realuser.job01@realuser.app	일자리	1998-02-03	서울특별시	강서구	4	구직자	1인 가구
job	realuser.job02@realuser.app	구직가	1996-06-17	경기도	안산시	5	미취업	1인 가구
job	realuser.job03@realuser.app	구직나	1999-09-05	인천광역시	미추홀구	4	재직자	2인 가구
job	realuser.job04@realuser.app	구직다	1995-11-13	대구광역시	달서구	3	창업준비중	1인 가구
job	realuser.job05@realuser.app	구직라	2000-04-09	부산광역시	사상구	4	프리랜서	1인 가구
job	realuser.job06@realuser.app	구직마	1997-01-23	광주광역시	서구	5	구직자	부모와 거주
job	realuser.job07@realuser.app	구직바	1994-08-31	경상남도	창원시	4	재직자	청년부부
job	realuser.job08@realuser.app	구직사	2001-10-07	충청북도	청주시	3	아르바이트	1인 가구
job	realuser.job09@realuser.app	구직아	1996-03-26	전라남도	순천시	2	자영업자	3인 가구
job	realuser.job10@realuser.app	구직자	1998-12-15	강원특별자치도	춘천시	4	미취업	1인 가구
finance	realuser.finance01@realuser.app	금융가	1997-05-05	서울특별시	중랑구	1	구직자	한부모 가구
finance	realuser.finance02@realuser.app	금융나	1995-09-10	경기도	의정부시	2	미취업	조손 가구
finance	realuser.finance03@realuser.app	금융다	1999-02-28	인천광역시	계양구	1	아르바이트	1인 가구
finance	realuser.finance04@realuser.app	금융라	1998-07-07	대전광역시	동구	2	구직자	다자녀 가구
finance	realuser.finance05@realuser.app	금융마	1996-11-22	광주광역시	광산구	3	프리랜서	청년부부
finance	realuser.finance06@realuser.app	금융바	2000-03-30	대구광역시	북구	2	대학생	부모와 거주
finance	realuser.finance07@realuser.app	금융사	1994-01-12	부산광역시	북구	1	재직자	4인 가구
finance	realuser.finance08@realuser.app	금융아	2001-06-18	전북특별자치도	익산시	2	대학생	자취
finance	realuser.finance09@realuser.app	금융자	1997-10-24	경상북도	포항시	3	창업준비중	1인 가구
finance	realuser.finance10@realuser.app	금융차	1995-12-02	제주특별자치도	제주시	2	미취업	한부모 가구
housing_leader_path	realuser.hpath01@realuser.app	경로가	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath02@realuser.app	경로나	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath03@realuser.app	경로다	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath04@realuser.app	경로라	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath05@realuser.app	경로마	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath06@realuser.app	경로바	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath07@realuser.app	경로사	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath08@realuser.app	경로아	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath09@realuser.app	경로자	2001-04-30	인천광역시	중구	5	미취업	1인 가구
housing_leader_path	realuser.hpath10@realuser.app	경로차	2001-04-30	인천광역시	중구	5	미취업	1인 가구
EOF

printf '%s\n' "cohort	email	name	birth_date	sido	sgg	income_level	employment_status	household_type	existing_user_reused	service_id	log_id" > "${SEEDED_USERS_OUTPUT}"

total_seeded=0
total_reused=0

while IFS=$'\t' read -r cohort email name birth_date sido sgg income_level employment_status household_type; do
  if [[ "${cohort}" == "cohort" ]]; then
    continue
  fi

  if ! matches_filter "${cohort}"; then
    continue
  fi

  user_output="${ARTIFACT_DIR}/${name}.out"
  smoke_print_step "seed ${cohort} ${email}"
  APP_BASE_URL="${APP_BASE_URL}" \
  SMOKE_PASSWORD="${SMOKE_PASSWORD}" \
  SMOKE_EMAIL_DOMAIN="${SMOKE_EMAIL_DOMAIN}" \
  SMOKE_EMAIL="${email}" \
  SMOKE_NAME="${name}" \
  SMOKE_BIRTH_DATE="${birth_date}" \
  SMOKE_SIDO="${sido}" \
  SMOKE_SGG="${sgg}" \
  SMOKE_INCOME_LEVEL="${income_level}" \
  SMOKE_EMPLOYMENT_STATUS="${employment_status}" \
  SMOKE_HOUSEHOLD_TYPE="${household_type}" \
  ALLOW_EXISTING_USER=true \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-click-smoke.sh" | tee "${user_output}"

  existing_user_reused="$(extract_key_value "${user_output}" "existing_user_reused")"
  service_id="$(extract_key_value "${user_output}" "service_id")"
  log_id="$(extract_key_value "${user_output}" "log_id")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${cohort}" "${email}" "${name}" "${birth_date}" "${sido}" "${sgg}" \
    "${income_level}" "${employment_status}" "${household_type}" \
    "${existing_user_reused}" "${service_id}" "${log_id}" >> "${SEEDED_USERS_OUTPUT}"

  total_seeded=$((total_seeded + 1))
  if [[ "${existing_user_reused}" == "true" ]]; then
    total_reused=$((total_reused + 1))
  fi
done < "${PROFILE_MANIFEST_OUTPUT}"

if [[ "${RUN_READINESS_AFTER}" == "true" ]]; then
  smoke_print_step "post-seed readiness"
  APP_BASE_URL="${APP_BASE_URL}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123!}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-real-user-exclusion-readiness-check.sh" | tee "${READINESS_OUTPUT}"
fi

if [[ "${RUN_BLOCKER_AUDIT_AFTER}" == "true" ]]; then
  smoke_print_step "post-seed review-gate blocker audit"
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh" | tee "${BLOCKER_OUTPUT}"
fi

cat > "${SUMMARY_OUTPUT}" <<EOF
generated_at_utc=$(smoke_now_iso_utc)
generated_at_kst=$(smoke_now_iso_kst)
cohort_filter=${COHORT_FILTER}
seed_email_domain=${SMOKE_EMAIL_DOMAIN}
total_profiles_seeded=${total_seeded}
total_existing_users_reused=${total_reused}
seeded_users_output=${SEEDED_USERS_OUTPUT}
profile_manifest_output=${PROFILE_MANIFEST_OUTPUT}
readiness_output=${READINESS_OUTPUT}
blocker_output=${BLOCKER_OUTPUT}
artifact_dir=${ARTIFACT_DIR}
EOF

smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-cohort-library-summary.txt" \
  "${SEEDED_USERS_OUTPUT}" "${ARTIFACT_ROOT}/latest-seeded-users.tsv" \
  "${PROFILE_MANIFEST_OUTPUT}" "${ARTIFACT_ROOT}/latest-cohort-profiles.tsv"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
