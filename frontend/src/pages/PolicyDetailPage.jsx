import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Snackbar, Alert } from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const A = "#2563eb";
const A7 = "#1d4ed8";
const AS = "#e8efff";
const AI = "#1e3a8a";
const WARN = "#ef4444";
const BG = "#f7f8fc";
const WHITE = "#fff";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const LINE = "#e5e7eb";
const LINE2 = "#f3f4f6";
const NO_DATA = "원문에서 확인해주세요.";

const HTML_ENTITIES = {
  "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'",
  "&nbsp;": " ", "&middot;": "·", "&bull;": "•", "&ndash;": "–",
  "&mdash;": "—", "&laquo;": "«", "&raquo;": "»", "&times;": "×",
};
const decodeHtml = (text) => {
  if (!text) return text;
  return text.replace(/&[a-zA-Z0-9#]+;/g, (e) => HTML_ENTITIES[e] ?? e);
};

const formatDate = (value) => {
  if (!value) return null;
  const d = new Date(`${value}T00:00:00`);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

const formatPeriod = (start, end) => {
  const s = formatDate(start), e = formatDate(end);
  if (s && e) return `${s} ~ ${e}`;
  if (s) return `${s} ~`;
  if (e) return `~ ${e}`;
  return null;
};

const formatAgeRange = (min, max) => {
  if (min && max) return `만 ${min}~${max}세`;
  if (min) return `만 ${min}세 이상`;
  if (max) return `만 ${max}세 이하`;
  return null;
};

const formatSource = (sourceType) => {
  if (sourceType === "YOUTH") return "온통청년";
  if (sourceType === "BOKJIRO_CENTRAL") return "복지로 중앙";
  if (sourceType === "BOKJIRO_LOCAL") return "복지로 지자체";
  if (sourceType === "GOV24") return "Gov24";
  return sourceType || "출처 정보 없음";
};

const formatStatusLabel = (status, applyEndDate) => {
  if (status === "CLOSED") return "종료";
  if (applyEndDate) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    if (new Date(`${applyEndDate}T00:00:00`) < today) return "종료";
  }
  if (status === "ACTIVE") return "진행중";
  if (status === "UPCOMING") return "예정";
  return "상태 정보 없음";
};

const formatDday = (endDate, status) => {
  if (status === "CLOSED") return "종료";
  if (!endDate) return status === "UPCOMING" ? "예정" : "상시/문의";
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const target = new Date(`${endDate}T00:00:00`);
  if (Number.isNaN(target.getTime())) return "상시/문의";
  const diff = Math.ceil((target - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
};

const parseContacts = (raw) => {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => ({
          name: item?.name || item?.deptNm || item?.orgNm || "",
          phone: item?.phone || item?.telNo || item?.contact || "",
        }))
        .filter((item) => item.name || item.phone);
    }
  } catch {
    return raw.split(/\n+/).map(l => l.trim()).filter(Boolean).map(l => ({ name: l, phone: "" }));
  }
  return [];
};

const parseReferenceUrls = (raw) => {
  if (!raw) return [];

  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];

    const seen = new Set();
    return parsed
      .map((item) => {
        const url = typeof item?.url === "string" ? item.url.trim() : "";
        if (!url) return null;
        const normalizedUrl = /^https?:\/\//i.test(url) ? url : `https://${url}`;
        return {
          url: normalizedUrl,
          displayUrl: url,
          type: item?.type || "REFERENCE",
          label: item?.label || "추가 링크",
          sourceField: item?.sourceField || "",
          confidence: item?.confidence ?? null,
        };
      })
      .filter((item) => {
        if (!item || seen.has(item.url)) return false;
        seen.add(item.url);
        return true;
      });
  } catch {
    return [];
  }
};

function Tag({ children, color, bg, border }) {
  return (
    <span style={{
      display: "inline-flex", alignItems: "center",
      padding: "3px 10px", borderRadius: 99,
      fontSize: 12, fontWeight: 600, lineHeight: 1.5,
      color: color ?? INK2,
      background: bg ?? LINE2,
      border: `1px solid ${border ?? "transparent"}`,
    }}>
      {children}
    </span>
  );
}

