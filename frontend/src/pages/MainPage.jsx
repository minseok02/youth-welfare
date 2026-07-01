import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Snackbar, Alert, useMediaQuery } from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import CategoryIcon from "../components/CategoryIcon";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";
import { resolveStandardProfileCodeCompletion } from "../lib/profileStandardCodes";
import { buildSafeReturnLocation, sanitizeTransientRouteState } from "../lib/safeNavigation";

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

const SOURCE_LABEL_BY_TYPE = {
  YOUTH: "온통청년",
  BOKJIRO_CENTRAL: "복지로 중앙",
  BOKJIRO_LOCAL: "복지로 지자체",
  GOV24: "정부24",
};

const FOOTER_LINK_STYLE = {
  background: "none",
  border: 0,
  padding: 0,
  color: "inherit",
  font: "inherit",
  cursor: "pointer",
};

const formatDday = (dateText, status) => {
  if (status === "CLOSED") return "종료";
  if (!dateText) return status === "UPCOMING" ? "예정" : "상시";
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const end = new Date(`${dateText}T00:00:00`);
  if (Number.isNaN(end.getTime())) return "상시";
  const diff = Math.ceil((end - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
};

const mapRec = (r) => ({
  id: r.serviceId,
  logId: r.logId ?? null,
  title: r.title,
  category: r.unifiedCategory || "기타",
  dday: formatDday(r.applyEndDate, r.status),
  summary: r.description || "",
  sourceType: r.sourceType || "",
  sourceTypeLabel: SOURCE_LABEL_BY_TYPE[r.sourceType] || "",
  source: r.hostOrg || r.sido || r.operatingOrg || "",
  aiReason: r.aiReason,
  aiStatus: r.aiStatus || "",
  youthMajorLabel: r.youthMajorLabel || "",
  youthMidLabel: r.youthMidLabel || "",
  provisionMethodLabel: r.provisionMethodLabel || "",
  gov24ServiceFieldLabel: r.gov24ServiceFieldLabel || "",
  gov24UserTypeLabel: r.gov24UserTypeLabel || "",
  gov24BenefitTypeLabel: r.gov24BenefitTypeLabel || "",
  bookmarked: Boolean(r.isBookmarked),
});

const nonBlank = (value) => (value || "").trim();

const compactJoin = (values, limit = 2) => {
  const unique = [];
  values.forEach((value) => {
    const trimmed = nonBlank(value);
    if (!trimmed || unique.includes(trimmed)) {
      return;
    }
    unique.push(trimmed);
  });
  return unique.slice(0, limit);
};

const buildFallbackRecommendationMemo = (rec) => {
  const supportLabels = compactJoin([
    rec.youthMidLabel,
    rec.provisionMethodLabel,
    rec.gov24BenefitTypeLabel,
    rec.gov24ServiceFieldLabel,
    rec.youthMajorLabel,
    rec.category !== "기타" ? rec.category : "",
  ]);

  if (supportLabels.length >= 2) {
    return `${supportLabels[0]} · ${supportLabels[1]} 기준으로 먼저 보여드렸어요.`;
  }
  if (supportLabels.length === 1) {
    return `${supportLabels[0]} 기준으로 먼저 보여드렸어요.`;
  }

  const audienceLabels = compactJoin([
    rec.gov24UserTypeLabel,
    rec.source,
    rec.sourceTypeLabel ? `${rec.sourceTypeLabel} 정책` : "",
  ], 1);

  if (audienceLabels.length === 1) {
    return `${audienceLabels[0]} 분류를 기준으로 먼저 보여드렸어요.`;
  }

  return "정책 분류와 기본 자격 신호를 기준으로 먼저 보여드렸어요.";
};

const resolveRecommendationMemo = (rec) => {
  const aiReason = nonBlank(rec.aiReason);
  if (aiReason) {
    return {
      heading: "추천 메모",
      body: `"${aiReason}"`,
      accentColor: A,
    };
  }

  return {
    heading: rec.aiStatus === "NOT_REQUESTED" ? "추천 단서" : "추천 메모",
    body: buildFallbackRecommendationMemo(rec),
    accentColor: INK2,
  };
};

const mapRecentViewedPolicy = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  dday: formatDday(policy.applyEndDate, policy.status),
  source: policy.hostOrg || policy.sido || policy.operatingOrg || "",
});

// ── 디자인 상수 ───────────────────────────────────────────────────────────────

const A = "#2563eb";
const A7 = "#1d4ed8";
const AS = "#e8efff";
const AS2 = "#f0f5ff";
const AI = "#1e3a8a";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#8b91a0";
const LINE = "#e7e9ef";
const WARN = "#ef4444";
const OK = "#047857";
const OK_BG = "#ecfdf5";
const GUIDE_NUDGE_STORAGE_KEY = "yw-guide-nudge-seen-v1";

const CATEGORY_META = [
  { value: "일자리",        label: "일자리",        icon: "job",           bg: "#dbeafe", fg: "#2563eb" },
  { value: "주거",          label: "주거",          icon: "housing",       bg: "#fef3c7", fg: "#f59e0b" },
  { value: "교육·직업훈련", label: "교육·직업훈련", icon: "education",     bg: "#dcfce7", fg: "#16a34a" },
  { value: "금융·생활지원", label: "금융·생활지원", icon: "finance",       bg: "#fce7f3", fg: "#db2777" },
  { value: "건강·의료",     label: "건강·의료",     icon: "health",        bg: "#e0e7ff", fg: "#0d9488" },
  { value: "참여·기회",     label: "참여·기회",     icon: "participation", bg: "#fed7aa", fg: "#7c3aed" },
];

const POPULAR_CATEGORIES = ["일자리", "교육·직업훈련", "금융·생활지원", "주거", "건강·의료", "참여·기회"];

// ── 서브 컴포넌트 ─────────────────────────────────────────────────────────────

function Tag({ children, style }) {
  return (
    <span style={{
      display: "inline-flex", alignItems: "center", fontSize: 12,
      padding: "3px 10px", borderRadius: 99, fontWeight: 600, ...style,
    }}>
      {children}
    </span>
  );
}

