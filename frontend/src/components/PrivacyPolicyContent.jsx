import { useMediaQuery } from "@mui/material";

const A = "#2563eb";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const LINE = "#e5e7eb";

const sectionStyle = {
  padding: "24px 0",
  borderTop: `1px solid ${LINE}`,
};

const noteBoxStyle = {
  background: "#f8fafc",
  border: `1px solid ${LINE}`,
  borderRadius: 12,
  padding: "14px 16px",
  color: INK2,
  fontSize: 14,
  lineHeight: 1.75,
  margin: 0,
};

const tableStyle = {
  width: "100%",
  minWidth: 640,
  borderCollapse: "collapse",
  fontSize: 14,
  color: INK2,
};

const thStyle = {
  textAlign: "left",
  padding: "12px 10px",
  borderBottom: `1px solid ${LINE}`,
  background: "#f8fafc",
  color: INK,
  whiteSpace: "nowrap",
};

const tdStyle = {
  verticalAlign: "top",
  padding: "12px 10px",
  borderBottom: `1px solid ${LINE}`,
  lineHeight: 1.65,
};

const PI_COLUMNS = [
  { key: "items", label: "처리 항목" },
  { key: "purpose", label: "처리 목적" },
  { key: "basis", label: "법적 근거" },
  { key: "retention", label: "보유기간" },
];

const PI_ROWS = [
  {
    category: "회원가입 필수 정보",
    items: "이메일, 비밀번호, 이름, 생년월일",
    purpose: "계정 생성, 로그인, 이메일 인증, 회원 식별, 청년 대상 서비스 제공",
    basis: "서비스 이용계약 체결 및 이행",
    retention: "회원 탈퇴 시까지",
  },
  {
    category: "추천용 선택 정보",
    items: "지역, 소득수준, 취업상태, 가구형태, 주거형태, 주택유형, 기초생활수급 관련 정보",
    purpose: "맞춤형 복지정책 추천 정확도 향상",
    basis: "정보주체의 선택 동의",
    retention: "동의 철회 또는 회원 탈퇴 시까지",
  },
  {
    category: "민감정보",
    items: "장애등급 관련 정보",
    purpose: "장애 관련 복지정책 추천 정확도 향상",
    basis: "정보주체의 별도 선택 동의",
    retention: "동의 철회 또는 회원 탈퇴 시까지",
  },
  {
    category: "서비스 이용 과정 생성 정보",
    items: "로그인 이력, 정책 조회/북마크/추천 이력, 채팅 세션 및 메시지, 알림 설정 및 발송 이력",
    purpose: "서비스 제공, 보안, 오류 대응, 추천 품질 개선",
    basis: "서비스 이용계약 이행 및 정당한 이익",
    retention: "목적 달성 또는 회원 탈퇴 시까지",
  },
  {
    category: "문의 및 오류 제보 정보",
    items: "문의 유형, 문의 내용, 정책 오류 제보 내용, 처리 상태",
    purpose: "서비스 문의 응대, 정책 데이터 오류 확인, 운영 품질 개선",
    basis: "서비스 이용계약 이행 및 정당한 이익",
    retention: "문의 처리 및 분쟁 대응 목적 달성 시까지",
  },
];

const SUMMARY_ITEMS = [
  ["필수 정보", "계정 생성과 로그인, 청년 대상 서비스 제공에 필요한 정보입니다."],
  ["선택 정보", "추천 품질을 높이기 위한 정보이며 동의하지 않아도 기본 이용은 가능합니다."],
  ["AI 처리", "추천과 챗봇 답변을 위해 필요한 최소 정보와 정책 후보 정보만 사용합니다."],
];

function Section({ title, children }) {
  return (
    <section style={sectionStyle}>
      <h2 style={{ fontSize: 20, lineHeight: 1.35, margin: "0 0 14px", color: INK }}>{title}</h2>
      {children}
    </section>
  );
}

