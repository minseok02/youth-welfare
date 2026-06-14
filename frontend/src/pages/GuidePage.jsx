import { useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useMediaQuery } from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import { useAuthStore } from "../store/authStore";

const A = "#2563eb";
const A7 = "#1d4ed8";
const AI = "#1e3a8a";
const BG = "#f7f8fc";
const WHITE = "#ffffff";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const LINE = "#e5e7eb";
const AS = "#e8efff";
const OK_BG = "#ecfdf5";
const OK = "#047857";
const WARN_BG = "#fff7ed";
const WARN = "#c2410c";

const QUICK_STEPS = [
  {
    step: "1",
    title: "로그인하고 기본 정보를 맞춰요",
    body: "지역과 기본 프로필이 맞아야 정책 검색과 추천이 지역·연령 기준으로 흔들리지 않습니다.",
  },
  {
    step: "2",
    title: "우선순위를 먼저 고르세요",
    body: "주거, 일자리, 교육처럼 지금 중요한 축을 선택하면 추천 상단 쏠림이 줄고 메모도 더 구체적으로 나옵니다.",
  },
  {
    step: "3",
    title: "주거·생활 여건을 채우세요",
    body: "주거형태, 주택유형, 기초생활수급권자, 장애등급은 추천 정확도를 직접 끌어올리는 입력값입니다.",
  },
  {
    step: "4",
    title: "추천, 검색, 챗봇을 같이 쓰세요",
    body: "추천으로 후보를 좁히고, 검색으로 비교하고, 챗봇으로 후속 질문을 이어가면 탐색 속도가 빨라집니다.",
  },
];

const FEATURE_CARDS = [
  {
    title: "맞춤 추천",
    desc: "프로필과 우선순위를 기준으로 지금 볼 정책을 먼저 보여줍니다.",
    cta: "추천 받으러 가기",
    target: "recommend",
  },
  {
    title: "정책 검색",
    desc: "카테고리, 지역, 소득, 출처까지 직접 걸러서 비교할 수 있습니다.",
    cta: "정책 검색하기",
    target: "policies",
  },
  {
    title: "AI 챗봇",
    desc: "월세, 전세, 청약처럼 후속 질문을 이어가며 정책을 좁히는 데 유용합니다.",
    cta: "챗봇 열기",
    target: "chat",
  },
  {
    title: "북마크·알림",
    desc: "나중에 볼 정책을 저장하고, 추천 알림과 마감 임박 알림으로 다시 이어받을 수 있습니다.",
    cta: "알림함 보기",
    target: "alerts",
  },
  {
    title: "서비스 문의",
    desc: "로그인, 추천, 챗봇, 검색, 알림처럼 서비스를 쓰다가 막힌 경우 문의를 남길 수 있습니다.",
    cta: "문의 남기기",
    target: "support",
  },
];

const ACCURACY_ITEMS = [
  "지역 정보는 시·군·구까지 정확히 맞추세요. 지역 기준이 어긋나면 추천 후보가 불필요하게 넓어질 수 있습니다.",
  "우선순위를 비워두면 비슷한 정책이 상단에 몰릴 수 있습니다. 최소 1개는 고르는 편이 좋습니다.",
  "주거형태·주택유형·기초생활수급권자·장애등급 입력은 추천 메모와 후보 매칭 품질에 직접 반영됩니다.",
  "챗봇 답변은 탐색 보조입니다. 최종 자격조건은 상세 페이지와 원문 공고에서 다시 확인해야 합니다.",
];

const FAQ_ITEMS = [
  {
    q: "추천에 떴다고 바로 신청 가능한 건가요?",
    a: "아닙니다. 추천은 후보를 먼저 좁혀주는 단계입니다. 신청 전에는 상세 페이지의 자격요건과 운영기관 원문을 다시 확인해야 합니다.",
  },
  {
    q: "챗봇이 정책을 바로 확정해주나요?",
    a: "챗봇은 후속 질문을 이어가며 후보를 찾는 용도입니다. 최종 기준은 정책 상세 정보와 공고문입니다.",
  },
  {
    q: "왜 우선순위와 주거·생활 여건을 계속 입력하라고 하나요?",
    a: "이 값들이 있어야 추천 상단이 덜 퍼지고, 주거·복지 조건에 맞는 정책이 더 앞으로 올라옵니다.",
  },
];

function SectionShell({ eyebrow, title, desc, children }) {
  return (
    <section style={{ marginTop: 48 }}>
      <div style={{ marginBottom: 18 }}>
        {eyebrow && (
          <div style={{ fontSize: 12, fontWeight: 800, color: A7, letterSpacing: "0.08em", marginBottom: 8 }}>
            {eyebrow}
          </div>
        )}
        <h2 style={{ margin: 0, fontSize: 28, lineHeight: 1.25, letterSpacing: "-0.03em", color: INK }}>
          {title}
        </h2>
        {desc && (
          <p style={{ margin: "10px 0 0", fontSize: 15, lineHeight: 1.7, color: INK2, maxWidth: 780 }}>
            {desc}
          </p>
        )}
      </div>
      {children}
    </section>
  );
}