function ContentSection({ id, title, children }) {
  return (
    <section id={id} style={{ padding: "32px 0", borderBottom: `1px solid ${LINE}` }}>
      <h2 style={{ fontSize: 20, fontWeight: 800, letterSpacing: "-0.01em", margin: "0 0 14px", color: INK }}>
        {title}
      </h2>
      <div style={{ fontSize: 15, color: INK2, lineHeight: 1.75 }}>
        {children}
      </div>
    </section>
  );
}

function Spinner() {
  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: 400 }}>
      <div style={{
        width: 40, height: 40, borderRadius: "50%",
        border: `3px solid ${AS}`, borderTopColor: A,
        animation: "spin 0.8s linear infinite",
      }} />
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  );
}

export default function PolicyDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const { isLoggedIn } = useAuthStore();

  const [policy, setPolicy] = useState(null);
  const [bookmarked, setBookmarked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [related, setRelated] = useState([]);
  const [activeTab, setActiveTab] = useState("intro");
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  useEffect(() => {
    const controller = new AbortController();
    const fetchPolicy = async () => {
      setLoading(true);
      try {
        const logId = searchParams.get("log_id");
        const { data } = await api.get(`/api/policies/${id}`, {
          params: { logId: logId || undefined },
          signal: controller.signal,
        });
        setPolicy(data.data);
        setBookmarked(Boolean(data.data?.bookmarked));
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        setToast({ open: true, msg: "정책 상세를 불러오지 못했습니다", severity: "error" });
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    fetchPolicy();
    return () => controller.abort();
  }, [id, isLoggedIn, searchParams]);

  useEffect(() => {
    if (!policy?.unifiedCategory) return;
    api.get("/api/policies", {
      params: { category: policy.unifiedCategory, size: 6, statusFilter: "ACTIVE_ONLY" },
    }).then(res => {
      const items = (res.data?.data?.content ?? [])
        .filter(p => String(p.id) !== String(id))
        .slice(0, 3);
      setRelated(items);
    }).catch(() => {});
  }, [policy?.unifiedCategory, id]);

  const contacts = useMemo(() => parseContacts(policy?.contactList), [policy?.contactList]);
  const referenceUrls = useMemo(() => parseReferenceUrls(policy?.referenceUrlsJson), [policy?.referenceUrlsJson]);
  const primaryReferenceUrls = useMemo(
    () => referenceUrls.filter((item) => item.url !== policy?.homepageUrl && item.url !== policy?.detailUrl),
    [policy?.detailUrl, policy?.homepageUrl, referenceUrls]
  );
  const hasExtraSection = Boolean(
    policy?.homepageUrl || policy?.relatedLaw || policy?.formFiles || primaryReferenceUrls.length
  );

  const detailTabs = useMemo(() => {
    const tabs = [
      { id: "intro", label: "소개" },
      { id: "support", label: "지원내용" },
      { id: "target", label: "신청대상" },
    ];

    if (policy?.selectionCriteria) {
      tabs.push({ id: "criteria", label: "선정 기준" });
    }

    tabs.push(
      { id: "method", label: "신청방법" },
      { id: "contact", label: "문의처" }
    );

    if (hasExtraSection) {
      tabs.push({ id: "extra", label: "추가정보" });
    }

    return tabs;
  }, [hasExtraSection, policy?.selectionCriteria]);

  const visibleTags = useMemo(() => {
    if (!policy?.tags?.length) return [];
    const seen = new Set();
    return policy.tags
      .map(tag => tag?.tagValue?.trim())
      .filter(v => {
        if (!v || seen.has(v)) return false;
        if (/^[A-Z0-9_]+$/.test(v)) return false;
        seen.add(v);
        return true;
      });
  }, [policy?.tags]);

  useEffect(() => {
    if (!detailTabs.some((tab) => tab.id === activeTab)) {
      setActiveTab(detailTabs[0]?.id ?? "intro");
    }
  }, [activeTab, detailTabs]);

  const ddayInfo = useMemo(() => {
    if (!policy) return null;
    const { applyEndDate: end, applyStartDate: start, status } = policy;
    if (status === "CLOSED") return { label: "종료", daysLeft: -1, progress: 100 };
    if (!end) return { label: status === "UPCOMING" ? "예정" : "상시", daysLeft: null, progress: 0 };

    const today = new Date(); today.setHours(0, 0, 0, 0);
    const endD = new Date(`${end}T00:00:00`);
    const daysLeft = Math.ceil((endD - today) / 86400000);
    if (daysLeft < 0) return { label: "종료", daysLeft: -1, progress: 100 };

    let progress = 0, totalDays = 0, elapsedDays = 0;
    if (start) {
      const startD = new Date(`${start}T00:00:00`);
      totalDays = Math.max(1, Math.ceil((endD - startD) / 86400000));
      elapsedDays = Math.max(0, Math.ceil((today - startD) / 86400000));
      progress = Math.min(100, Math.max(0, (elapsedDays / totalDays) * 100));
    }
    return {
      label: daysLeft === 0 ? "D-Day" : `D-${daysLeft}`,
      daysLeft, progress, totalDays, elapsedDays,
      startDate: formatDate(start),
      endDate: formatDate(end),
    };
  }, [policy]);

  const summaryRows = useMemo(() => {
    if (!policy) return [];
    const period = formatPeriod(policy.applyStartDate, policy.applyEndDate)
      || formatPeriod(policy.startDate, policy.endDate) || "상시/문의";
    const ageRange = formatAgeRange(policy.minAge, policy.maxAge);
    return [
      { ico: "💻", label: "신청방법", value: policy.applyMethodName || (policy.isOnlineApply ? "온라인 신청 가능" : NO_DATA) },
      { ico: "👤", label: "신청대상", value: ageRange || NO_DATA },
      { ico: "🏢", label: "소관부처", value: policy.hostOrg || policy.operatingOrg || NO_DATA },
      { ico: "📅", label: "신청기간", value: period },
      { ico: "💰", label: "지원내용", value: policy.provisionType || policy.supportCycle || NO_DATA },
      { ico: "🎯", label: "키워드", value: visibleTags.slice(0, 4).join(" · ") || NO_DATA },
    ];
  }, [policy, visibleTags]);

  const handleBookmark = async () => {
    if (!isLoggedIn) {
      navigate("/login", {
        state: {
          from: {
            pathname: location.pathname,
            search: location.search,
            state: location.state,
          },
          reason: "login-required",
          postLoginAction: {
            type: "toggle-bookmark",
            policyId: id,
          },
        },
      });
      return;
    }
    try {
      await api.post(`/api/policies/${id}/bookmark`);
      const next = !bookmarked;
      setBookmarked(next);
      setPolicy(cur => cur ? { ...cur, bookmarked: next } : cur);
      setToast({ open: true, msg: next ? "북마크에 저장했어요" : "북마크를 해제했어요", severity: "success" });
    } catch {
      setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" });
    }
  };

  const scrollToSection = (sectionId) => {
    setActiveTab(sectionId);
    const el = document.getElementById(sectionId);
    if (el) {
      const top = el.getBoundingClientRect().top + window.scrollY - 130;
      window.scrollTo({ top, behavior: "smooth" });
    }
  };

  const statusLabel = policy ? formatStatusLabel(policy.status, policy.applyEndDate) : "";
  const regionText = policy?.sido
    || policy?.regions?.filter(r => !/^\d+$/.test(r))?.join(", ")
    || "";
  const backTarget = location.state?.from;
  const chatFromTarget = location.state?.chatFrom?.pathname === "/chat"
    ? location.state.chatFrom
    : backTarget?.pathname === "/chat"
      ? backTarget
      : undefined;
  const listBackTarget = useMemo(() => {
    if (backTarget?.pathname === "/policies") {
      return backTarget;
    }

    return {
      pathname: "/policies",
      search: policy?.unifiedCategory ? `?category=${encodeURIComponent(policy.unifiedCategory)}` : "",
    };
  }, [backTarget, policy?.unifiedCategory]);
  const categoryListTarget = useMemo(() => {
    if (!policy?.unifiedCategory) {
      return listBackTarget;
    }

    const params = new URLSearchParams(backTarget?.pathname === "/policies" ? (backTarget.search ?? "") : "");
    params.set("category", policy.unifiedCategory);
    params.delete("page");

    return {
      pathname: "/policies",
      search: params.toString() ? `?${params.toString()}` : "",
    };
  }, [backTarget, listBackTarget, policy?.unifiedCategory]);
  const inheritedBackTarget = backTarget?.pathname
    ? backTarget
    : {
        pathname: location.pathname,
        search: location.search,
      };

  const handleBack = () => {
    if (backTarget?.pathname) {
      navigate(`${backTarget.pathname}${backTarget.search ?? ""}`);
      return;
    }
    navigate(`${listBackTarget.pathname}${listBackTarget.search ?? ""}`);
  };

  useEffect(() => {
    const postLoginAction = location.state?.postLoginAction;
    if (!isLoggedIn || loading || postLoginAction?.type !== "toggle-bookmark" || String(postLoginAction.policyId) !== String(id)) {
      return;
    }

    const clearPostLoginAction = () => {
      const nextState = { ...(location.state ?? {}) };
      delete nextState.postLoginAction;
      navigate(`${location.pathname}${location.search}`, {
        replace: true,
        state: Object.keys(nextState).length ? nextState : undefined,
      });
    };

    let cancelled = false;
    const runPostLoginAction = async () => {
      try {
        await api.post(`/api/policies/${id}/bookmark`);
        if (cancelled) {
          return;
        }
        const nextBookmarked = !bookmarked;
        setBookmarked(nextBookmarked);
        setPolicy((current) => (current ? { ...current, bookmarked: nextBookmarked } : current));
        setToast({
          open: true,
          msg: nextBookmarked ? "북마크에 저장했어요" : "북마크를 해제했어요",
          severity: "success",
        });
      } catch {
        if (!cancelled) {
          setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" });
        }
      } finally {
        if (!cancelled) {
          clearPostLoginAction();
        }
      }
    };

    void runPostLoginAction();

    return () => {
      cancelled = true;
    };
  }, [bookmarked, id, isLoggedIn, loading, location.pathname, location.search, location.state, navigate]);

  return (
    <div style={{ minHeight: "100vh", background: BG }}>
      <Header />
      <div style={{ maxWidth: 1240, margin: "0 auto", padding: "0 24px 64px" }}>
        <div style={{ paddingTop: 20 }}>
          <button
            onClick={handleBack}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 6,
              padding: "8px 14px",
              borderRadius: 999,
              border: `1px solid ${LINE}`,
              background: WHITE,
              color: INK2,
              fontSize: 13,
              fontWeight: 700,
              cursor: "pointer",
            }}
          >
            ← 뒤로가기
          </button>
        </div>
        {/* Breadcrumb */}
        <nav style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: INK3, padding: "20px 0 8px", flexWrap: "wrap" }}>
          <span style={{ cursor: "pointer" }} onClick={() => navigate("/")}>홈</span>
          <span>›</span>
          <span style={{ cursor: "pointer" }} onClick={() => navigate(`${listBackTarget.pathname}${listBackTarget.search ?? ""}`)}>정책검색</span>
          {policy?.unifiedCategory && (
            <>
              <span>›</span>
              <span style={{ cursor: "pointer" }} onClick={() => navigate(`${categoryListTarget.pathname}${categoryListTarget.search ?? ""}`)}>{policy.unifiedCategory}</span>
            </>
          )}
          {policy?.title && (
            <>
              <span>›</span>
              <span style={{ color: INK2, maxWidth: 300, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{policy.title}</span>
            </>
          )}
        </nav>

        {loading ? <Spinner /> : !policy ? (
          <div style={{ textAlign: "center", padding: "80px 0", color: INK3 }}>
            <div style={{ fontSize: 48, marginBottom: 16 }}>😢</div>
            <div style={{ fontSize: 20, fontWeight: 700, color: INK, marginBottom: 8 }}>정책 정보를 찾지 못했습니다</div>
            <div style={{ fontSize: 14 }}>목록으로 돌아가 다른 정책을 선택해주세요.</div>
            <button onClick={() => navigate(`${listBackTarget.pathname}${listBackTarget.search ?? ""}`)} style={{ marginTop: 24, padding: "10px 24px", borderRadius: 8, background: A, color: WHITE, border: 0, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              목록으로
            </button>
          </div>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: 36, alignItems: "flex-start" }}>
            <main>
              {/* Detail Header */}
              <header style={{ padding: "12px 0 28px", borderBottom: `1px solid ${LINE}` }}>
                <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
                  {policy.unifiedCategory && <Tag bg={AS} color={AI} border={`${A}44`}>{policy.unifiedCategory}</Tag>}
                  <Tag>{formatSource(policy.sourceType)}</Tag>
                  <Tag
                    bg={statusLabel === "진행중" ? "#dcfce7" : statusLabel === "종료" ? LINE2 : "#fef9c3"}
                    color={statusLabel === "진행중" ? "#166534" : statusLabel === "종료" ? INK3 : "#854d0e"}
                    border={statusLabel === "진행중" ? "#bbf7d0" : statusLabel === "종료" ? LINE : "#fde68a"}
                  >
                    {statusLabel}
                  </Tag>
                </div>
                <h1 style={{ margin: "0 0 16px", fontSize: 34, fontWeight: 800, letterSpacing: "-0.025em", lineHeight: 1.2, color: INK }}>
                  {policy.title}
                </h1>
                <div style={{ display: "flex", gap: 18, fontSize: 13, color: INK3, flexWrap: "wrap" }}>
                  {(policy.hostOrg || policy.operatingOrg) && <span>🏢 {policy.hostOrg || policy.operatingOrg}</span>}
                  {regionText && <span>📍 {regionText}</span>}
                  {policy.viewCount != null && <span>👀 {policy.viewCount.toLocaleString()}명이 봤어요</span>}
                </div>
              </header>

              {/* Summary grid */}
              <section style={{ padding: "28px 0", borderBottom: `1px solid ${LINE}` }}>
                <div style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.01em", marginBottom: 16, color: INK }}>요약 정보</div>
                <div style={{ background: AS, borderRadius: 16, padding: 24, display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "20px 32px" }}>
                  {summaryRows.map((r) => (
                    <div key={r.label} style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                      <div style={{ width: 40, height: 40, borderRadius: 10, background: WHITE, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, flexShrink: 0, boxShadow: "0 1px 4px rgba(37,99,235,0.1)" }}>
                        {r.ico}
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 11, fontWeight: 600, color: INK3 }}>{r.label}</div>
                        <div style={{ fontSize: 13, fontWeight: 700, marginTop: 2, color: INK, wordBreak: "keep-all" }}>{r.value}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </section>

              {/* Tab bar */}
              <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${LINE}`, position: "sticky", top: 60, background: BG, zIndex: 10, padding: "8px 0 0" }}>
                {detailTabs.map(t => (
                  <button key={t.id} onClick={() => scrollToSection(t.id)} style={{
                    padding: "14px 22px", background: "transparent", border: 0,
                    borderBottom: `2px solid ${activeTab === t.id ? A : "transparent"}`,
                    color: activeTab === t.id ? INK : INK3,
                    fontSize: 14, fontWeight: activeTab === t.id ? 700 : 500,
                    marginBottom: -1, cursor: "pointer",
                  }}>
                    {t.label}
                  </button>
                ))}
              </div>

              <ContentSection id="intro" title="정책 소개">
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeHtml(policy.description) || `정책 소개 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              <ContentSection id="support" title="지원 내용">
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeHtml(policy.supportDetail || policy.supportContent) || `지원 내용 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              <ContentSection id="target" title="신청 대상">
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeHtml(policy.targetDetail) || `신청 대상 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              {policy.selectionCriteria && (
                <ContentSection id="criteria" title="선정 기준">
                  <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                    {decodeHtml(policy.selectionCriteria)}
                  </p>
                </ContentSection>
              )}

              <ContentSection id="method" title="신청 방법">
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeHtml(policy.applyMethodDetail || policy.applyMethodName) || `신청 방법 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              <ContentSection id="contact" title="문의처">
                {contacts.length > 0 ? (
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 12 }}>
                    {contacts.map((c, i) => (
                      <div key={i} style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 10, padding: "12px 14px" }}>
                        {c.name && <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{c.name}</div>}
                        {c.phone && <div style={{ fontSize: 13, color: INK3, marginTop: 2 }}>📞 {c.phone}</div>}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p style={{ color: INK3, margin: 0 }}>문의처 정보가 없습니다.</p>
                )}
              </ContentSection>

              {hasExtraSection && (
                <ContentSection id="extra" title="추가 정보">
                  <div style={{ display: "grid", gap: 16 }}>
                    {policy.homepageUrl && (
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 6 }}>
                          참고 홈페이지
                        </div>
                        <a
                          href={policy.homepageUrl}
                          target="_blank"
                          rel="noreferrer"
                          style={{ color: A, fontWeight: 700, textDecoration: "none", wordBreak: "break-all" }}
                        >
                          {policy.homepageUrl} ↗
                        </a>
                      </div>
                    )}
                    {primaryReferenceUrls.length > 0 && (
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 8 }}>
                          추가 링크
                        </div>
                        <div style={{ display: "grid", gap: 8 }}>
                          {primaryReferenceUrls.map((item) => (
                            <a
                              key={`${item.type}-${item.url}`}
                              href={item.url}
                              target="_blank"
                              rel="noreferrer"
                              style={{
                                color: A,
                                fontWeight: 700,
                                textDecoration: "none",
                                wordBreak: "break-all",
                                display: "flex",
                                flexDirection: "column",
                                gap: 2,
                              }}
                            >
                              <span>{item.label || item.type} ↗</span>
                              <span style={{ fontSize: 12, color: INK3, fontWeight: 500 }}>
                                {item.displayUrl}
                              </span>
                            </a>
                          ))}
                        </div>
                      </div>
                    )}
                    {policy.relatedLaw && (
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 6 }}>
                          관련 법령
                        </div>
                        <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                          {decodeHtml(policy.relatedLaw)}
                        </p>
                      </div>
                    )}
                    {policy.formFiles && (
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 6 }}>
                          제출 서류
                        </div>
                        <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                          {decodeHtml(policy.formFiles)}
                        </p>
                      </div>
                    )}
                  </div>
                </ContentSection>
              )}

              {visibleTags.length > 0 && (
                <section style={{ padding: "32px 0", borderBottom: `1px solid ${LINE}` }}>
                  <h2 style={{ fontSize: 20, fontWeight: 800, letterSpacing: "-0.01em", margin: "0 0 14px", color: INK }}>관련 태그</h2>
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {visibleTags.map(t => <Tag key={t} bg={WHITE} border={LINE}># {t}</Tag>)}
                  </div>
                </section>
              )}

              {related.length > 0 && (
                <section style={{ padding: "32px 0 56px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                    <div style={{ fontSize: 18, fontWeight: 800, color: INK }}>비슷한 정책</div>
                    <span style={{ fontSize: 13, color: A, cursor: "pointer", fontWeight: 600 }} onClick={() => navigate(`${categoryListTarget.pathname}${categoryListTarget.search ?? ""}`)}>
                      더 보기 →
                    </span>
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14 }}>
                    {related.map(p => {
                      const dday = formatDday(p.applyEndDate, p.status);
                      const urgent = dday !== "종료" && dday !== "상시/문의" && dday !== "예정"
                        && (dday === "D-Day" || Number(dday.slice(2)) <= 14);
                      return (
                        <div key={p.id}
                          style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 14, padding: 18, cursor: "pointer" }}
                          onClick={() => navigate(`/policies/${p.id}`, {
                            state: {
                              from: inheritedBackTarget,
                              chatFrom: chatFromTarget,
                            },
                          })}
                          onMouseEnter={e => { e.currentTarget.style.boxShadow = "0 4px 16px rgba(37,99,235,0.12)"; e.currentTarget.style.borderColor = `${A}44`; }}
                          onMouseLeave={e => { e.currentTarget.style.boxShadow = "none"; e.currentTarget.style.borderColor = LINE; }}
                        >
                          <div style={{ display: "flex", gap: 6, marginBottom: 12, flexWrap: "wrap" }}>
                            <Tag bg={AS} color={AI} border={`${A}33`}>{p.unifiedCategory || "기타"}</Tag>
                            <Tag
                              bg={dday === "종료" ? LINE2 : dday === "상시/문의" ? "#dcfce7" : urgent ? "#fee2e2" : AS}
                              color={dday === "종료" ? INK3 : dday === "상시/문의" ? "#166534" : urgent ? "#991b1b" : AI}
                            >
                              {dday}
                            </Tag>
                          </div>
                          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: "-0.01em", color: INK, lineHeight: 1.4, marginBottom: 8 }}>{p.title}</div>
                          <div style={{ fontSize: 12, color: INK3 }}>자세히 보기 →</div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              )}
            </main>

            {/* Sidebar */}
            <aside style={{ position: "sticky", top: 76 }}>
              <div style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 16, padding: 24, boxShadow: "0 1px 4px rgba(0,0,0,0.04)" }}>
                {ddayInfo && ddayInfo.daysLeft !== null && ddayInfo.daysLeft >= 0 ? (
                  <>
                    <div style={{ fontSize: 11, fontWeight: 700, color: INK3 }}>신청 마감까지</div>
                    <div style={{ display: "flex", alignItems: "baseline", gap: 6, marginTop: 4 }}>
                      <span style={{ fontSize: 32, fontWeight: 800, color: ddayInfo.daysLeft <= 7 ? WARN : A, letterSpacing: "-0.02em" }}>
                        {ddayInfo.daysLeft === 0 ? "오늘" : ddayInfo.daysLeft}
                      </span>
                      {ddayInfo.daysLeft > 0 && (
                        <span style={{ fontSize: 14, color: INK2, fontWeight: 600 }}>일 남음</span>
                      )}
                    </div>
                    {ddayInfo.totalDays > 0 && (
                      <>
                        <div style={{ height: 6, background: LINE2, borderRadius: 99, marginTop: 12, overflow: "hidden" }}>
                          <div style={{
                            height: "100%", width: `${ddayInfo.progress}%`,
                            background: ddayInfo.progress > 80
                              ? `linear-gradient(90deg,${WARN},#f97316)`
                              : `linear-gradient(90deg,${A},#60a5fa)`,
                          }} />
                        </div>
                        <div style={{ fontSize: 11, color: INK3, marginTop: 6 }}>
                          {ddayInfo.startDate} ~ {ddayInfo.endDate} ({ddayInfo.totalDays}일 중 {ddayInfo.elapsedDays}일 경과)
                        </div>
                      </>
                    )}
                  </>
                ) : (
                  <div style={{ textAlign: "center", padding: "8px 0" }}>
                    <div style={{ fontSize: 24, fontWeight: 800, color: ddayInfo?.label === "종료" ? INK3 : A }}>
                      {ddayInfo?.label || "상시/문의"}
                    </div>
                    <div style={{ fontSize: 12, color: INK3, marginTop: 4 }}>
                      {ddayInfo?.label === "종료" ? "신청이 종료된 정책입니다" : "상시 신청 가능 또는 별도 문의"}
                    </div>
                  </div>
                )}

                <hr style={{ border: 0, borderTop: `1px solid ${LINE}`, margin: "20px 0" }} />

                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  <button
                    onClick={() => policy?.detailUrl && window.open(policy.detailUrl, "_blank")}
                    disabled={!policy?.detailUrl}
                    style={{
                      padding: "14px 0", fontSize: 14, fontWeight: 700,
                      background: policy?.detailUrl ? A : LINE2,
                      color: policy?.detailUrl ? WHITE : INK3,
                      border: 0, borderRadius: 10,
                      cursor: policy?.detailUrl ? "pointer" : "not-allowed",
                      display: "flex", justifyContent: "center", alignItems: "center", gap: 6,
                    }}
                    onMouseEnter={e => { if (policy?.detailUrl) e.currentTarget.style.background = A7; }}
                    onMouseLeave={e => { if (policy?.detailUrl) e.currentTarget.style.background = A; }}
                  >
                    {policy?.detailUrl ? "관련 사이트 보기 ↗" : "링크 없음"}
                  </button>
                  {policy?.homepageUrl && policy.homepageUrl !== policy.detailUrl && (
                    <button
                      onClick={() => window.open(policy.homepageUrl, "_blank")}
                      style={{
                        padding: "12px 0", fontSize: 13, fontWeight: 700,
                        background: WHITE,
                        color: A,
                        border: `1px solid ${A}33`,
                        borderRadius: 10, cursor: "pointer",
                      }}
                    >
                      참고 홈페이지 ↗
                    </button>
                  )}
                  {!policy?.homepageUrl && primaryReferenceUrls[0] && (
                    <button
                      onClick={() => window.open(primaryReferenceUrls[0].url, "_blank")}
                      style={{
                        padding: "12px 0", fontSize: 13, fontWeight: 700,
                        background: WHITE,
                        color: A,
                        border: `1px solid ${A}33`,
                        borderRadius: 10, cursor: "pointer",
                      }}
                    >
                      추가 링크 보기 ↗
                    </button>
                  )}
                  <button
                    onClick={handleBookmark}
                    style={{
                      padding: "12px 0", fontSize: 13, fontWeight: 600,
                      background: bookmarked ? "#fef3c7" : WHITE,
                      color: bookmarked ? "#92400e" : INK2,
                      border: `1px solid ${bookmarked ? "#fcd34d" : LINE}`,
                      borderRadius: 10, cursor: "pointer",
                      display: "flex", justifyContent: "center", alignItems: "center", gap: 6,
                    }}
                  >
                    {bookmarked ? "★ 북마크됨" : "♡ 북마크에 저장"}
                  </button>
                </div>

                <hr style={{ border: 0, borderTop: `1px solid ${LINE}`, margin: "20px 0" }} />

                <div style={{ fontSize: 11, fontWeight: 700, color: INK3, marginBottom: 8 }}>공유하기</div>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(window.location.href);
                    setToast({ open: true, msg: "링크를 복사했어요", severity: "success" });
                  }}
                  style={{ width: "100%", padding: "10px 0", borderRadius: 10, background: LINE2, color: INK2, fontSize: 12, fontWeight: 700, border: 0, cursor: "pointer" }}
                >
                  🔗 링크 복사
                </button>
              </div>

              <div style={{ background: AS, border: `1px solid ${A}33`, borderRadius: 16, padding: 20, marginTop: 16 }}>
                <div style={{ fontSize: 13, fontWeight: 800, color: AI }}>💡 내가 자격될까?</div>
                <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
                  {isLoggedIn
                    ? "마이페이지에서 내 조건을 설정하면 자격 여부를 자동으로 확인해드려요."
                    : "로그인하고 1분만에 내 정보를 등록하면 자격 여부를 자동으로 확인해드려요."}
                </div>
                <button
                  onClick={() => navigate(
                    isLoggedIn
                      ? "/mypage"
                      : "/login",
                    {
                      state: {
                        from: {
                          pathname: location.pathname,
                          search: location.search,
                        },
                        ...(chatFromTarget ? { chatFrom: chatFromTarget } : {}),
                        ...(!isLoggedIn ? { reason: "login-required" } : {}),
                      },
                    }
                  )}
                  style={{ marginTop: 12, padding: "8px 14px", fontSize: 12, fontWeight: 700, background: A, color: WHITE, border: 0, borderRadius: 8, cursor: "pointer" }}
                >
                  자격 확인하기 →
                </button>
              </div>
            </aside>
          </div>
        )}
      </div>

      <Snackbar
        open={toast.open}
        autoHideDuration={2200}
        onClose={() => setToast(p => ({ ...p, open: false }))}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
      >
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
      <FloatingNav />
    </div>
  );
}
