import { useNavigate } from "react-router-dom";

const A = "#2563eb";
const BG = "#f7f8fc";
const WHITE = "#fff";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const LINE = "#e5e7eb";
const SOFT = "#f8fafc";

const sectionStyle = {
  padding: "24px 0",
  borderTop: `1px solid ${LINE}`,
};

const paragraphStyle = {
  color: INK2,
  lineHeight: 1.75,
  fontSize: 14,
  margin: 0,
};

const listStyle = {
  margin: "10px 0 0",
  paddingLeft: 18,
  color: INK2,
  lineHeight: 1.75,
  fontSize: 14,
};

function Section({ title, children }) {
  return (
    <section style={sectionStyle}>
      <h2 style={{ fontSize: 20, lineHeight: 1.35, margin: "0 0 14px", color: INK }}>{title}</h2>
      {children}
    </section>
  );
}

export default function TermsPage() {
  const navigate = useNavigate();

  return (
    <main style={{ minHeight: "100vh", background: BG }}>
      <div style={{ maxWidth: 960, margin: "0 auto", padding: "40px 16px 72px" }}>
        <button
          onClick={() => navigate(-1)}
          style={{
            border: `1px solid ${LINE}`,
            background: WHITE,
            borderRadius: 8,
            padding: "9px 13px",
            color: INK2,
            fontWeight: 700,
            cursor: "pointer",
            marginBottom: 18,
          }}
        >
          이전
        </button>

        <div style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 8, padding: "34px 32px", boxShadow: "0 4px 18px rgba(15,23,42,0.04)" }}>
          <p style={{ color: A, fontSize: 13, fontWeight: 800, margin: "0 0 8px" }}>청년복지플랫폼</p>
          <h1 style={{ fontSize: 30, lineHeight: 1.25, margin: "0 0 10px", color: INK }}>이용약관</h1>
          <p style={{ color: INK3, lineHeight: 1.7, margin: 0 }}>
            청년복지플랫폼을 이용할 때 필요한 기본 약속입니다. 정책을 찾고 비교하는 데 도움이 되도록 서비스를 제공하고,
            이용자는 원문 공고와 실제 신청 안내를 함께 확인해 주세요.
          </p>

          <div style={{ marginTop: 22, background: SOFT, border: `1px solid ${LINE}`, borderRadius: 8, padding: "18px 18px 16px" }}>
            <div style={{ fontSize: 14, fontWeight: 800, color: INK, marginBottom: 8 }}>먼저 확인해 주세요</div>
            <ul style={listStyle}>
              <li>이 서비스는 정책 정보를 쉽게 찾도록 돕는 안내 서비스입니다.</li>
              <li>추천 결과와 챗봇 답변은 참고용이며, 신청 가능 여부를 확정하지 않습니다.</li>
              <li>정책 내용은 원문 공고, 신청 페이지, 운영기관 안내가 우선합니다.</li>
            </ul>
          </div>

          <Section title="1. 약관의 적용">
            <p style={paragraphStyle}>
              이 약관은 청년복지플랫폼이 제공하는 정책 검색, 맞춤 추천, 챗봇, 북마크, 알림, 고객센터 이용에 적용됩니다.
              회원가입을 하거나 서비스를 계속 이용하면 이 약관을 확인하고 동의한 것으로 봅니다.
            </p>
          </Section>

          <Section title="2. 제공하는 서비스">
            <p style={paragraphStyle}>
              청년복지플랫폼은 온통청년, 복지로, 정부24 및 각 운영기관 공고를 바탕으로 청년 관련 정책 정보를 보여줍니다.
              이용자는 정책을 검색하고, 관심 정책을 저장하고, 입력한 정보를 기준으로 추천과 알림을 받을 수 있습니다.
            </p>
            <ul style={listStyle}>
              <li>정책 검색과 필터</li>
              <li>회원 정보와 관심사에 따른 맞춤 추천</li>
              <li>정책 관련 질문을 위한 챗봇</li>
              <li>북마크, 최근 본 정책, 알림, 문의 접수</li>
            </ul>
          </Section>

          <Section title="3. 회원가입과 계정 관리">
            <p style={paragraphStyle}>
              회원은 본인의 정확한 정보로 가입해야 합니다. 이메일, 비밀번호, 알림 설정 등 계정 정보는 스스로 관리해야 하며,
              다른 사람의 계정을 사용하거나 본인 계정을 다른 사람이 쓰게 해서는 안 됩니다.
            </p>
            <p style={{ ...paragraphStyle, marginTop: 10 }}>
              계정 도용이 의심되거나 비밀번호가 노출된 것 같다면 비밀번호를 바꾸고 고객센터로 알려주세요.
            </p>
          </Section>

          <Section title="4. 정책 정보 이용 시 주의할 점">
            <p style={paragraphStyle}>
              서비스에 표시되는 정책 정보는 수집 시점의 자료를 보기 쉽게 정리한 것입니다. 모집 기간, 예산 소진, 지역 제한,
              세부 자격요건은 운영기관 사정에 따라 바뀔 수 있습니다.
            </p>
            <p style={{ ...paragraphStyle, marginTop: 10 }}>
              실제 신청 전에는 상세 페이지의 원문 링크, 신청 페이지, 운영기관 공지를 반드시 확인해야 합니다.
            </p>
          </Section>

          <Section title="5. 추천, 챗봇, 알림">
            <p style={paragraphStyle}>
              맞춤 추천은 이용자가 입력한 지역, 생년월일, 소득수준, 취업상태, 주거·복지 관련 선택 정보를 바탕으로 계산됩니다.
              선택 정보를 적게 입력하면 추천이 넓게 보일 수 있습니다.
            </p>
            <p style={{ ...paragraphStyle, marginTop: 10 }}>
              챗봇 답변과 알림은 정책 탐색을 돕기 위한 보조 기능입니다. 법률, 세무, 의료, 행정상 최종 판단이나 신청 자격 확정을 대신하지 않습니다.
            </p>
          </Section>

          <Section title="6. 이용자가 지켜야 할 사항">
            <p style={paragraphStyle}>다음과 같은 이용은 제한될 수 있습니다.</p>
            <ul style={listStyle}>
              <li>허위 정보로 가입하거나 타인의 정보를 사용하는 행위</li>
              <li>자동화 도구로 과도하게 요청하거나 서비스를 방해하는 행위</li>
              <li>보안 취약점을 악용하거나 개인정보를 침해하는 행위</li>
              <li>정책 데이터, 화면, 기능을 무단으로 복제하거나 재배포하는 행위</li>
              <li>고객센터나 오류 제보 기능을 허위·악성 목적으로 사용하는 행위</li>
            </ul>
          </Section>

          <Section title="7. 서비스 변경과 이용 제한">
            <p style={paragraphStyle}>
              데이터 제공기관의 사정, 시스템 점검, 보안 조치, 기능 개선이 필요한 경우 서비스의 전부 또는 일부가 바뀌거나 일시적으로 중단될 수 있습니다.
              중요한 변경은 서비스 화면이나 공지 수단을 통해 안내합니다.
            </p>
            <p style={{ ...paragraphStyle, marginTop: 10 }}>
              약관 위반이나 비정상적인 이용이 확인되면 사전 안내 후 이용을 제한할 수 있습니다. 긴급한 보안 문제가 있으면 먼저 제한한 뒤 안내할 수 있습니다.
            </p>
          </Section>

          <Section title="8. 문의와 오류 제보">
            <p style={paragraphStyle}>
              서비스 이용 중 불편한 점은 고객센터로 문의할 수 있습니다. 정책의 지역, 신청 기간, 자격조건, 링크가 실제와 다르게 보이면
              정책 상세 페이지의 오류 제보를 이용해 주세요. 접수된 내용은 운영자가 확인한 뒤 필요한 경우 데이터 정정이나 표시 개선에 반영합니다.
            </p>
          </Section>

          <Section title="9. 책임의 범위">
            <p style={paragraphStyle}>
              청년복지플랫폼은 정확한 정보를 제공하기 위해 노력합니다. 다만 외부 기관의 원문 변경, 신청 마감, 예산 소진,
              이용자의 원문 미확인으로 생긴 불이익까지 보장하지는 않습니다.
            </p>
            <p style={{ ...paragraphStyle, marginTop: 10 }}>
              서비스의 고의 또는 중대한 과실이 있는 경우에는 관련 법령에 따라 책임을 부담합니다.
            </p>
          </Section>

          <Section title="10. 약관 변경">
            <p style={paragraphStyle}>
              약관을 바꿀 때에는 적용일과 변경 이유를 서비스 화면에 안내합니다. 이용자에게 불리하거나 중요한 변경은 적용 전에 충분한 기간을 두고 알립니다.
              변경된 약관에 동의하지 않으면 서비스 이용을 중단하거나 회원 탈퇴를 할 수 있습니다.
            </p>
          </Section>

          <Section title="11. 기준 법령과 분쟁 해결">
            <p style={paragraphStyle}>
              이 약관은 대한민국 법령을 기준으로 해석합니다. 서비스 이용과 관련한 분쟁은 먼저 고객센터를 통해 해결을 시도하고,
              필요한 경우 관련 법령에서 정한 절차에 따릅니다.
            </p>
          </Section>

          <p style={{ color: INK3, fontSize: 13, margin: "24px 0 0" }}>
            시행일: 2026년 6월 10일
          </p>
        </div>
      </div>
    </main>
  );
}
