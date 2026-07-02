import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";

const BG = "#f7f8fc";
const INK = "#11131a";
const MUTED = "#5f6675";
const BLUE = "#2563eb";
const BLUE_DARK = "#1d4ed8";
const LINE = "#d9deea";
const WHITE = "#ffffff";

export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div style={{ minHeight: "100vh", background: BG }}>
      <Header />
      <div
        style={{
          maxWidth: 960,
          margin: "0 auto",
          padding: "64px 20px 120px",
        }}
      >
        <section
          style={{
            display: "grid",
            gap: 22,
            alignItems: "start",
            padding: "44px 0",
          }}
        >
          <div
            style={{
              fontSize: 13,
              fontWeight: 800,
              color: BLUE_DARK,
              letterSpacing: "0.08em",
            }}
          >
            404
          </div>
          <div>
            <h1
              style={{
                margin: 0,
                color: INK,
                fontSize: "clamp(34px, 6vw, 56px)",
                lineHeight: 1.08,
                letterSpacing: 0,
                fontWeight: 800,
              }}
            >
              페이지를 찾을 수 없습니다
            </h1>
            <p
              style={{
                margin: "16px 0 0",
                maxWidth: 620,
                color: MUTED,
                fontSize: 16,
                lineHeight: 1.75,
              }}
            >
              주소가 바뀌었거나 더 이상 제공하지 않는 경로입니다. 홈으로 돌아가거나 정책 검색에서 필요한 정보를 다시 찾을 수 있습니다.
            </p>
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
            <button
              type="button"
              onClick={() => navigate("/")}
              style={{
                minHeight: 44,
                padding: "0 18px",
                borderRadius: 8,
                border: `1px solid ${BLUE}`,
                background: BLUE,
                color: WHITE,
                fontSize: 15,
                fontWeight: 800,
                cursor: "pointer",
              }}
            >
              홈으로
            </button>
            <button
              type="button"
              onClick={() => navigate("/policies")}
              style={{
                minHeight: 44,
                padding: "0 18px",
                borderRadius: 8,
                border: `1px solid ${LINE}`,
                background: WHITE,
                color: INK,
                fontSize: 15,
                fontWeight: 800,
                cursor: "pointer",
              }}
            >
              정책 검색
            </button>
          </div>
        </section>
      </div>
      <FloatingNav />
    </div>
  );
}