function HeroNonLogin({ totalPolicies, deadlineCount, firstDeadlinePolicy, secondDeadlinePolicy, navigate, authState }) {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  return (
    <section style={{
      background: "linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%)",
      borderRadius: 28, padding: isMobile ? "28px 20px" : "48px 56px", color: "white", marginTop: 24,
      position: "relative", overflow: "hidden",
    }}>
      <div style={{ position: "absolute", right: -80, top: -80, width: 280, height: 280, borderRadius: "50%", background: "rgba(255,255,255,0.08)", pointerEvents: "none" }} />
      <div style={{ position: "absolute", right: 120, bottom: -100, width: 200, height: 200, borderRadius: "50%", background: "rgba(255,255,255,0.06)", pointerEvents: "none" }} />

      <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "1.2fr 1fr", gap: isMobile ? 0 : 40, alignItems: "center", position: "relative", zIndex: 1 }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 28 : 40, fontWeight: 800, letterSpacing: "-0.03em", lineHeight: 1.2, margin: "0 0 14px", color: "white" }}>
            나에게 딱 맞는<br />
            <span style={{ background: "linear-gradient(180deg, transparent 65%, rgba(255,255,255,0.25) 65%)", padding: "0 4px" }}>
              청년 복지정책
            </span>을<br />
            찾아드려요
          </h1>
          <p style={{ fontSize: 15, opacity: 0.85, margin: "0 0 24px", lineHeight: 1.6 }}>
            지역·소득·관심사 입력 1분이면 끝.<br />
            지금 자격되는 정책만 추려드립니다.
          </p>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            <button
              onClick={() => navigate("/login", { state: authState })}
              style={{ padding: "14px 22px", background: "white", color: A7, border: 0, borderRadius: 12, fontSize: 14, fontWeight: 700, cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 8, boxShadow: "0 4px 12px rgba(0,0,0,0.12)" }}
            >
              맞춤 추천 받기 →
            </button>
            <button
              onClick={() => navigate("/policies")}
              style={{ padding: "14px 22px", background: "rgba(255,255,255,0.12)", color: "white", border: "1px solid rgba(255,255,255,0.25)", borderRadius: 12, fontSize: 14, fontWeight: 600, cursor: "pointer" }}
            >
              전체 둘러보기
            </button>
            <button
              onClick={() => navigate("/guide")}
              style={{ padding: "14px 22px", background: "rgba(255,255,255,0.08)", color: "white", border: "1px solid rgba(255,255,255,0.18)", borderRadius: 12, fontSize: 14, fontWeight: 600, cursor: "pointer" }}
            >
              이용가이드
            </button>
          </div>
          <div style={{ display: "flex", gap: isMobile ? 16 : 24, marginTop: 28, fontSize: 13 }}>
            <div>
              <div style={{ fontSize: isMobile ? 18 : 22, fontWeight: 800 }}>{totalPolicies > 0 ? totalPolicies.toLocaleString() : "—"}</div>
              <div style={{ opacity: 0.7, fontSize: 12, marginTop: 2 }}>전체 정책</div>
            </div>
            <div style={{ width: 1, background: "rgba(255,255,255,0.2)" }} />
            <div>
              <div style={{ fontSize: isMobile ? 18 : 22, fontWeight: 800 }}>{deadlineCount > 0 ? `${deadlineCount}건` : "—"}</div>
              <div style={{ opacity: 0.7, fontSize: 12, marginTop: 2 }}>이번 주 마감</div>
            </div>
            <div style={{ width: 1, background: "rgba(255,255,255,0.2)" }} />
            <div>
              <div style={{ fontSize: isMobile ? 18 : 22, fontWeight: 800 }}>무료</div>
              <div style={{ opacity: 0.7, fontSize: 12, marginTop: 2 }}>AI 추천 서비스</div>
            </div>
          </div>
        </div>

        {/* 플로팅 카드 — PC 전용 */}
        {!isMobile && (
          <div style={{ position: "relative", height: 270 }}>
            {firstDeadlinePolicy && (
              <div style={{ position: "absolute", right: 30, top: 8, width: 190, background: "white", borderRadius: 14, padding: 16, color: INK, boxShadow: "0 12px 32px rgba(0,0,0,0.18)", transform: "rotate(-3deg)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Tag style={{ background: "#fef2f2", color: WARN }}>마감임박</Tag>
                  <span style={{ fontSize: 18, fontWeight: 800, color: WARN }}>{firstDeadlinePolicy.dday}</span>
                </div>
                <div style={{ fontSize: 13, fontWeight: 700, marginTop: 10, lineHeight: 1.3, letterSpacing: "-0.01em" }}>
                  {firstDeadlinePolicy.title}
                </div>
                {firstDeadlinePolicy.source && (
                  <div style={{ fontSize: 11, color: INK3, marginTop: 5 }}>{firstDeadlinePolicy.source}</div>
                )}
              </div>
            )}
            {secondDeadlinePolicy && (
              <div style={{ position: "absolute", right: 0, top: 140, width: 200, background: "white", borderRadius: 14, padding: 16, color: INK, boxShadow: "0 12px 32px rgba(0,0,0,0.22)", transform: "rotate(2.5deg)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Tag style={{ background: AS, color: AI }}>{secondDeadlinePolicy.category}</Tag>
                  <span style={{ fontSize: 14, fontWeight: 800, color: WARN }}>{secondDeadlinePolicy.dday}</span>
                </div>
                <div style={{ fontSize: 13, fontWeight: 700, marginTop: 10, letterSpacing: "-0.01em", lineHeight: 1.3 }}>
                  {secondDeadlinePolicy.title}
                </div>
                {secondDeadlinePolicy.source && (
                  <div style={{ fontSize: 11, color: INK3, marginTop: 4 }}>{secondDeadlinePolicy.source}</div>
                )}
                <div style={{ marginTop: 8, paddingTop: 8, borderTop: "1px solid #f3f4f6", display: "flex", alignItems: "center", gap: 4 }}>
                  <span style={{ width: 6, height: 6, borderRadius: "50%", background: "#22c55e", flexShrink: 0 }} />
                  <span style={{ fontSize: 10, color: INK3, fontWeight: 600 }}>실시간 업데이트</span>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function HeroLoggedIn({
  user,
  navigate,
  onPersonalRefresh,
  refreshingRec,
  personalRefreshing,
  totalPolicies,
  deadlineCount,
  firstDeadlinePolicy,
  secondDeadlinePolicy,
}) {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  return (
    <section style={{
      background: "linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%)",
      borderRadius: 28, padding: isMobile ? "28px 20px" : "48px 56px", color: "white", marginTop: 24,
      position: "relative", overflow: "hidden",
    }}>
      <div style={{ position: "absolute", right: -80, top: -80, width: 280, height: 280, borderRadius: "50%", background: "rgba(255,255,255,0.08)", pointerEvents: "none" }} />
      <div style={{ position: "absolute", right: 120, bottom: -100, width: 200, height: 200, borderRadius: "50%", background: "rgba(255,255,255,0.06)", pointerEvents: "none" }} />

      <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "1.2fr 1fr", gap: isMobile ? 0 : 40, alignItems: "center", position: "relative", zIndex: 1 }}>
        <div>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 6, background: "rgba(255,255,255,0.15)", padding: "6px 14px", borderRadius: 99, fontSize: 13, fontWeight: 600 }}>
            {user?.name ? `${user.name}님, 안녕하세요!` : "안녕하세요!"}
          </span>
          <h1 style={{ fontSize: isMobile ? 28 : 40, fontWeight: 800, letterSpacing: "-0.03em", lineHeight: 1.2, margin: "16px 0 14px", color: "white" }}>
            저희가 선별한<br />
            <span style={{ background: "linear-gradient(180deg, transparent 65%, rgba(255,255,255,0.25) 65%)", padding: "0 4px" }}>
              맞춤 정책
            </span>을<br />
            확인해보세요
          </h1>
          <div style={{ display: "flex", gap: 10, marginTop: 20, flexWrap: "wrap" }}>
            <button
              onClick={onPersonalRefresh}
              disabled={personalRefreshing || refreshingRec}
              style={{ padding: "14px 22px", background: "white", color: A7, border: 0, borderRadius: 12, fontSize: 14, fontWeight: 700, cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 8, boxShadow: "0 4px 12px rgba(0,0,0,0.12)", opacity: (personalRefreshing || refreshingRec) ? 0.7 : 1 }}
            >
              {personalRefreshing ? "분석 중..." : "맞춤 재추천 →"}
            </button>
            <button
              onClick={() => navigate("/policies")}
              style={{ padding: "14px 22px", background: "rgba(255,255,255,0.12)", color: "white", border: "1px solid rgba(255,255,255,0.25)", borderRadius: 12, fontSize: 14, fontWeight: 600, cursor: "pointer" }}
            >
              전체 둘러보기
            </button>
          </div>
          <div style={{ display: "flex", gap: isMobile ? 16 : 24, marginTop: 28, fontSize: 13 }}>
            <div>
              <div style={{ fontSize: isMobile ? 18 : 22, fontWeight: 800 }}>{totalPolicies > 0 ? totalPolicies.toLocaleString() : "—"}</div>
              <div style={{ opacity: 0.7, fontSize: 12, marginTop: 2 }}>전체 정책</div>
            </div>
            <div style={{ width: 1, background: "rgba(255,255,255,0.2)" }} />
            <div>
              <div style={{ fontSize: isMobile ? 18 : 22, fontWeight: 800 }}>{deadlineCount > 0 ? `${deadlineCount}건` : "—"}</div>
              <div style={{ opacity: 0.7, fontSize: 12, marginTop: 2 }}>이번 주 마감</div>
            </div>
            <div style={{ width: 1, background: "rgba(255,255,255,0.2)" }} />
            <div>
              <div style={{ fontSize: isMobile ? 18 : 22, fontWeight: 800 }}>무료</div>
              <div style={{ opacity: 0.7, fontSize: 12, marginTop: 2 }}>AI 추천 서비스</div>
            </div>
          </div>
        </div>

        {!isMobile && (
          <div style={{ position: "relative", height: 270 }}>
            {firstDeadlinePolicy && (
              <div style={{ position: "absolute", right: 30, top: 8, width: 190, background: "white", borderRadius: 14, padding: 16, color: INK, boxShadow: "0 12px 32px rgba(0,0,0,0.18)", transform: "rotate(-3deg)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Tag style={{ background: "#fef2f2", color: WARN }}>마감임박</Tag>
                  <span style={{ fontSize: 18, fontWeight: 800, color: WARN }}>{firstDeadlinePolicy.dday}</span>
                </div>
                <div style={{ fontSize: 13, fontWeight: 700, marginTop: 10, lineHeight: 1.3, letterSpacing: "-0.01em" }}>
                  {firstDeadlinePolicy.title}
                </div>
                {firstDeadlinePolicy.source && (
                  <div style={{ fontSize: 11, color: INK3, marginTop: 5 }}>{firstDeadlinePolicy.source}</div>
                )}
              </div>
            )}
            {secondDeadlinePolicy && (
              <div style={{ position: "absolute", right: 0, top: 140, width: 200, background: "white", borderRadius: 14, padding: 16, color: INK, boxShadow: "0 12px 32px rgba(0,0,0,0.22)", transform: "rotate(2.5deg)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Tag style={{ background: AS, color: AI }}>{secondDeadlinePolicy.category}</Tag>
                  <span style={{ fontSize: 14, fontWeight: 800, color: WARN }}>{secondDeadlinePolicy.dday}</span>
                </div>
                <div style={{ fontSize: 13, fontWeight: 700, marginTop: 10, letterSpacing: "-0.01em", lineHeight: 1.3 }}>
                  {secondDeadlinePolicy.title}
                </div>
                {secondDeadlinePolicy.source && (
                  <div style={{ fontSize: 11, color: INK3, marginTop: 4 }}>{secondDeadlinePolicy.source}</div>
                )}
                <div style={{ marginTop: 8, paddingTop: 8, borderTop: "1px solid #f3f4f6", display: "flex", alignItems: "center", gap: 4 }}>
                  <span style={{ width: 6, height: 6, borderRadius: "50%", background: "#22c55e", flexShrink: 0 }} />
                  <span style={{ fontSize: 10, color: INK3, fontWeight: 600 }}>실시간 업데이트</span>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function CategoryBar({ counts, navigate }) {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  return (
    <section style={{ marginTop: 32, display: "grid", gridTemplateColumns: isMobile ? "repeat(3, 1fr)" : "repeat(6, 1fr)", gap: 12 }}>
      {CATEGORY_META.map((c) => (
        <div
          key={c.value}
          onClick={() => navigate(`/policies?category=${encodeURIComponent(c.value)}`)}
          style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 16, padding: isMobile ? "14px 8px" : "20px 16px", textAlign: "center", cursor: "pointer", transition: "transform .15s, box-shadow .15s" }}
          onMouseEnter={e => { e.currentTarget.style.transform = "translateY(-3px)"; e.currentTarget.style.boxShadow = "0 8px 24px rgba(37,99,235,0.12)"; }}
          onMouseLeave={e => { e.currentTarget.style.transform = ""; e.currentTarget.style.boxShadow = ""; }}
        >
          <div style={{ width: isMobile ? 40 : 52, height: isMobile ? 40 : 52, borderRadius: 14, background: c.bg, margin: "0 auto 8px", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <CategoryIcon name={c.icon} size={isMobile ? 22 : 28} color={c.fg} />
          </div>
          <div style={{ fontSize: isMobile ? 12 : 13, fontWeight: 700, color: INK }}>{c.label}</div>
          {counts[c.value] > 0 && (
            <div style={{ fontSize: 11, color: INK3, marginTop: 2 }}>{counts[c.value].toLocaleString()}개</div>
          )}
        </div>
      ))}
    </section>
  );
}

function DeadlineRail({ policies, navigate, onPolicyNavigate }) {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  if (!policies.length) return null;
  return (
    <section style={{ marginTop: 48 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 16 }}>
        <div>
          <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>마감임박 정책</div>
          <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>곧 신청이 마감되는 정책이에요</div>
        </div>
        <button
          onClick={() => navigate("/policies?sort=deadline&statusFilter=신청가능")}
          style={{ background: "none", border: "none", fontSize: 13, color: INK3, cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }}
          onMouseEnter={e => { e.currentTarget.style.color = A; }}
          onMouseLeave={e => { e.currentTarget.style.color = INK3; }}
        >
          전체 보기 →
        </button>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: isMobile ? `repeat(${Math.min(2, policies.length)}, 1fr)` : `repeat(${policies.length}, 1fr)`, gap: 14 }}>
        {policies.map((p) => (
          <div
            key={p.id}
            onClick={() => onPolicyNavigate(p.id)}
            style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 16, padding: 18, cursor: "pointer", transition: "box-shadow .15s" }}
            onMouseEnter={e => { e.currentTarget.style.boxShadow = "0 4px 16px rgba(37,99,235,0.10)"; }}
            onMouseLeave={e => { e.currentTarget.style.boxShadow = ""; }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <Tag style={{ background: "#fef2f2", color: WARN }}>{p.dday}</Tag>
            </div>
            <div style={{ fontSize: 14, fontWeight: 700, marginTop: 12, lineHeight: 1.4, letterSpacing: "-0.01em", color: INK }}>{p.title}</div>
            {p.source && <div style={{ fontSize: 12, color: INK3, marginTop: 4 }}>{p.source}</div>}
          </div>
        ))}
      </div>
    </section>
  );
}

function RecentViewedRail({ policies, navigate, onPolicyNavigate }) {
  if (!policies.length) return null;
  return (
    <section style={{ marginTop: 48 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 16 }}>
        <div>
          <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>최근 본 정책</div>
          <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>다시 확인하고 싶은 정책을 빠르게 이어서 볼 수 있어요</div>
        </div>
        <button
          onClick={() => navigate("/mypage?tab=2")}
          style={{ background: "none", border: "none", fontSize: 13, color: INK3, cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }}
          onMouseEnter={e => { e.currentTarget.style.color = A; }}
          onMouseLeave={e => { e.currentTarget.style.color = INK3; }}
        >
          마이페이지에서 보기 →
        </button>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: `repeat(${policies.length}, 1fr)`, gap: 14 }}>
        {policies.map((policy) => (
          <div
            key={policy.id}
            onClick={() => onPolicyNavigate(policy.id)}
            style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 16, padding: 18, cursor: "pointer", transition: "box-shadow .15s" }}
            onMouseEnter={e => { e.currentTarget.style.boxShadow = "0 4px 16px rgba(37,99,235,0.10)"; }}
            onMouseLeave={e => { e.currentTarget.style.boxShadow = ""; }}
          >
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              <Tag style={{ background: AS, color: AI }}>{policy.category}</Tag>
              <Tag style={policy.dday === "종료" ? { background: "#f3f4f6", color: INK3 } : { background: "#ecfdf5", color: OK }}>
                {policy.dday}
              </Tag>
            </div>
            <div style={{ fontSize: 14, fontWeight: 700, marginTop: 12, lineHeight: 1.4, letterSpacing: "-0.01em", color: INK }}>{policy.title}</div>
            {policy.source && <div style={{ fontSize: 12, color: INK3, marginTop: 4 }}>{policy.source}</div>}
          </div>
        ))}
      </div>
    </section>
  );
}

function PopularSection({ teaserPolicies, categoryCounts, navigate, onPolicyNavigate }) {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  return (
    <section style={{ marginTop: 56 }}>
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>지금 청년들이 많이 보는 정책</div>
        <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>조회수 기준 인기 정책</div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "repeat(2, minmax(0, 1fr))", gap: 28 }}>
        {POPULAR_CATEGORIES.map((catValue) => {
          const items = teaserPolicies[catValue] ?? [];
          if (!items.length) return null;
          return (
            <div key={catValue} style={{ minWidth: 0 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 14 }}>
                <div>
                  <div style={{ fontSize: 17, fontWeight: 800, letterSpacing: "-0.01em", color: INK }}>
                    {catValue}
                  </div>
                  {categoryCounts[catValue] > 0 && (
                    <div style={{ fontSize: 12, color: INK3, marginTop: 2 }}>{categoryCounts[catValue].toLocaleString()}개 정책</div>
                  )}
                </div>
                <button
                  onClick={() => navigate(`/policies?category=${encodeURIComponent(catValue)}`)}
                  style={{ background: "none", border: "none", fontSize: 13, color: INK3, cursor: "pointer" }}
                  onMouseEnter={e => { e.currentTarget.style.color = A; }}
                  onMouseLeave={e => { e.currentTarget.style.color = INK3; }}
                >
                  더보기 →
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {items.map((p, i) => (
                  <div
                    key={p.id}
                    onClick={() => onPolicyNavigate(p.id)}
                    style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 12, padding: "14px 16px", display: "flex", alignItems: "center", gap: 14, cursor: "pointer", transition: "border-color .15s" }}
                    onMouseEnter={e => { e.currentTarget.style.borderColor = A; }}
                    onMouseLeave={e => { e.currentTarget.style.borderColor = LINE; }}
                  >
                    <div style={{ width: 38, height: 38, borderRadius: 10, background: AS, color: A7, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 13, flexShrink: 0 }}>
                      {String(i + 1).padStart(2, "0")}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 14, fontWeight: 700, letterSpacing: "-0.01em", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", color: INK }}>
                        {p.title}
                      </div>
                      <div style={{ fontSize: 12, color: INK3, marginTop: 3, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {p.summary?.slice(0, 60)}
                      </div>
                    </div>
                    <span style={{ color: INK3, fontSize: 14 }}>›</span>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function RecCard({ rec, onPolicyNavigate, onBookmarkToggle }) {
  const recommendationMemo = resolveRecommendationMemo(rec);
  const isUrgent = rec.dday.startsWith("D-") && parseInt(rec.dday.replace("D-", "")) <= 14;
  const ddayStyle =
    rec.dday === "종료" ? { background: "#f3f4f6", color: INK3 }
    : rec.dday === "상시" || rec.dday === "진행중" ? { background: OK_BG, color: OK }
    : isUrgent ? { background: "#fef2f2", color: WARN }
    : { background: AS2, color: A };

  return (
    <div
      onClick={() => onPolicyNavigate(rec.id, rec.logId)}
      style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 14, padding: "18px 20px", cursor: "pointer", transition: "border-color .15s, box-shadow .15s" }}
      onMouseEnter={e => { e.currentTarget.style.borderColor = A; e.currentTarget.style.boxShadow = "0 4px 16px rgba(37,99,235,0.08)"; }}
      onMouseLeave={e => { e.currentTarget.style.borderColor = LINE; e.currentTarget.style.boxShadow = ""; }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12, marginBottom: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <Tag style={{ background: AS, color: AI }}>{rec.category}</Tag>
          <Tag style={ddayStyle}>{rec.dday}</Tag>
        </div>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onBookmarkToggle(rec.id);
          }}
          style={{
            background: rec.bookmarked ? "#fef3c7" : "white",
            color: rec.bookmarked ? "#92400e" : INK3,
            border: `1px solid ${rec.bookmarked ? "#fcd34d" : LINE}`,
            borderRadius: 999,
            padding: "6px 10px",
            fontSize: 12,
            fontWeight: 700,
            cursor: "pointer",
            flexShrink: 0,
          }}
        >
          {rec.bookmarked ? "★ 저장됨" : "☆ 저장"}
        </button>
      </div>
      <div style={{ fontSize: 16, fontWeight: 700, letterSpacing: "-0.01em", lineHeight: 1.35, color: INK }}>{rec.title}</div>
      <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.55, overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical" }}>
        {rec.summary}
      </div>
      <div style={{ display: "flex", gap: 10, marginTop: 6, fontSize: 12, color: INK3, flexWrap: "wrap" }}>
        {rec.sourceTypeLabel && <span>출처 {rec.sourceTypeLabel}</span>}
        {rec.source && <span>{rec.source}</span>}
      </div>
      {recommendationMemo && (
        <div style={{
          marginTop: 10,
          padding: "10px 12px",
          borderRadius: 12,
          background: "#f8fbff",
          border: "1px solid #dbeafe",
        }}>
          <div style={{ fontSize: 11, fontWeight: 800, color: AI, marginBottom: 4 }}>{recommendationMemo.heading}</div>
          <div style={{ fontSize: 12, lineHeight: 1.55, color: recommendationMemo.accentColor }}>
            {recommendationMemo.body}
          </div>
        </div>
      )}
    </div>
  );
}

function CTASection({ navigate, teaserPolicies, onPolicyNavigate, authState }) {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  const previewItems = ["일자리", "금융·생활지원", "주거"]
    .map(cat => {
      const policy = (teaserPolicies ?? {})[cat]?.[0];
      return policy ? { id: policy.id, title: policy.title, category: cat } : null;
    })
    .filter(Boolean);

  return (
    <section style={{ marginTop: 56, background: "white", border: `1px solid ${LINE}`, borderRadius: 24, padding: isMobile ? "28px 20px" : 36, display: "grid", gridTemplateColumns: (!isMobile && previewItems.length) ? "1.5fr 1fr" : "1fr", gap: 32, alignItems: "center" }}>
      <div>
        <Tag style={{ background: AS, color: AI }}>베타 서비스</Tag>
        <div style={{ fontSize: 26, fontWeight: 800, letterSpacing: "-0.02em", marginTop: 14, lineHeight: 1.3, color: INK }}>
          내 상황을 입력하면<br />정책을 추천해드려요
        </div>
        <div style={{ marginTop: 10, fontSize: 14, color: INK2, lineHeight: 1.6 }}>
          지역, 나이, 소득, 관심사를 알려주시면 자격이 맞는 정책 위주로 골라드립니다.
        </div>
        <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
          <button
            onClick={() => navigate("/signup", { state: authState })}
            style={{ padding: "12px 18px", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
          >
            1분만에 추천받기 →
          </button>
          <button
            onClick={() => navigate("/login", { state: authState })}
            style={{ padding: "12px 18px", background: "white", color: INK2, border: `1px solid ${LINE}`, borderRadius: 10, fontSize: 14, fontWeight: 600, cursor: "pointer" }}
          >
            로그인
          </button>
        </div>
      </div>
      {!isMobile && previewItems.length > 0 && (
        <div style={{ background: AS2, borderRadius: 16, padding: 22 }}>
          <div style={{ fontSize: 11, color: INK3, fontWeight: 700, marginBottom: 10 }}>인기 정책</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {previewItems.map(item => (
              <div
                key={item.id}
                onClick={() => onPolicyNavigate(item.id)}
                style={{ background: "white", borderRadius: 10, padding: "10px 12px", display: "flex", alignItems: "center", gap: 10, fontSize: 13, cursor: "pointer" }}
              >
                <span style={{ fontWeight: 600, color: INK, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{item.title}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function StandardCodePromptBanner({ missingCount, filledCount, totalCount, navigate }) {
  if (!missingCount) return null;
  return (
    <section style={{
      marginTop: 24,
      background: "linear-gradient(135deg, #eff6ff 0%, #ffffff 100%)",
      border: `1px solid ${LINE}`,
      borderRadius: 20,
      padding: "20px 22px",
      display: "flex",
      justifyContent: "space-between",
      gap: 16,
      alignItems: "center",
      flexWrap: "wrap",
    }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 800, color: A7, letterSpacing: "0.04em", textTransform: "uppercase" }}>
          추천 정확도 보강
        </div>
        <div style={{ fontSize: 18, fontWeight: 800, color: INK, marginTop: 6, letterSpacing: "-0.02em" }}>
          선택 프로필 {filledCount}/{totalCount}개 입력됨
        </div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
          선택 프로필 {missingCount}개를 더 보완하면 주거·복지 자격조건 매칭과 맞춤 추천이 더 정확해집니다.
        </div>
      </div>
      <button
        onClick={() => navigate("/mypage?tab=0")}
        style={{
          padding: "13px 18px",
          borderRadius: 12,
          border: "none",
          background: A,
          color: "white",
          fontSize: 14,
          fontWeight: 700,
          cursor: "pointer",
          whiteSpace: "nowrap",
          boxShadow: "0 8px 24px rgba(37,99,235,0.18)",
        }}
      >
        마이페이지에서 채우기 →
      </button>
    </section>
  );
}

function RecommendationRefreshStandardCodePrompt({
  mode,
  missingCount,
  filledCount,
  totalCount,
  onComplete,
  onContinue,
  onDismiss,
}) {
  if (!mode || missingCount < 3) return null;

  const actionLabel = mode === "personal" ? "맞춤 재추천" : "새로고침";

  return (
    <section style={{
      marginTop: 16,
      background: "white",
      border: `1px solid ${LINE}`,
      borderRadius: 18,
      padding: "18px 20px",
      display: "flex",
      justifyContent: "space-between",
      gap: 16,
      alignItems: "center",
      flexWrap: "wrap",
      boxShadow: "0 10px 30px rgba(15,23,42,0.06)",
    }}>
      <div style={{ minWidth: 240, flex: "1 1 420px" }}>
        <div style={{ fontSize: 12, fontWeight: 800, color: WARN, letterSpacing: "0.04em" }}>
          추천 전 확인
        </div>
        <div style={{ fontSize: 17, fontWeight: 800, color: INK, marginTop: 6, letterSpacing: "-0.01em" }}>
          선택 프로필 {filledCount}/{totalCount}개 입력 상태입니다
        </div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
          선택 프로필 {missingCount}개를 {actionLabel} 전에 보완하면 주거·복지 조건 매칭이 더 안정됩니다.
        </div>
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
        <button
          onClick={onComplete}
          style={{
            padding: "12px 15px",
            borderRadius: 12,
            border: "none",
            background: A,
            color: "white",
            fontSize: 13,
            fontWeight: 800,
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          선택 정보 보완하기 →
        </button>
        <button
          onClick={onContinue}
          style={{
            padding: "12px 14px",
            borderRadius: 12,
            border: `1px solid ${LINE}`,
            background: "white",
            color: INK2,
            fontSize: 13,
            fontWeight: 700,
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          그래도 {actionLabel}
        </button>
        <button
          onClick={onDismiss}
          aria-label="추천 전 확인 닫기"
          style={{
            width: 42,
            height: 42,
            borderRadius: 12,
            border: `1px solid ${LINE}`,
            background: "#f8fafc",
            color: INK3,
            fontSize: 18,
            fontWeight: 800,
            cursor: "pointer",
            lineHeight: 1,
          }}
        >
          ×
        </button>
      </div>
    </section>
  );
}

function PriorityPromptBanner({ navigate }) {
  return (
    <section style={{
      marginTop: 16,
      marginBottom: 16,
      background: "linear-gradient(135deg, #fff7ed 0%, #ffffff 100%)",
      border: `1px solid ${LINE}`,
      borderRadius: 18,
      padding: "18px 20px",
      display: "flex",
      justifyContent: "space-between",
      gap: 16,
      alignItems: "center",
      flexWrap: "wrap",
    }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 800, color: "#c2410c", letterSpacing: "0.04em", textTransform: "uppercase" }}>
          추천 우선순위 필요
        </div>
        <div style={{ fontSize: 17, fontWeight: 800, color: INK, marginTop: 6, letterSpacing: "-0.02em" }}>
          아직 추천 우선순위를 설정하지 않았어요
        </div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
          최신 추천 품질 점검 기준으로도 우선순위가 비어 있으면 특정 지자체 정책이 상단에 반복되는 경향이 더 컸습니다.
          {" "}주거, 일자리, 교육처럼 먼저 보고 싶은 축을 정해두면 재추천 결과가 더 안정적입니다.
        </div>
      </div>
      <button
        onClick={() => navigate("/mypage?tab=1")}
        style={{
          padding: "12px 16px",
          borderRadius: 12,
          border: "none",
          background: "#c2410c",
          color: "white",
          fontSize: 14,
          fontWeight: 700,
          cursor: "pointer",
          whiteSpace: "nowrap",
          boxShadow: "0 8px 24px rgba(194,65,12,0.14)",
        }}
      >
        우선순위 먼저 설정 →
      </button>
    </section>
  );
}

function GuidePromptBanner({ source, navigate, onDismiss }) {
  return (
    <section style={{
      marginTop: 16,
      background: "linear-gradient(135deg, #eef2ff 0%, #ffffff 100%)",
      border: `1px solid ${LINE}`,
      borderRadius: 18,
      padding: "18px 20px",
      display: "flex",
      justifyContent: "space-between",
      gap: 16,
      alignItems: "center",
      flexWrap: "wrap",
    }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 800, color: AI, letterSpacing: "0.04em", textTransform: "uppercase" }}>
          처음 시작 가이드
        </div>
        <div style={{ fontSize: 17, fontWeight: 800, color: INK, marginTop: 6, letterSpacing: "-0.02em" }}>
          {source === "signup" ? "가입 직후라면 이용가이드부터 보는 편이 빠릅니다" : "처음 로그인했다면 이용가이드부터 보는 편이 빠릅니다"}
        </div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
          추천, 정책 검색, 챗봇, 북마크, 알림을 어떤 순서로 쓰면 좋은지 한 페이지로 정리해뒀습니다.
        </div>
      </div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        <button
          onClick={() => navigate("/guide")}
          style={{
            padding: "12px 16px",
            borderRadius: 12,
            border: "none",
            background: A,
            color: "white",
            fontSize: 14,
            fontWeight: 700,
            cursor: "pointer",
            whiteSpace: "nowrap",
            boxShadow: "0 8px 24px rgba(37,99,235,0.16)",
          }}
        >
          이용가이드 보기 →
        </button>
        <button
          onClick={onDismiss}
          style={{
            padding: "12px 14px",
            borderRadius: 12,
            border: `1px solid ${LINE}`,
            background: "white",
            color: INK2,
            fontSize: 14,
            fontWeight: 700,
            cursor: "pointer",
            whiteSpace: "nowrap",
          }}
        >
          나중에 볼게요
        </button>
      </div>
    </section>
  );
}

function Spinner() {
  return (
    <div style={{ display: "flex", justifyContent: "center", padding: "48px 0" }}>
      <div style={{ width: 32, height: 32, border: `3px solid ${AS}`, borderTopColor: A, borderRadius: "50%", animation: "spin 0.8s linear infinite" }} />
    </div>
  );
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────────

export default function MainPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isLoggedIn, user, setUser } = useAuthStore();
  const isMobile = useMediaQuery("(max-width: 1199px)");

  // AI 추천 (로그인)
  const [recommendations, setRecommendations] = useState([]);
  const [recVisibleCount, setRecVisibleCount] = useState(9);
  const [loadingRec, setLoadingRec] = useState(false);
  const [recError, setRecError] = useState(false);
  const [refreshingRec, setRefreshingRec] = useState(false);
  const [personalRefreshing, setPersonalRefreshing] = useState(false);
  const [standardCodeRefreshPrompt, setStandardCodeRefreshPrompt] = useState(null);

  // 공통 데이터
  const [totalPolicies, setTotalPolicies] = useState(0);
  const [categoryCounts, setCategoryCounts] = useState({});
  const [teaserPolicies, setTeaserPolicies] = useState({});
  const [deadlinePolicies, setDeadlinePolicies] = useState([]);
  const [recentViewedPolicies, setRecentViewedPolicies] = useState([]);
  const [guideNudge, setGuideNudge] = useState(null);

  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });
  const showToast = useCallback((msg, severity = "info") => setToast({ open: true, msg, severity }), []);
  const returnLocation = buildSafeReturnLocation(location);
  const authState = {
    from: returnLocation,
  };
  const standardCodeMissingCount = user?.standardCodeMissingCount ?? 0;
  const standardCodeTotalCount = user?.standardCodeTotalCount ?? 4;
  const standardCodeFilledCount = user?.standardCodeFilledCount ?? Math.max(0, standardCodeTotalCount - standardCodeMissingCount);
  const shouldGateRecommendationRefresh = standardCodeMissingCount >= 3;

  useEffect(() => {
    if (location.state?.reason === "admin-required") {
      showToast("운영 대시보드는 관리자 계정만 접근할 수 있습니다.", "warning");
    }
  }, [location.state, showToast]);

  useEffect(() => {
    const nudge = location.state?.postLoginRecommendationNudge;
    if (!nudge) {
      return;
    }

    if (nudge.kind === "both") {
      showToast(`로그인되었습니다. 우선순위와 선택 프로필 ${nudge.filledCount}/${nudge.totalCount ?? 4} 입력 상태를 함께 보완하면 추천 품질이 더 빨리 안정됩니다.`, "info");
    } else if (nudge.kind === "priorities") {
      showToast("로그인되었습니다. 다음으로 우선순위를 설정하면 추천 품질이 더 안정됩니다.", "info");
    } else if (nudge.kind === "standardCodes") {
      showToast(`로그인되었습니다. 선택 프로필 ${nudge.filledCount}/${nudge.totalCount ?? 4} 입력 상태입니다. 필요한 정보를 더 보완하면 추천 정확도가 올라갑니다.`, "info");
    }

    const nextState = { ...(sanitizeTransientRouteState(location.state) ?? {}) };
    delete nextState.postLoginRecommendationNudge;
    navigate(`${location.pathname}${location.search}`, {
      replace: true,
      state: Object.keys(nextState).length ? nextState : undefined,
    });
  }, [location.pathname, location.search, location.state, navigate, showToast]);

  useEffect(() => {
    const nudge = location.state?.postLoginGuideNudge;
    if (!nudge) {
      return;
    }

    if (nudge.source === "signup") {
      showToast("가입이 끝났다면 이용가이드부터 보면 더 빨리 적응할 수 있습니다.", "info");
    } else {
      showToast("처음이라면 이용가이드를 먼저 보고 추천과 검색을 시작해보세요.", "info");
    }
    setGuideNudge(nudge);

    if (typeof window !== "undefined") {
      window.localStorage.setItem(GUIDE_NUDGE_STORAGE_KEY, new Date().toISOString());
    }

    const nextState = { ...(sanitizeTransientRouteState(location.state) ?? {}) };
    delete nextState.postLoginGuideNudge;
    navigate(`${location.pathname}${location.search}`, {
      replace: true,
      state: Object.keys(nextState).length ? nextState : undefined,
    });
  }, [location.pathname, location.search, location.state, navigate, showToast]);

  const navigateToPolicyDetail = useCallback((policyId, logId = null) => {
    navigate(`/policies/${policyId}`, {
      state: {
        from: returnLocation,
        ...(logId ? { recommendationLogId: logId } : {}),
      },
    });
  }, [navigate, returnLocation]);

  // ── AI 추천 fetch ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!isLoggedIn) return;
    const controller = new AbortController();
    api.get("/api/users/me", { signal: controller.signal })
      .then(({ data }) => {
        const profile = data?.data;
        if (!profile) {
          return;
        }
        const standardCodeCompletion = resolveStandardProfileCodeCompletion(profile);
        setUser({
          ...(profile.name ? { name: profile.name } : {}),
          ...(profile.email ? { email: profile.email } : {}),
          hasPriorities: Array.isArray(profile.priorities) && profile.priorities.length > 0,
          standardCodeFilledCount: standardCodeCompletion.filledCount,
          standardCodeMissingCount: standardCodeCompletion.missingCount,
          standardCodeTotalCount: standardCodeCompletion.totalCount,
        });
      })
      .catch((err) => {
        if (err.name === "CanceledError" || err.code === "ERR_CANCELED") {
          return;
        }
      });
    setLoadingRec(true);
    setRecError(false);
    api.get("/api/recommendations", { params: { size: 30 }, signal: controller.signal })
      .then(({ data }) => setRecommendations((data.data ?? []).map(mapRec)))
      .catch((err) => {
        if (err.name !== "CanceledError" && err.code !== "ERR_CANCELED") {
          setRecError(true);
          showToast("추천 정책을 불러오지 못했습니다", "error");
        }
      })
      .finally(() => { if (!controller.signal.aborted) setLoadingRec(false); });
    return () => controller.abort();
  }, [isLoggedIn, setUser, showToast]);

  useEffect(() => {
    if (!isLoggedIn) {
      setRecentViewedPolicies([]);
      return;
    }

    const controller = new AbortController();
    api.get("/api/users/me/recent-viewed-policies", {
      params: { limit: 4 },
      signal: controller.signal,
    })
      .then(({ data }) => setRecentViewedPolicies((data.data ?? []).map(mapRecentViewedPolicy)))
      .catch((err) => {
        if (err.name === "CanceledError" || err.code === "ERR_CANCELED") {
          return;
        }
        setRecentViewedPolicies([]);
      });

    return () => controller.abort();
  }, [isLoggedIn]);

  // ── 공통 데이터 fetch (전체 수, 카테고리별, 마감임박) ─────────────────────
  useEffect(() => {
    const controller = new AbortController();
    const sig = { signal: controller.signal };

    Promise.all([
      api.get("/api/policies", { params: { size: 1, statusFilter: "ACTIVE_ONLY" }, ...sig }),
      ...CATEGORY_META.map((c) =>
        api.get("/api/policies", { params: { category: c.value, sort: "VIEWS", statusFilter: "ACTIVE_ONLY", size: 3, page: 0 }, ...sig })
      ),
      api.get("/api/policies", { params: { sort: "DEADLINE", statusFilter: "ACTIVE_ONLY", size: 4 }, ...sig }),
    ])
      .then(([totalRes, ...rest]) => {
        const catResults = rest.slice(0, CATEGORY_META.length);
        const deadlineRes = rest[CATEGORY_META.length];

        setTotalPolicies(totalRes.data?.data?.totalElements ?? 0);

        const counts = {};
        const teaser = {};
        CATEGORY_META.forEach((c, i) => {
          const pd = catResults[i].data?.data ?? {};
          counts[c.value] = pd.totalElements ?? 0;
          teaser[c.value] = (pd.content ?? []).map((p) => ({
            id: p.id,
            title: p.title,
            summary: p.description || "",
          }));
        });
        setCategoryCounts(counts);
        setTeaserPolicies(teaser);

        const dl = (deadlineRes.data?.data?.content ?? [])
          .map((p) => ({
            id: p.id,
            title: p.title,
            dday: formatDday(p.applyEndDate, p.status),
            source: p.hostOrg || p.sido || p.operatingOrg || "",
            category: p.unifiedCategory || "기타",
          }))
          .filter((p) => p.dday !== "종료" && p.dday !== "상시" && p.dday !== "예정");
        setDeadlinePolicies(dl.slice(0, 4));
      })
      .catch(() => {});

    return () => controller.abort();
  }, []);

  // ── 추천 갱신 핸들러 ──────────────────────────────────────────────────────
  const redirectToPrioritySetup = useCallback(() => {
    showToast("마이페이지 우선순위 탭으로 이동합니다.", "info");
    navigate("/mypage?tab=1");
  }, [navigate, showToast]);

  const redirectToProfileInfo = useCallback(() => {
    showToast("마이페이지 선택 프로필 입력 영역으로 이동합니다.", "info");
    navigate("/mypage?tab=0");
  }, [navigate, showToast]);

  const handleRefreshRecommendations = async ({ bypassStandardCodeGate = false } = {}) => {
    if (!user?.hasPriorities) {
      redirectToPrioritySetup();
      return;
    }
    if (shouldGateRecommendationRefresh && !bypassStandardCodeGate) {
      setStandardCodeRefreshPrompt("refresh");
      showToast("새로고침 전에 선택 프로필 입력 상태를 확인해주세요.", "info");
      return;
    }
    if (standardCodeMissingCount > 0) {
      showToast(`선택 프로필 ${standardCodeFilledCount}/${standardCodeTotalCount} 입력 상태로 추천을 새로 불러옵니다. 필요한 정보를 더 보완하면 추천이 더 정확해집니다.`, "info");
    }
    setRefreshingRec(true);
    try {
      const { data } = await api.post("/api/recommendations/refresh");
      setRecommendations((data.data ?? []).map(mapRec));
      setRecError(false);
      if (standardCodeMissingCount > 0) {
        showToast("추천을 새로 불러왔습니다. 선택 프로필을 보완하면 개인화 정확도가 더 올라갑니다.", "success");
      } else {
        showToast("추천을 새로 불러왔습니다", "success");
      }
    } catch { showToast("추천 갱신에 실패했습니다", "error"); }
    finally { setRefreshingRec(false); }
  };

  const handlePersonalRefresh = async ({ bypassStandardCodeGate = false } = {}) => {
    if (!user?.hasPriorities) {
      redirectToPrioritySetup();
      return;
    }
    if (shouldGateRecommendationRefresh && !bypassStandardCodeGate) {
      setStandardCodeRefreshPrompt("personal");
      showToast("맞춤 재추천 전에 선택 프로필 입력 상태를 확인해주세요.", "info");
      return;
    }
    if (standardCodeMissingCount > 0) {
      showToast(`선택 프로필 ${standardCodeFilledCount}/${standardCodeTotalCount} 입력 상태로 개인 맞춤 재추천을 진행합니다. 필요한 정보를 더 보완하면 추천이 더 정교해집니다.`, "info");
    }
    setPersonalRefreshing(true);
    try {
      const { data } = await api.post("/api/recommendations/refresh", null, { params: { personal: true } });
      setRecommendations((data.data ?? []).map(mapRec));
      setRecError(false);
      if (standardCodeMissingCount > 0) {
        showToast("개인 맞춤 추천을 새로 받았습니다. 선택 프로필을 보완하면 더 안정적인 결과를 볼 수 있습니다.", "success");
      } else {
        showToast("개인 맞춤 추천을 새로 받았습니다", "success");
      }
    } catch { showToast("재추천에 실패했습니다", "error"); }
    finally { setPersonalRefreshing(false); }
  };

  const handleRecommendationBookmarkToggle = async (serviceId) => {
    try {
      await api.post(`/api/policies/${serviceId}/bookmark`);
      const current = recommendations.find((rec) => rec.id === serviceId);
      const nextBookmarked = !current?.bookmarked;
      setRecommendations((prev) =>
        prev.map((rec) => (rec.id === serviceId ? { ...rec, bookmarked: nextBookmarked } : rec))
      );
      showToast(nextBookmarked ? "북마크에 저장했어요" : "북마크를 해제했어요", "success");
    } catch {
      showToast("북마크 처리에 실패했습니다", "error");
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: "#f7f8fc" }}>
      <Header />

      <div style={{ maxWidth: 1240, margin: "0 auto", padding: isMobile ? "0 16px 80px" : "0 24px 80px" }}>

        {/* 히어로 */}
        {isLoggedIn ? (
          <HeroLoggedIn
            user={user}
            navigate={navigate}
            onPersonalRefresh={() => handlePersonalRefresh()}
            refreshingRec={refreshingRec}
            personalRefreshing={personalRefreshing}
            totalPolicies={totalPolicies}
            deadlineCount={deadlinePolicies.length}
            firstDeadlinePolicy={deadlinePolicies[0]}
            secondDeadlinePolicy={deadlinePolicies[1]}
          />
        ) : (
          <HeroNonLogin
              totalPolicies={totalPolicies}
              deadlineCount={deadlinePolicies.length}
              firstDeadlinePolicy={deadlinePolicies[0]}
              secondDeadlinePolicy={deadlinePolicies[1]}
              navigate={navigate}
              authState={authState}
            />
        )}

        {isLoggedIn && (
          <StandardCodePromptBanner
            missingCount={standardCodeMissingCount}
            filledCount={standardCodeFilledCount}
            totalCount={standardCodeTotalCount}
            navigate={navigate}
          />
        )}
        {isLoggedIn && (
          <RecommendationRefreshStandardCodePrompt
            mode={standardCodeRefreshPrompt}
            missingCount={standardCodeMissingCount}
            filledCount={standardCodeFilledCount}
            totalCount={standardCodeTotalCount}
            onComplete={redirectToProfileInfo}
            onContinue={() => {
              const mode = standardCodeRefreshPrompt;
              setStandardCodeRefreshPrompt(null);
              if (mode === "personal") {
                handlePersonalRefresh({ bypassStandardCodeGate: true });
                return;
              }
              handleRefreshRecommendations({ bypassStandardCodeGate: true });
            }}
            onDismiss={() => setStandardCodeRefreshPrompt(null)}
          />
        )}
        {isLoggedIn && guideNudge && (
          <GuidePromptBanner
            source={guideNudge.source}
            navigate={navigate}
            onDismiss={() => setGuideNudge(null)}
          />
        )}
        {isLoggedIn && !user?.hasPriorities && (
          <PriorityPromptBanner navigate={navigate} />
        )}

        {/* AI 추천 (로그인) / 로그인 유도 (비로그인) */}
        <section style={{ marginTop: 48 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 16 }}>
            <div>
              <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>맞춤 추천 정책</div>
              <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>
                {isLoggedIn
                  ? (recommendations.length > 0 ? `저희가 프로필을 분석해 선별했어요 · 총 ${recommendations.length}건` : "저희가 프로필을 분석해 선별했어요")
                  : "로그인하면 나에게 맞는 정책을 추려드려요"}
              </div>
            </div>
            {isLoggedIn && (
              <button
                onClick={() => navigate("/policies")}
                style={{ background: "none", border: "none", fontSize: 13, color: INK3, cursor: "pointer" }}
                onMouseEnter={e => { e.currentTarget.style.color = A; }}
                onMouseLeave={e => { e.currentTarget.style.color = INK3; }}
              >
                모든 정책 보기 →
              </button>
            )}
          </div>

          {isLoggedIn ? (
            loadingRec ? (
              <Spinner />
            ) : recommendations.length > 0 ? (
              <>
                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  {recommendations.slice(0, recVisibleCount).map((rec) => (
                    <RecCard
                      key={rec.id}
                      rec={rec}
                      onPolicyNavigate={navigateToPolicyDetail}
                      onBookmarkToggle={handleRecommendationBookmarkToggle}
                    />
                  ))}
                </div>

                {recommendations.length > recVisibleCount && (
                  <div style={{ textAlign: "center", marginTop: 18 }}>
                    <button
                      onClick={() => setRecVisibleCount((n) => n + 9)}
                      style={{ padding: "12px 28px", background: "white", color: A7, border: `1px solid ${A}44`, borderRadius: 12, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
                      onMouseEnter={e => { e.currentTarget.style.background = AS; }}
                      onMouseLeave={e => { e.currentTarget.style.background = "white"; }}
                    >
                      더 보기 ({recommendations.length - recVisibleCount}건 더)
                    </button>
                  </div>
                )}
              </>
            ) : (
              <div style={{ textAlign: "center", padding: "48px 24px", background: "white", borderRadius: 16, border: `1px solid ${LINE}` }}>
                <div style={{ marginTop: 12, fontSize: 15, color: INK2 }}>
                  {recError
                    ? "추천 정책을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
                    : user?.hasPriorities
                      ? "아직 추천 정책이 없어요. 맞춤 재추천 버튼을 눌러보세요."
                      : "마이페이지에서 우선순위를 설정하면 맞춤 추천을 받을 수 있어요"}
                </div>
                {(recError || user?.hasPriorities) && !recError && (
                  <button
                    onClick={() => handlePersonalRefresh()}
                    disabled={personalRefreshing}
                    style={{ marginTop: 14, padding: "10px 20px", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer", opacity: personalRefreshing ? 0.7 : 1 }}
                  >
                    {personalRefreshing ? "분석 중..." : "맞춤 재추천 받기 →"}
                  </button>
                )}
                {!user?.hasPriorities && !recError && (
                  <button
                    onClick={() => navigate("/mypage?tab=1")}
                    style={{ marginTop: 14, padding: "10px 20px", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
                  >
                    우선순위 설정하러 가기
                  </button>
                )}
              </div>
            )
          ) : (
            <div style={{ background: "white", borderRadius: 20, border: `1px solid ${LINE}`, padding: isMobile ? "28px 20px" : "48px 40px", display: "flex", alignItems: isMobile ? "flex-start" : "center", justifyContent: "space-between", gap: 32, flexDirection: isMobile ? "column" : "row" }}>
              <div>
                <div style={{ fontSize: 28, fontWeight: 800, color: INK, letterSpacing: "-0.02em", lineHeight: 1.3 }}>
                  로그인하여<br />맞춤 정책 찾아보기
                </div>
                <div style={{ fontSize: 14, color: INK3, marginTop: 10, lineHeight: 1.6 }}>
                  나이·지역·소득 정보를 입력하면<br />저희가 준비한 자격되는 정책만 골라드려요.
                </div>
                <div style={{ display: "flex", gap: 10, marginTop: 24 }}>
                  <button
                    onClick={() => navigate("/login", { state: authState })}
                    style={{ padding: "13px 24px", background: A, color: "white", border: 0, borderRadius: 12, fontSize: 15, fontWeight: 700, cursor: "pointer", boxShadow: "0 4px 12px rgba(37,99,235,0.25)" }}
                    onMouseEnter={e => { e.currentTarget.style.background = A7; }}
                    onMouseLeave={e => { e.currentTarget.style.background = A; }}
                  >
                    로그인하기
                  </button>
                  <button
                    onClick={() => navigate("/signup", { state: authState })}
                    style={{ padding: "13px 24px", background: "white", color: INK2, border: `1px solid ${LINE}`, borderRadius: 12, fontSize: 15, fontWeight: 600, cursor: "pointer" }}
                    onMouseEnter={e => { e.currentTarget.style.borderColor = A; e.currentTarget.style.color = A; }}
                    onMouseLeave={e => { e.currentTarget.style.borderColor = LINE; e.currentTarget.style.color = INK2; }}
                  >
                    회원가입
                  </button>
                </div>
              </div>
              {!isMobile && (() => {
                const previewItems = ["일자리", "금융·생활지원", "주거"]
                  .map(cat => {
                    const meta = CATEGORY_META.find(c => c.value === cat);
                    const policy = teaserPolicies[cat]?.[0];
                    return policy ? { id: policy.id, title: policy.title, bg: meta?.bg, fg: meta?.fg, icon: meta?.icon, category: cat } : null;
                  })
                  .filter(Boolean);
                if (!previewItems.length) return null;
                return (
                  <div style={{ flexShrink: 0, display: "flex", flexDirection: "column", gap: 10 }}>
                    {previewItems.map(item => (
                      <div
                        key={item.id}
                        onClick={() => navigateToPolicyDetail(item.id)}
                        style={{ display: "flex", alignItems: "center", gap: 12, background: "#f7f8fc", borderRadius: 12, padding: "12px 16px", minWidth: 220, cursor: "pointer" }}
                      >
                        <div style={{ width: 36, height: 36, borderRadius: 10, background: item.bg, flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
                          <CategoryIcon name={item.icon} size={20} color={item.fg} />
                        </div>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 13, fontWeight: 700, color: INK, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: 160 }}>{item.title}</div>
                          <div style={{ fontSize: 12, color: A, fontWeight: 600, marginTop: 1 }}>{item.category}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })()}
            </div>
          )}
        </section>

        {/* 관심 분야로 찾기 (카테고리) — 맞춤 추천 아래로 이동 */}
        <div style={{ marginTop: 48, marginBottom: -16 }}>
          <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>관심 분야로 찾기</div>
          <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>분야를 선택해 전체 정책을 둘러보세요</div>
        </div>
        <CategoryBar counts={categoryCounts} navigate={navigate} />

        {isLoggedIn && (
          <RecentViewedRail
            policies={recentViewedPolicies}
            navigate={navigate}
            onPolicyNavigate={navigateToPolicyDetail}
          />
        )}

        {/* 마감임박 */}
        <DeadlineRail policies={deadlinePolicies} navigate={navigate} onPolicyNavigate={navigateToPolicyDetail} />

        {/* 카테고리별 인기 정책 */}
        <PopularSection teaserPolicies={teaserPolicies} categoryCounts={categoryCounts} navigate={navigate} onPolicyNavigate={navigateToPolicyDetail} />

        {/* CTA (비로그인) */}
        {!isLoggedIn && <CTASection navigate={navigate} teaserPolicies={teaserPolicies} onPolicyNavigate={navigateToPolicyDetail} authState={authState} />}

        {/* 푸터 */}
        <footer style={{ marginTop: isMobile ? 40 : 72, padding: "28px 0 0", borderTop: `1px solid ${LINE}`, color: INK3, fontSize: 12 }}>
          <div style={{ display: "flex", flexDirection: isMobile ? "column" : "row", justifyContent: "space-between", alignItems: isMobile ? "flex-start" : "center", gap: isMobile ? 12 : 0 }}>
            <div>
              <div style={{ fontSize: 14, color: INK2, fontWeight: 700 }}>청년복지플랫폼</div>
              <div style={{ marginTop: 6 }}>© 2026 청년복지플랫폼. 정책 데이터는 온통청년·복지로·정부24에서 제공받습니다.</div>
            </div>
            <div style={{ display: "flex", gap: 18 }}>
              <button type="button" onClick={() => navigate("/terms")} style={FOOTER_LINK_STYLE}>이용약관</button>
              <button type="button" onClick={() => navigate("/privacy")} style={FOOTER_LINK_STYLE}>개인정보처리방침</button>
              <button type="button" onClick={() => navigate("/support")} style={FOOTER_LINK_STYLE}>고객센터</button>
            </div>
          </div>
        </footer>
      </div>

      <FloatingNav />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>

      <Snackbar
        open={toast.open}
        autoHideDuration={3000}
        onClose={() => setToast((p) => ({ ...p, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((p) => ({ ...p, open: false }))}>
          {toast.msg}
        </Alert>
      </Snackbar>
    </div>
  );
}