function ProcessingItemsTable() {
  return (
    <div style={{ overflowX: "auto" }}>
      <table style={tableStyle}>
        <thead>
          <tr>
            <th style={thStyle}>구분</th>
            {PI_COLUMNS.map((col) => (
              <th key={col.key} style={thStyle}>{col.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {PI_ROWS.map((row) => (
            <tr key={row.category}>
              <td style={{ ...tdStyle, fontWeight: 700, color: INK, whiteSpace: "nowrap" }}>{row.category}</td>
              {PI_COLUMNS.map((col) => (
                <td key={col.key} style={tdStyle}>{row[col.key]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ProcessingItemsCards() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {PI_ROWS.map((row) => (
        <div
          key={row.category}
          style={{ background: "#f8fafc", border: `1px solid ${LINE}`, borderRadius: 12, padding: "14px 16px" }}
        >
          <div style={{ fontSize: 15, fontWeight: 800, color: INK, marginBottom: 10 }}>{row.category}</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {PI_COLUMNS.map((col) => (
              <div key={col.key}>
                <div style={{ fontSize: 12, fontWeight: 700, color: INK3, marginBottom: 2 }}>{col.label}</div>
                <div style={{ fontSize: 13, color: INK2, lineHeight: 1.6 }}>{row[col.key]}</div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export default function PrivacyPolicyContent() {
  const isMobile = useMediaQuery("(max-width: 600px)");

  return (
    <>
      <p style={{ color: A, fontSize: 13, fontWeight: 800, margin: "0 0 8px" }}>청년복지플랫폼</p>
      <h1 style={{ fontSize: 30, lineHeight: 1.25, margin: "0 0 16px", color: INK }}>개인정보 처리방침</h1>
      <p style={{ ...noteBoxStyle, color: INK3 }}>
        청년복지플랫폼은 회원 계정 제공, 맞춤형 복지정책 추천, 알림 및 문의 응대를 위해 필요한 범위에서 개인정보를 처리합니다.
        이 방침은 서비스 화면에서 수집되는 항목, 목적, 보유기간, 이용자의 권리 행사 방법을 안내하기 위한 문서입니다.
      </p>

      <div style={{ display: "grid", gap: 12, gridTemplateColumns: isMobile ? "1fr" : "repeat(3, 1fr)", marginTop: 16 }}>
        {SUMMARY_ITEMS.map(([title, body]) => (
          <div key={title} style={{ background: "#f8fafc", border: `1px solid ${LINE}`, borderRadius: 12, padding: "14px 16px" }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: INK }}>{title}</div>
            <div style={{ marginTop: 6, fontSize: 13, color: INK2, lineHeight: 1.6 }}>{body}</div>
          </div>
        ))}
      </div>

      <Section title="1. 처리하는 개인정보 항목과 목적">
        {isMobile ? <ProcessingItemsCards /> : <ProcessingItemsTable />}
      </Section>

      <Section title="2. 보유 및 이용기간">
        <p style={noteBoxStyle}>
          회원 정보는 회원 탈퇴 시 지체 없이 삭제하는 것을 원칙으로 합니다. 다만 부정 이용 방지, 분쟁 대응, 법령상 의무 이행을 위해 필요한 정보는 목적 달성 또는 법령에서 정한 기간까지 보관할 수 있습니다.
          선택 동의로 수집한 추천용 정보와 민감정보는 동의 철회 또는 회원 탈퇴 시 삭제합니다.
        </p>
      </Section>

      <Section title="3. 선택 동의와 동의 철회">
        <p style={noteBoxStyle}>
          추천용 선택 정보와 민감정보 제공에 동의하지 않아도 회원가입과 기본 서비스 이용은 가능합니다.
          다만 일부 맞춤 추천의 정확도가 낮아질 수 있습니다. 이용자는 마이페이지에서 선택 정보를 수정하거나 삭제할 수 있으며, 민감정보 제공 동의는 언제든 철회할 수 있습니다.
        </p>
      </Section>

      <Section title="4. 자동화 추천 및 AI 처리 안내">
        <p style={noteBoxStyle}>
          맞춤 추천, 추천 사유, 챗봇 답변, 정책 상세의 AI 신청 준비하기는 이용자가 입력한 프로필과 정책 후보 정보를 바탕으로 자동화된 방식으로 생성될 수 있습니다.
          이 결과는 정책 탐색을 돕는 참고 정보이며, 신청 자격 확정이나 행정상 최종 결정을 대신하지 않습니다.
        </p>
        <p style={{ ...noteBoxStyle, marginTop: 10 }}>
          AI 기능에는 질문 내용, 최근 대화 일부, 정책 후보와 근거 문장, 범주형 추천 신호가 사용될 수 있습니다.
          서비스는 이름, 이메일, 생년월일, 전화번호 등 직접 식별자를 외부 AI 요청에 포함하지 않도록 최소화와 비식별 처리를 적용합니다.
          이용자는 마이페이지에서 추천용 정보를 수정하거나 삭제할 수 있고, 고객센터를 통해 AI 추천·답변에 대한 설명과 정정을 요청할 수 있습니다.
        </p>
      </Section>

      <Section title="5. 만 14세 미만 아동의 개인정보">
        <p style={noteBoxStyle}>
          청년복지플랫폼은 현재 법정대리인 동의 확인 절차를 제공하지 않으므로 만 14세 미만 아동의 회원가입을 제한합니다.
          생년월일 기준으로 만 14세 미만인 경우 가입 절차를 완료할 수 없습니다.
        </p>
      </Section>

      <Section title="6. 제3자 제공 및 처리위탁">
        <p style={noteBoxStyle}>
          청년복지플랫폼은 법령에 근거가 있거나 이용자의 별도 동의가 있는 경우를 제외하고 개인정보를 제3자에게 제공하지 않습니다.
          이메일 발송, 인프라 운영, AI 답변 생성, 분석 도구 등 외부 서비스에 개인정보 처리를 위탁하거나 국외 이전이 필요한 경우
          수탁자, 이전되는 항목, 목적, 보유기간, 거부 방법 등 필요한 사항을 이 방침 또는 별도 안내로 공개합니다.
        </p>
      </Section>

      <Section title="7. 정보주체의 권리">
        <p style={noteBoxStyle}>
          이용자는 개인정보 열람, 정정, 삭제, 처리정지, 동의 철회를 요청할 수 있습니다.
          자동화된 추천 또는 AI 답변에 대해서도 설명, 정정, 재검토 요청을 할 수 있습니다.
          요청은 서비스 내 문의 채널 또는 운영자가 공지한 연락처를 통해 접수할 수 있으며, 본인 확인 후 관련 법령에 따라 처리합니다.
        </p>
      </Section>

      <Section title="8. 안전성 확보조치">
        <p style={noteBoxStyle}>
          서비스는 비밀번호 해시 처리, 개인정보 암호화, 접근권한 분리, 접속기록 관리, 민감 정보 접근 제한 등 개인정보 보호를 위한 기술적·관리적 조치를 적용합니다.
        </p>
      </Section>

      <Section title="9. 개인정보 보호책임자">
        <p style={noteBoxStyle}>
          개인정보 보호책임자는 청년복지플랫폼 운영팀입니다.
          개인정보 관련 문의, 권리 행사, 피해 구제 요청은 서비스 내 문의 채널로 접수할 수 있으며, 운영 연락처가 확정되면 이 방침에 추가로 공지합니다.
        </p>
      </Section>

      <p style={{ color: INK3, fontSize: 13, margin: "24px 0 0" }}>
        시행일: 2026년 7월 21일
      </p>
    </>
  );
}