export default function GuidePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const isMobile = useMediaQuery("(max-width: 1199px)");
  const { isLoggedIn } = useAuthStore();

  const authState = useMemo(() => ({ from: location }), [location]);

  const moveToTarget = (target) => {
    if (target === "recommend") {
      if (isLoggedIn) {
        navigate("/", { state: { fromGuide: true, focusRecommendation: true } });
        return;
      }
      navigate("/login", { state: authState });
      return;
    }

    if (target === "policies") {
      navigate("/policies");
      return;
    }

    if (target === "chat") {
      if (isLoggedIn) {
        navigate("/chat");
        return;
      }
      navigate("/login", { state: authState });
      return;
    }

    if (target === "alerts") {
      if (isLoggedIn) {
        navigate("/alerts");
        return;
      }
      navigate("/login", { state: authState });
      return;
    }

    if (target === "support") {
      navigate("/support", { state: authState });
      return;
    }

    if (target === "profile") {
      if (isLoggedIn) {
        navigate("/mypage?tab=0");
        return;
      }
      navigate("/login", { state: authState });
      return;
    }

    if (target === "priority") {
      if (isLoggedIn) {
        navigate("/mypage?tab=1");
        return;
      }
      navigate("/login", { state: authState });
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: BG }}>
      <Header />

      <main style={{ maxWidth: 1160, margin: "0 auto", padding: isMobile ? "22px 16px 110px" : "28px 24px 120px" }}>
        <section
          style={{
            position: "relative",
            overflow: "hidden",
            borderRadius: 30,
            background: "linear-gradient(135deg, #1e3a8a 0%, #2563eb 56%, #60a5fa 100%)",
            color: WHITE,
            padding: isMobile ? "28px 20px" : "44px 48px",
          }}
        >
          <div style={{ position: "absolute", right: -70, top: -70, width: 250, height: 250, borderRadius: "50%", background: "rgba(255,255,255,0.08)" }} />
          <div style={{ position: "absolute", right: 110, bottom: -90, width: 180, height: 180, borderRadius: "50%", background: "rgba(255,255,255,0.06)" }} />

          <div style={{ position: "relative", zIndex: 1, display: "grid", gridTemplateColumns: isMobile ? "1fr" : "1.25fr 0.95fr", gap: 28, alignItems: "center" }}>
            <div>
              <div style={{ display: "inline-flex", alignItems: "center", gap: 8, padding: "7px 14px", borderRadius: 999, background: "rgba(255,255,255,0.16)", fontSize: 12, fontWeight: 800, letterSpacing: "0.05em" }}>
                GUIDE
              </div>
              <h1 style={{ margin: "18px 0 12px", fontSize: isMobile ? 30 : 42, lineHeight: 1.18, letterSpacing: "-0.04em" }}>
                청년복지플랫폼을
                <br />
                가장 빠르게 쓰는 방법
              </h1>
              <p style={{ margin: 0, maxWidth: 680, fontSize: 15, lineHeight: 1.75, color: "rgba(255,255,255,0.88)" }}>
                추천, 정책 검색, 챗봇, 북마크, 알림은 서로 따로가 아니라 같이 쓸 때 가장 효율이 좋습니다.
                처음이라면 이 순서대로 사용하시길 추천드립니다.
              </p>
              <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginTop: 24 }}>
                <button
                  onClick={() => moveToTarget("recommend")}
                  style={{ padding: "14px 20px", borderRadius: 14, border: 0, background: WHITE, color: A7, fontSize: 14, fontWeight: 800, cursor: "pointer" }}
                >
                  추천부터 시작하기
                </button>
                <button
                  onClick={() => moveToTarget("policies")}
                  style={{ padding: "14px 20px", borderRadius: 14, border: "1px solid rgba(255,255,255,0.25)", background: "rgba(255,255,255,0.10)", color: WHITE, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
                >
                  정책 검색하기
                </button>
                <button
                  onClick={() => moveToTarget("chat")}
                  style={{ padding: "14px 20px", borderRadius: 14, border: "1px solid rgba(255,255,255,0.25)", background: "rgba(255,255,255,0.10)", color: WHITE, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
                >
                  챗봇 열기
                </button>
              </div>
            </div>

            <div style={{ display: "grid", gap: 12 }}>
              <div style={{ background: "rgba(255,255,255,0.12)", border: "1px solid rgba(255,255,255,0.18)", borderRadius: 18, padding: 18 }}>
                <div style={{ fontSize: 12, fontWeight: 800, letterSpacing: "0.05em", color: "rgba(255,255,255,0.78)" }}>
                  먼저 해두면 좋은 것
                </div>
                <div style={{ marginTop: 10, fontSize: 17, fontWeight: 800 }}>
                  우선순위 설정 + 주거·생활 여건 입력
                </div>
                <div style={{ marginTop: 6, fontSize: 13, lineHeight: 1.65, color: "rgba(255,255,255,0.84)" }}>
                  추천 상단 쏠림을 줄이고, 주거·복지 조건에 맞는 정책을 더 빨리 위로 끌어올립니다.
                </div>
              </div>
              <div style={{ background: WHITE, borderRadius: 18, padding: 18, color: INK, boxShadow: "0 16px 36px rgba(15,23,42,0.14)" }}>
                <div style={{ fontSize: 12, fontWeight: 800, color: A7, letterSpacing: "0.05em" }}>
                  빠른 루트
                </div>
                <div style={{ marginTop: 10, display: "grid", gap: 10 }}>
                  {["추천으로 후보 보기", "검색으로 직접 비교", "챗봇으로 후속 질문", "북마크·알림으로 다시 받기"].map((item, index) => (
                    <div key={item} style={{ display: "grid", gridTemplateColumns: "26px 1fr", gap: 10, alignItems: "start" }}>
                      <div style={{ width: 26, height: 26, borderRadius: "50%", background: AS, color: AI, fontSize: 12, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center" }}>
                        {index + 1}
                      </div>
                      <div style={{ paddingTop: 3, fontSize: 13, fontWeight: 700, color: INK }}>
                        {item}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </section>

        <SectionShell
          eyebrow="QUICK START"
          title="처음이라면 이 순서로 사용해 보세요"
          desc="처음부터 모든 기능을 다 보지 않아도 됩니다. 아래 네 단계만 따라가면 추천과 검색 품질이 눈에 띄게 안정됩니다."
        >
          <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "repeat(2, 1fr)", gap: 14 }}>
            {QUICK_STEPS.map((item) => (
              <div key={item.step} style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 20, padding: 22 }}>
                <div style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 34, height: 34, borderRadius: "50%", background: AS, color: AI, fontSize: 14, fontWeight: 900 }}>
                  {item.step}
                </div>
                <div style={{ marginTop: 14, fontSize: 18, fontWeight: 800, color: INK, lineHeight: 1.35 }}>
                  {item.title}
                </div>
                <div style={{ marginTop: 8, fontSize: 14, lineHeight: 1.7, color: INK2 }}>
                  {item.body}
                </div>
              </div>
            ))}
          </div>
        </SectionShell>

        <SectionShell
          eyebrow="FEATURES"
          title="기능별로 이렇게 쓰면 됩니다"
          desc="하나만 쓰는 것보다 연결해서 쓸 때 훨씬 편합니다. 각 기능마다 바로 이동할 수 있는 버튼을 붙여뒀습니다."
        >
          <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "repeat(2, 1fr)", gap: 14 }}>
            {FEATURE_CARDS.map((card) => (
              <div key={card.title} style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 20, padding: 22 }}>
                <div style={{ fontSize: 18, fontWeight: 800, color: INK }}>{card.title}</div>
                <div style={{ marginTop: 8, fontSize: 14, lineHeight: 1.7, color: INK2 }}>{card.desc}</div>
                <button
                  onClick={() => moveToTarget(card.target)}
                  style={{ marginTop: 18, padding: "11px 14px", borderRadius: 12, border: `1px solid ${LINE}`, background: AS, color: AI, fontSize: 13, fontWeight: 800, cursor: "pointer" }}
                >
                  {card.cta}
                </button>
              </div>
            ))}
          </div>
        </SectionShell>

        <SectionShell
          eyebrow="ACCURACY"
          title="추천이 더 정확해지는 방법"
          desc="이 서비스는 입력을 많이 요구하려는 게 아니라, 추천과 필터가 흔들리지 않도록 최소한의 기준값을 받는 구조입니다."
        >
          <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "1.15fr 0.85fr", gap: 14 }}>
            <div style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 20, padding: 22 }}>
              <div style={{ display: "grid", gap: 12 }}>
                {ACCURACY_ITEMS.map((item) => (
                  <div key={item} style={{ display: "grid", gridTemplateColumns: "12px 1fr", gap: 12, alignItems: "start" }}>
                    <div style={{ width: 12, height: 12, borderRadius: "50%", background: A, marginTop: 5 }} />
                    <div style={{ fontSize: 14, lineHeight: 1.7, color: INK2 }}>{item}</div>
                  </div>
                ))}
              </div>
            </div>
            <div style={{ display: "grid", gap: 14 }}>
              <div style={{ background: OK_BG, border: "1px solid #d1fae5", borderRadius: 20, padding: 22 }}>
                <div style={{ fontSize: 13, fontWeight: 900, letterSpacing: "0.04em", color: OK }}>추천 정확도 보강</div>
                <div style={{ marginTop: 10, fontSize: 18, fontWeight: 800, lineHeight: 1.35, color: INK }}>
                  주거·생활 여건 4개와 우선순위가 가장 직접적입니다
                </div>
                <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.65, color: INK2 }}>
                  주거형태, 주택유형, 기초생활수급권자, 장애등급은 추천 메모와 주거·복지 정책 매칭에 바로 반영됩니다.
                </div>
                <button
                  onClick={() => moveToTarget("profile")}
                  style={{ marginTop: 16, padding: "10px 14px", borderRadius: 12, border: "1px solid #a7f3d0", background: WHITE, color: OK, fontSize: 13, fontWeight: 800, cursor: "pointer" }}
                >
                  주거·생활 여건 채우러 가기
                </button>
              </div>
              <div style={{ background: WARN_BG, border: "1px solid #fed7aa", borderRadius: 20, padding: 22 }}>
                <div style={{ fontSize: 13, fontWeight: 900, letterSpacing: "0.04em", color: WARN }}>상단 쏠림 방지</div>
                <div style={{ marginTop: 10, fontSize: 18, fontWeight: 800, lineHeight: 1.35, color: INK }}>
                  우선순위가 비어 있으면 비슷한 추천이 늘어납니다
                </div>
                <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.65, color: INK2 }}>
                  주거, 일자리, 교육처럼 지금 중요한 축을 최소 하나 고르면 상단 추천 메모와 후보 분산이 더 자연스러워집니다.
                </div>
                <button
                  onClick={() => moveToTarget("priority")}
                  style={{ marginTop: 16, padding: "10px 14px", borderRadius: 12, border: "1px solid #fdba74", background: WHITE, color: WARN, fontSize: 13, fontWeight: 800, cursor: "pointer" }}
                >
                  우선순위 설정하기
                </button>
              </div>
            </div>
          </div>
        </SectionShell>

        <SectionShell
          eyebrow="FAQ"
          title="자주 헷갈리는 점"
          desc="추천과 챗봇은 탐색을 돕는 층이고, 최종 자격 판정은 상세 공고와 운영기관 기준이 우선입니다."
        >
          <div style={{ display: "grid", gap: 14 }}>
            {FAQ_ITEMS.map((item) => (
              <div key={item.q} style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 20, padding: 22 }}>
                <div style={{ fontSize: 17, fontWeight: 800, color: INK }}>{item.q}</div>
                <div style={{ marginTop: 8, fontSize: 14, lineHeight: 1.7, color: INK2 }}>{item.a}</div>
              </div>
            ))}
          </div>
        </SectionShell>

        <section style={{ marginTop: 52, background: WHITE, border: `1px solid ${LINE}`, borderRadius: 24, padding: isMobile ? "24px 18px" : "30px 30px 28px" }}>
          <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "1fr auto", gap: 18, alignItems: "center" }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 900, color: A7, letterSpacing: "0.06em" }}>START NOW</div>
              <div style={{ marginTop: 8, fontSize: 28, fontWeight: 800, lineHeight: 1.25, color: INK }}>
                지금은 추천부터 볼지, 검색부터 할지 고르면 됩니다
              </div>
              <div style={{ marginTop: 8, fontSize: 14, lineHeight: 1.7, color: INK2 }}>
                처음엔 추천으로 후보를 좁히고, 조금 익숙해지면 검색과 챗봇을 같이 쓰는 흐름이 가장 편합니다.
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap", justifyContent: isMobile ? "flex-start" : "flex-end" }}>
              <button
                onClick={() => moveToTarget("recommend")}
                style={{ padding: "13px 18px", borderRadius: 12, border: 0, background: A, color: WHITE, fontSize: 14, fontWeight: 800, cursor: "pointer" }}
              >
                추천 보기
              </button>
              <button
                onClick={() => moveToTarget("policies")}
                style={{ padding: "13px 18px", borderRadius: 12, border: `1px solid ${LINE}`, background: WHITE, color: INK, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
              >
                정책 검색
              </button>
              <button
                onClick={() => moveToTarget("chat")}
                style={{ padding: "13px 18px", borderRadius: 12, border: `1px solid ${LINE}`, background: WHITE, color: INK, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
              >
                챗봇 사용
              </button>
              <button
                onClick={() => moveToTarget("support")}
                style={{ padding: "13px 18px", borderRadius: 12, border: `1px solid ${LINE}`, background: WHITE, color: INK, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
              >
                서비스 문의
              </button>
            </div>
          </div>
        </section>
      </main>

      <FloatingNav />
    </div>
  );
}
