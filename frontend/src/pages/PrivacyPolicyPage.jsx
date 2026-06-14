import { useNavigate } from "react-router-dom";
import PrivacyPolicyContent from "../components/PrivacyPolicyContent";

const BG = "#f7f8fc";
const WHITE = "#fff";
const INK2 = "#4a4f5c";
const LINE = "#e5e7eb";

export default function PrivacyPolicyPage() {
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
          <PrivacyPolicyContent />
        </div>
      </div>
    </main>
  );
}
