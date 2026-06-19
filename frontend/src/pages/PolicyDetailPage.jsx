import { useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Snackbar, Alert, useMediaQuery } from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import CategoryOutlinedIcon from "@mui/icons-material/CategoryOutlined";
import PaymentsOutlinedIcon from "@mui/icons-material/PaymentsOutlined";
import ComputerOutlinedIcon from "@mui/icons-material/ComputerOutlined";
import PlaceOutlinedIcon from "@mui/icons-material/PlaceOutlined";
import BusinessOutlinedIcon from "@mui/icons-material/BusinessOutlined";
import CalendarMonthOutlinedIcon from "@mui/icons-material/CalendarMonthOutlined";
import LocalOfferOutlinedIcon from "@mui/icons-material/LocalOfferOutlined";
import VisibilityOutlinedIcon from "@mui/icons-material/VisibilityOutlined";
import CallOutlinedIcon from "@mui/icons-material/CallOutlined";
import OutlinedFlagIcon from "@mui/icons-material/OutlinedFlag";
import ContentCopyOutlinedIcon from "@mui/icons-material/ContentCopyOutlined";
import LightbulbOutlinedIcon from "@mui/icons-material/LightbulbOutlined";
import SentimentDissatisfiedOutlinedIcon from "@mui/icons-material/SentimentDissatisfiedOutlined";
import ForumOutlinedIcon from "@mui/icons-material/ForumOutlined";

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
const SOURCE_NOTICE_TEXT = "정책 정보는 수집 시점 기준으로 재구성되었으며, 신청 전 반드시 원문 공고와 운영기관 안내를 확인하세요.";

const HTML_ENTITIES = {
  "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'",
  "&nbsp;": " ", "&middot;": "·", "&bull;": "•", "&ndash;": "–",
  "&mdash;": "—", "&laquo;": "«", "&raquo;": "»", "&times;": "×",
};
const EXTERNAL_URL_PROTOCOLS = new Set(["http:", "https:"]);
const GOV24_USER_TYPE_TOKENS = ["개인", "가구", "법인/시설/단체", "소상공인"];
const GOV24_BENEFIT_TYPE_TOKENS = [
  "현금",
  "현물",
  "기타",
  "현금(감면)",
  "이용권",
  "서비스(의료)",
  "시설이용",
  "기타(교육)",
  "현금(보험)",
  "현금(장학금)",
  "현금(융자)",
  "기타(상담)",
  "서비스(돌봄)",
  "서비스(일자리)",
  "의료지원",
  "상담/법률지원",
  "기술지원",
  "문화/여가지원",
  "민원",
  "봉사/기부",
];
const POLICY_ERROR_REPORT_REASONS = [
  { value: "REGION_MISMATCH", label: "지역 정보가 다릅니다" },
  { value: "PERIOD_MISMATCH", label: "신청 기간이 다릅니다" },
  { value: "ELIGIBILITY_MISMATCH", label: "자격조건 설명이 다릅니다" },
  { value: "BROKEN_LINK", label: "링크나 원문이 열리지 않습니다" },
  { value: "DUPLICATE_POLICY", label: "중복 정책 같습니다" },
  { value: "OTHER", label: "기타" },
];

const decodeHtml = (text) => {
  if (!text) return text;
  return text.replace(/&[a-zA-Z0-9#]+;/g, (e) => HTML_ENTITIES[e] ?? e);
};

const splitMultiValue = (value) => {
  const normalized = typeof value === "string" ? value.trim() : "";
  if (!normalized) return [];
  const seen = new Set();
  return normalized
    .split("||")
    .map((part) => part.trim())
    .filter((part) => {
      if (!part || seen.has(part)) return false;
      seen.add(part);
      return true;
    });
};

const formatMultiValueText = (value, separator = " · ") => {
  const parts = splitMultiValue(value);
  return parts.length > 0 ? parts.join(separator) : null;
};

const decodeDisplayText = (text) => {
  const decoded = decodeHtml(text);
  if (typeof decoded !== "string") return decoded;
  return decoded.replace(/\s*\|\|\s*/g, " · ");
};

const normalizeSafeExternalUrl = (value) => {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const candidate = /^https?:\/\//i.test(trimmed) ? trimmed : trimmed.startsWith("www.") ? `https://${trimmed}` : trimmed;

  try {
    const parsed = new URL(candidate);
    return EXTERNAL_URL_PROTOCOLS.has(parsed.protocol) ? parsed.toString() : null;
  } catch {
    return null;
  }
};

const safeOpenExternalUrl = (value) => {
  const safeUrl = normalizeSafeExternalUrl(value);
  if (!safeUrl) return false;
  window.open(safeUrl, "_blank", "noopener,noreferrer");
  return true;
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

const formatIncomeRange = (min, max) => {
  if (min && max) return `소득 ${min}~${max}분위`;
  if (min) return `소득 ${min}분위 이상`;
  if (max) return `소득 ${max}분위 이하`;
  return null;
};

const joinMetaParts = (parts, separator = " · ") => {
  const seen = new Set();
  return parts
    .map((part) => (typeof part === "string" ? part.trim() : part))
    .filter((part) => {
      if (!part || seen.has(part)) return false;
      seen.add(part);
      return true;
    })
    .join(separator);
};

const splitGov24MultiLabel = (label) => {
  return splitMultiValue(label);
};

const resolveGov24FallbackLabels = (primaryLabel, candidates, allowedTokens) => {
  const normalizedPrimary = typeof primaryLabel === "string" ? primaryLabel.trim() : "";
  if (normalizedPrimary) return splitGov24MultiLabel(normalizedPrimary);

  const allowed = new Set(allowedTokens);
  const labels = [];
  for (const candidate of candidates) {
    const normalizedCandidate = typeof candidate === "string" ? candidate.trim() : "";
    if (allowed.has(normalizedCandidate) && !labels.includes(normalizedCandidate)) {
      labels.push(normalizedCandidate);
    }
  }

  return labels;
};

const formatGov24LabelText = (labels) => labels.length > 0 ? labels.join(" · ") : null;

const formatSource = (sourceType) => {
  if (sourceType === "YOUTH") return "온통청년";
  if (sourceType === "BOKJIRO_CENTRAL") return "복지로 중앙";
  if (sourceType === "BOKJIRO_LOCAL") return "복지로 지자체";
  if (sourceType === "GOV24") return "정부24";
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
        const displayUrl = typeof item?.url === "string" ? item.url.trim() : "";
        const normalizedUrl = normalizeSafeExternalUrl(displayUrl);
        if (!normalizedUrl) return null;
        return {
          url: normalizedUrl,
          displayUrl,
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

const appendRelatedPolicies = (bucket, items, currentId, relationLabel, limit = 3) => {
  for (const item of items ?? []) {
    if (!item || String(item.id) === String(currentId) || bucket.some((candidate) => String(candidate.id) === String(item.id))) {
      continue;
    }
    bucket.push({
      ...item,
      relationLabel,
    });
    if (bucket.length >= limit) {
      break;
    }
  }
};

function Tag({ children, color, bg, border, onClick, title }) {
  const sharedStyle = {
    display: "inline-flex",
    alignItems: "center",
    padding: "3px 10px",
    borderRadius: 99,
    fontSize: 12,
    fontWeight: 600,
    lineHeight: 1.5,
    color: color ?? INK2,
    background: bg ?? LINE2,
    border: `1px solid ${border ?? "transparent"}`,
  };

  if (typeof onClick === "function") {
    return (
      <button
        type="button"
        onClick={onClick}
        title={title}
        style={{
          ...sharedStyle,
          cursor: "pointer",
          whiteSpace: "nowrap",
        }}
      >
        {children}
      </button>
    );
  }

  return (
    <span style={sharedStyle} title={title}>
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

  const isMobile = useMediaQuery("(max-width: 1199px)");
  const [policy, setPolicy] = useState(null);
  const [bookmarked, setBookmarked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [related, setRelated] = useState([]);
  const [activeTab, setActiveTab] = useState("intro");
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });
  const [reportModalOpen, setReportModalOpen] = useState(false);
  const [reportReasonCode, setReportReasonCode] = useState(POLICY_ERROR_REPORT_REASONS[0].value);
  const [reportNote, setReportNote] = useState("");
  const [reportSubmitting, setReportSubmitting] = useState(false);
  const bookmarkActionKeyRef = useRef(null);

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
    if (!policy) {
      setRelated([]);
      return;
    }

    const controller = new AbortController();
    const fetchRelated = async () => {
      try {
        const relatedItems = [];
        if (policy.unifiedCategory) {
          const { data } = await api.get("/api/policies", {
            params: { category: policy.unifiedCategory, size: 6, statusFilter: "ACTIVE_ONLY" },
            signal: controller.signal,
          });
          appendRelatedPolicies(relatedItems, data?.data?.content, id, "같은 분야");
        }

        if (relatedItems.length < 3 && policy.sourceType) {
          const { data } = await api.get("/api/policies", {
            params: { sourceType: policy.sourceType, size: 6, statusFilter: "ACTIVE_ONLY" },
            signal: controller.signal,
          });
          appendRelatedPolicies(relatedItems, data?.data?.content, id, "같은 출처");
        }

        if (relatedItems.length < 3 && policy.sido) {
          const { data } = await api.get("/api/policies", {
            params: { sido: policy.sido, size: 6, statusFilter: "ACTIVE_ONLY" },
            signal: controller.signal,
          });
          appendRelatedPolicies(relatedItems, data?.data?.content, id, "같은 지역");
        }

        if (!controller.signal.aborted) {
          setRelated(relatedItems.slice(0, 3));
        }
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") {
          return;
        }
        if (!controller.signal.aborted) {
          setRelated([]);
        }
      }
    };

    fetchRelated();
    return () => controller.abort();
  }, [policy, id]);

  const contacts = useMemo(() => parseContacts(policy?.contactList), [policy?.contactList]);
  const referenceUrls = useMemo(() => parseReferenceUrls(policy?.referenceUrlsJson), [policy?.referenceUrlsJson]);
  const safeDetailUrl = useMemo(() => normalizeSafeExternalUrl(policy?.detailUrl), [policy?.detailUrl]);
  const safeHomepageUrl = useMemo(() => normalizeSafeExternalUrl(policy?.homepageUrl), [policy?.homepageUrl]);
  const primaryReferenceUrls = useMemo(
    () => referenceUrls.filter((item) => item.url !== safeHomepageUrl && item.url !== safeDetailUrl),
    [referenceUrls, safeDetailUrl, safeHomepageUrl]
  );
  const hasExtraSection = Boolean(
    safeHomepageUrl || policy?.relatedLaw || policy?.formFiles || primaryReferenceUrls.length
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
      .flatMap(tag => splitMultiValue(tag?.tagValue))
      .filter(v => {
        if (!v || seen.has(v)) return false;
        if (/^[A-Z0-9_]+$/.test(v)) return false;
        seen.add(v);
        return true;
      });
  }, [policy?.tags]);

  const gov24UserTypeDisplayLabels = useMemo(() => {
    if (policy?.sourceType !== "GOV24") return [];
    return resolveGov24FallbackLabels(
      policy?.gov24UserTypeLabel,
      visibleTags,
      GOV24_USER_TYPE_TOKENS
    );
  }, [policy?.gov24UserTypeLabel, policy?.sourceType, visibleTags]);
  const gov24UserTypeDisplayText = useMemo(
    () => formatGov24LabelText(gov24UserTypeDisplayLabels),
    [gov24UserTypeDisplayLabels]
  );

  const gov24BenefitTypeDisplayLabels = useMemo(() => {
    if (policy?.sourceType !== "GOV24") return [];
    return resolveGov24FallbackLabels(
      policy?.gov24BenefitTypeLabel,
      [policy?.provisionType, ...visibleTags],
      GOV24_BENEFIT_TYPE_TOKENS
    );
  }, [policy?.gov24BenefitTypeLabel, policy?.provisionType, policy?.sourceType, visibleTags]);
  const gov24BenefitTypeDisplayText = useMemo(
    () => formatGov24LabelText(gov24BenefitTypeDisplayLabels),
    [gov24BenefitTypeDisplayLabels]
  );

  const gov24MetaTags = useMemo(() => {
    if (policy?.sourceType !== "GOV24") return [];
    return [
      policy?.gov24ServiceFieldLabel && `분야 ${policy.gov24ServiceFieldLabel}`,
      ...gov24UserTypeDisplayLabels.map((label) => `대상 ${label}`),
      ...gov24BenefitTypeDisplayLabels.map((label) => `유형 ${label}`),
    ].filter(Boolean);
  }, [
    gov24BenefitTypeDisplayLabels,
    gov24UserTypeDisplayLabels,
    policy?.sourceType,
    policy?.gov24ServiceFieldLabel,
  ]);
  const youthOfficialFactRows = useMemo(() => {
    if (policy?.sourceType !== "YOUTH") return [];
    return [
      {
        label: "소득조건 유형",
        values: policy?.youthIncomeConditionTypeLabel ? [policy.youthIncomeConditionTypeLabel] : [],
      },
      {
        label: "취업 요건",
        values: policy?.youthEmploymentRequirementLabels ?? [],
      },
      {
        label: "학력 요건",
        values: policy?.youthEducationRequirementLabels ?? [],
      },
      {
        label: "특화 요건",
        values: policy?.youthSpecialRequirementLabels ?? [],
      },
      {
        label: "결혼 상태",
        values: policy?.youthMaritalStatusLabel ? [policy.youthMaritalStatusLabel] : [],
      },
    ].filter((row) => row.values.length > 0);
  }, [
    policy?.sourceType,
    policy?.youthEducationRequirementLabels,
    policy?.youthEmploymentRequirementLabels,
    policy?.youthIncomeConditionTypeLabel,
    policy?.youthMaritalStatusLabel,
    policy?.youthSpecialRequirementLabels,
  ]);
  const statusLabel = policy ? formatStatusLabel(policy.status, policy.applyEndDate) : "";
  const policySourceLabel = policy ? formatSource(policy.sourceType) : "출처 정보 없음";
  const regionText = policy?.sido
    || policy?.regions?.filter(r => !/^\d+$/.test(r))?.join(", ")
    || "";

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
    const incomeRange = formatIncomeRange(policy.minIncome, policy.maxIncome);
    const audienceSummary = joinMetaParts([
      ageRange,
      incomeRange,
      policy.lifeStage,
      gov24UserTypeDisplayText,
    ]) || NO_DATA;
    const categorySummary = joinMetaParts([
      policy.youthMajorLabel,
      policy.youthMidLabel,
      policy.gov24ServiceFieldLabel,
      policy.unifiedCategory,
    ]) || NO_DATA;
    const supportSummary = joinMetaParts([
      formatMultiValueText(policy.provisionType),
      policy.supportCycle,
      gov24BenefitTypeDisplayText,
    ]) || NO_DATA;
    const applyPathSummary = joinMetaParts([
      formatMultiValueText(policy.applyMethodName),
      formatMultiValueText(policy.provisionMethodLabel),
      policy.isOnlineApply ? "온라인 신청 가능" : null,
    ]) || NO_DATA;
    const orgSummary = joinMetaParts([
      policy.hostOrg,
      policy.operatingOrg && policy.operatingOrg !== policy.hostOrg ? policy.operatingOrg : null,
    ]) || NO_DATA;
    const regionSummary = regionText || "전국/원문 확인";

    return [
      { Icon: PersonOutlineIcon, label: "신청대상", value: audienceSummary },
      { Icon: CategoryOutlinedIcon, label: "지원분야", value: categorySummary },
      { Icon: PaymentsOutlinedIcon, label: "지원형태", value: supportSummary },
      { Icon: ComputerOutlinedIcon, label: "신청경로", value: applyPathSummary },
      { Icon: PlaceOutlinedIcon, label: "지원지역", value: regionSummary },
      { Icon: BusinessOutlinedIcon, label: "소관기관", value: orgSummary },
      { Icon: CalendarMonthOutlinedIcon, label: "신청기간", value: period },
      { Icon: LocalOfferOutlinedIcon, label: "키워드", value: visibleTags.slice(0, 4).join(" · ") || NO_DATA },
    ];
  }, [gov24BenefitTypeDisplayText, gov24UserTypeDisplayText, policy, regionText, visibleTags]);

  const quickHighlights = useMemo(() => {
    if (!policy) return [];

    return [...new Set([
      formatAgeRange(policy.minAge, policy.maxAge),
      formatIncomeRange(policy.minIncome, policy.maxIncome),
      policy.lifeStage,
      regionText,
      policy.youthMajorLabel,
      policy.youthMidLabel,
      policy.gov24ServiceFieldLabel,
      gov24UserTypeDisplayText,
      formatMultiValueText(policy.provisionType),
      gov24BenefitTypeDisplayText,
      policy.supportCycle,
      formatMultiValueText(policy.provisionMethodLabel),
      policy.isOnlineApply ? "온라인 신청 가능" : null,
    ].filter(Boolean))];
  }, [gov24BenefitTypeDisplayText, gov24UserTypeDisplayText, policy, regionText]);

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

  const handleOpenErrorReport = () => {
    if (!isLoggedIn) {
      navigate("/login", {
        state: {
          from: {
            pathname: location.pathname,
            search: location.search,
            state: location.state,
          },
          reason: "login-required",
        },
      });
      return;
    }
    setReportModalOpen(true);
  };

  const handleStartApplicationCoaching = () => {
    navigate(`/chat?coachPolicyId=${encodeURIComponent(policy?.id ?? id)}`, {
      state: {
        from: {
          pathname: location.pathname,
          search: location.search,
          state: location.state,
        },
      },
    });
  };

  const handleCloseErrorReport = () => {
    if (reportSubmitting) {
      return;
    }
    setReportModalOpen(false);
  };

  const handleSubmitErrorReport = async () => {
    if (!reportReasonCode || reportSubmitting) {
      return;
    }
    setReportSubmitting(true);
    try {
      await api.post(`/api/policies/${id}/error-reports`, {
        reasonCode: reportReasonCode,
        note: reportNote.trim() || null,
      });
      setReportModalOpen(false);
      setReportReasonCode(POLICY_ERROR_REPORT_REASONS[0].value);
      setReportNote("");
      setToast({ open: true, msg: "오류 제보가 접수되었습니다. 확인 후 반영하겠습니다.", severity: "success" });
    } catch (error) {
      if (error?.response?.status === 401) {
        navigate("/login", {
          state: {
            from: {
              pathname: location.pathname,
              search: location.search,
              state: location.state,
            },
            reason: "login-required",
          },
        });
        return;
      }
      setToast({ open: true, msg: "오류 제보 접수에 실패했습니다", severity: "error" });
    } finally {
      setReportSubmitting(false);
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

  const createGov24DiscoveryTarget = (field, value) => {
    if (!value) return null;
    const params = new URLSearchParams();
    params.set("sourceType", "GOV24");
    params.set(field, value);
    return {
      pathname: "/policies",
      search: `?${params.toString()}`,
    };
  };

  const gov24DiscoveryTargets = useMemo(() => ({
    serviceField: createGov24DiscoveryTarget("gov24ServiceField", policy?.gov24ServiceFieldLabel),
    userType: Object.fromEntries(gov24UserTypeDisplayLabels.map((label) => [
      label,
      createGov24DiscoveryTarget("gov24UserType", label),
    ])),
    benefitType: Object.fromEntries(gov24BenefitTypeDisplayLabels.map((label) => [
      label,
      createGov24DiscoveryTarget("gov24BenefitType", label),
    ])),
  }), [
    policy?.gov24ServiceFieldLabel,
    gov24BenefitTypeDisplayLabels,
    gov24UserTypeDisplayLabels,
  ]);

  const navigateToGov24Discovery = (target) => {
    if (!target) return;
    navigate(`${target.pathname}${target.search}`);
  };

  const handleBack = () => {
    if (backTarget?.pathname) {
      navigate(`${backTarget.pathname}${backTarget.search ?? ""}`, {
        state: backTarget.state,
      });
      return;
    }
    navigate(`${listBackTarget.pathname}${listBackTarget.search ?? ""}`, {
      state: listBackTarget.state,
    });
  };

  useEffect(() => {
    const postLoginAction = location.state?.postLoginAction;
    if (!isLoggedIn || loading || postLoginAction?.type !== "toggle-bookmark" || String(postLoginAction.policyId) !== String(id)) {
      if (!postLoginAction) {
        bookmarkActionKeyRef.current = null;
      }
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
    const actionKey = `${postLoginAction.type}:${postLoginAction.policyId}`;
    if (bookmarkActionKeyRef.current === actionKey) {
      return;
    }
    const runPostLoginAction = async () => {
      bookmarkActionKeyRef.current = actionKey;
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
      <div style={{ maxWidth: 1240, margin: "0 auto", padding: isMobile ? "0 16px 80px" : "0 24px 64px" }}>
        {!isMobile && (
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
        )}
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
            <div style={{ marginBottom: 16 }}><SentimentDissatisfiedOutlinedIcon sx={{ fontSize: 48, color: INK3 }} /></div>
            <div style={{ fontSize: 20, fontWeight: 700, color: INK, marginBottom: 8 }}>정책 정보를 찾지 못했습니다</div>
            <div style={{ fontSize: 14 }}>목록으로 돌아가 다른 정책을 선택해주세요.</div>
            <button onClick={() => navigate(`${listBackTarget.pathname}${listBackTarget.search ?? ""}`)} style={{ marginTop: 24, padding: "10px 24px", borderRadius: 8, background: A, color: WHITE, border: 0, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              목록으로
            </button>
          </div>
        ) : (
          <div
            className="policy-detail-layout"
            style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 340px", gap: 36, alignItems: "flex-start" }}
          >
            <main>
              {/* Detail Header */}
              <header style={{ padding: "12px 0 28px", borderBottom: `1px solid ${LINE}` }}>
                <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
                  {policy.unifiedCategory && <Tag bg={AS} color={AI} border={`${A}44`}>{policy.unifiedCategory}</Tag>}
                  <Tag>{policySourceLabel}</Tag>
                  <Tag
                    bg={statusLabel === "진행중" ? "#dcfce7" : statusLabel === "종료" ? LINE2 : "#fef9c3"}
                    color={statusLabel === "진행중" ? "#166534" : statusLabel === "종료" ? INK3 : "#854d0e"}
                    border={statusLabel === "진행중" ? "#bbf7d0" : statusLabel === "종료" ? LINE : "#fde68a"}
                  >
                    {statusLabel}
                  </Tag>
                </div>
                <h1 style={{ margin: "0 0 16px", fontSize: isMobile ? 24 : 34, fontWeight: 800, letterSpacing: "-0.025em", lineHeight: 1.25, color: INK }}>
                  {policy.title}
                </h1>
                <div style={{ display: "flex", gap: 18, fontSize: 13, color: INK3, flexWrap: "wrap" }}>
                  {(policy.hostOrg || policy.operatingOrg) && <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}><BusinessOutlinedIcon sx={{ fontSize: 15 }} /> {policy.hostOrg || policy.operatingOrg}</span>}
                  {regionText && <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}><PlaceOutlinedIcon sx={{ fontSize: 15 }} /> {regionText}</span>}
                  {policy.viewCount != null && <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}><VisibilityOutlinedIcon sx={{ fontSize: 15 }} /> {policy.viewCount.toLocaleString()}명이 봤어요</span>}
                </div>
                {gov24MetaTags.length > 0 && (
                  <div style={{ display: "flex", gap: 8, marginTop: 16, flexWrap: "wrap" }}>
                    {policy.gov24ServiceFieldLabel && (
                      <Tag
                        bg="#eef7f1"
                        color="#166534"
                        border="#bbf7d0"
                        onClick={() => navigateToGov24Discovery(gov24DiscoveryTargets.serviceField)}
                        title="같은 정부24 서비스분야 정책 보기"
                      >
                        분야 {policy.gov24ServiceFieldLabel}
                      </Tag>
                    )}
                    {gov24UserTypeDisplayLabels.map((label) => (
                      <Tag
                        key={`gov24-user-${label}`}
                        bg="#eef7f1"
                        color="#166534"
                        border="#bbf7d0"
                        onClick={() => navigateToGov24Discovery(gov24DiscoveryTargets.userType[label])}
                        title="같은 정부24 사용자구분 정책 보기"
                      >
                        대상 {label}
                      </Tag>
                    ))}
                    {gov24BenefitTypeDisplayLabels.map((label) => (
                      <Tag
                        key={`gov24-benefit-${label}`}
                        bg="#eef7f1"
                        color="#166534"
                        border="#bbf7d0"
                        onClick={() => navigateToGov24Discovery(gov24DiscoveryTargets.benefitType[label])}
                        title="같은 정부24 지원유형 정책 보기"
                      >
                        유형 {label}
                      </Tag>
                    ))}
                  </div>
                )}
              </header>

              {/* Summary grid */}
              <section style={{ padding: "28px 0", borderBottom: `1px solid ${LINE}` }}>
                <div style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.01em", marginBottom: 16, color: INK }}>요약 정보</div>
                {quickHighlights.length > 0 && (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
                    {quickHighlights.map((item) => (
                      <Tag key={item} bg={WHITE} color={AI} border={`${A}22`}>
                        {item}
                      </Tag>
                    ))}
                  </div>
                )}
                <div style={{ background: AS, borderRadius: 16, padding: 24, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "20px 24px" }}>
                  {summaryRows.map((r) => (
                    <div key={r.label} style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                      <div style={{ width: 40, height: 40, borderRadius: 10, background: WHITE, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, boxShadow: "0 1px 4px rgba(37,99,235,0.1)" }}>
                        <r.Icon sx={{ fontSize: 22, color: A }} />
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
              <div
                className="policy-detail-tabbar"
                style={{ display: "flex", gap: 2, borderBottom: `1px solid ${LINE}`, position: "sticky", top: 60, background: BG, zIndex: 10, padding: "4px 0 0" }}
              >
                {detailTabs.map(t => (
                  <button key={t.id} onClick={() => scrollToSection(t.id)} style={{
                    padding: "7px 14px", background: "transparent", border: 0,
                    borderBottom: `2px solid ${activeTab === t.id ? A : "transparent"}`,
                    color: activeTab === t.id ? INK : INK3,
                    fontSize: 13, fontWeight: activeTab === t.id ? 700 : 500,
                    marginBottom: -1, cursor: "pointer", whiteSpace: "nowrap",
                  }}>
                    {t.label}
                  </button>
                ))}
              </div>

              <ContentSection id="intro" title="정책 소개">
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeDisplayText(policy.description) || `정책 소개 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              <ContentSection id="support" title="지원 내용">
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeDisplayText(policy.supportDetail || policy.supportContent) || `지원 내용 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              <ContentSection id="target" title="신청 대상">
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
                  {formatAgeRange(policy.minAge, policy.maxAge) && (
                    <Tag bg={WHITE} border={LINE}>연령 {formatAgeRange(policy.minAge, policy.maxAge)}</Tag>
                  )}
                  {formatIncomeRange(policy.minIncome, policy.maxIncome) && (
                    <Tag bg={WHITE} border={LINE}>소득 {formatIncomeRange(policy.minIncome, policy.maxIncome).replace("소득 ", "")}</Tag>
                  )}
                  {policy.lifeStage && <Tag bg={WHITE} border={LINE}>{policy.lifeStage}</Tag>}
                  {gov24UserTypeDisplayLabels.map((label) => (
                    <Tag
                      key={`target-gov24-user-${label}`}
                      bg={WHITE}
                      border={LINE}
                      onClick={() => navigateToGov24Discovery(gov24DiscoveryTargets.userType[label])}
                      title="같은 정부24 사용자구분 정책 보기"
                    >
                      {label}
                    </Tag>
                  ))}
                  {regionText && <Tag bg={WHITE} border={LINE}>{regionText}</Tag>}
                </div>
                {youthOfficialFactRows.length > 0 && (
                  <div
                    style={{
                      background: WHITE,
                      border: `1px solid ${LINE}`,
                      borderRadius: 14,
                      padding: 16,
                      marginBottom: 14,
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 800, color: INK, marginBottom: 10 }}>
                      온통청년 공식 요건
                    </div>
                    <div style={{ display: "grid", gap: 10 }}>
                      {youthOfficialFactRows.map((row) => (
                        <div key={row.label}>
                          <div style={{ fontSize: 12, fontWeight: 700, color: INK3, marginBottom: 6 }}>
                            {row.label}
                          </div>
                          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                            {row.values.map((value) => (
                              <Tag key={`${row.label}-${value}`} bg={WHITE} border={LINE}>
                                {value}
                              </Tag>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeDisplayText(policy.targetDetail) || `신청 대상 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              {policy.selectionCriteria && (
                <ContentSection id="criteria" title="선정 기준">
                  <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                    {decodeDisplayText(policy.selectionCriteria)}
                  </p>
                </ContentSection>
              )}

              <ContentSection id="method" title="신청 방법">
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
                  {[...new Set([
                    ...splitMultiValue(policy.applyMethodName),
                    ...splitMultiValue(policy.provisionMethodLabel),
                  ])].map((label) => (
                    <Tag key={`method-${label}`} bg={WHITE} border={LINE}>{label}</Tag>
                  ))}
                  {policy.isOnlineApply && <Tag bg={WHITE} border={LINE}>온라인 신청 가능</Tag>}
                </div>
                <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                  {decodeDisplayText(policy.applyMethodDetail || policy.applyMethodName) || `신청 방법 정보가 없습니다. ${NO_DATA}`}
                </p>
              </ContentSection>

              <ContentSection id="contact" title="문의처">
                {contacts.length > 0 ? (
                  <div className="policy-detail-contact-grid" style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 12 }}>
                    {contacts.map((c, i) => (
                      <div key={i} style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 10, padding: "12px 14px" }}>
                        {c.name && <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{c.name}</div>}
                        {c.phone && <div style={{ fontSize: 13, color: INK3, marginTop: 2, display: "flex", alignItems: "center", gap: 4 }}><CallOutlinedIcon sx={{ fontSize: 14 }} /> {c.phone}</div>}
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
                    {safeHomepageUrl && (
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 6 }}>
                          참고 홈페이지
                        </div>
                        <a
                          href={safeHomepageUrl}
                          target="_blank"
                          rel="noopener noreferrer"
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
                              rel="noopener noreferrer"
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
                          {decodeDisplayText(policy.relatedLaw)}
                        </p>
                      </div>
                    )}
                    {policy.formFiles && (
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: INK, marginBottom: 6 }}>
                          제출 서류
                        </div>
                        <p style={{ whiteSpace: "pre-line", margin: 0 }}>
                          {decodeDisplayText(policy.formFiles)}
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
                  <div className="policy-detail-related-grid" style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14 }}>
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
                            {p.relationLabel && (
                              <Tag bg="#eff6ff" color={A7} border={`${A}22`}>{p.relationLabel}</Tag>
                            )}
                            <Tag bg={AS} color={AI} border={`${A}33`}>{p.unifiedCategory || "기타"}</Tag>
                            <Tag
                              bg={dday === "종료" ? LINE2 : dday === "상시/문의" ? "#dcfce7" : urgent ? "#fee2e2" : AS}
                              color={dday === "종료" ? INK3 : dday === "상시/문의" ? "#166534" : urgent ? "#991b1b" : AI}
                            >
                              {dday}
                            </Tag>
                          </div>
                          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: "-0.01em", color: INK, lineHeight: 1.4, marginBottom: 8 }}>{p.title}</div>
                          {(p.hostOrg || p.sido || p.operatingOrg) && (
                            <div style={{ fontSize: 12, color: INK3, marginBottom: 6 }}>
                              {joinMetaParts([p.hostOrg, p.sido, p.operatingOrg])}
                            </div>
                          )}
                          <div style={{ fontSize: 12, color: INK3 }}>자세히 보기 →</div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              )}
            </main>

            {/* Sidebar */}
            <aside className="policy-detail-aside" style={{ position: "sticky", top: 76 }}>
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
                    onClick={() => safeOpenExternalUrl(safeDetailUrl)}
                    disabled={!safeDetailUrl}
                    style={{
                      padding: "14px 0", fontSize: 14, fontWeight: 700,
                      background: safeDetailUrl ? A : LINE2,
                      color: safeDetailUrl ? WHITE : INK3,
                      border: 0, borderRadius: 10,
                      cursor: safeDetailUrl ? "pointer" : "not-allowed",
                      display: "flex", justifyContent: "center", alignItems: "center", gap: 6,
                    }}
                    onMouseEnter={e => { if (safeDetailUrl) e.currentTarget.style.background = A7; }}
                    onMouseLeave={e => { if (safeDetailUrl) e.currentTarget.style.background = A; }}
                  >
                    {safeDetailUrl ? "관련 사이트 보기 ↗" : "링크 없음"}
                  </button>
                  {safeHomepageUrl && safeHomepageUrl !== safeDetailUrl && (
                    <button
                      onClick={() => safeOpenExternalUrl(safeHomepageUrl)}
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
                  {!safeHomepageUrl && primaryReferenceUrls[0] && (
                    <button
                      onClick={() => safeOpenExternalUrl(primaryReferenceUrls[0].url)}
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
                  <div style={{ padding: 14, borderRadius: 12, background: "#f8fbff", border: `1px solid ${LINE}`, color: INK2, fontSize: 12, lineHeight: 1.65 }}>
                    <div style={{ fontWeight: 800, color: INK, marginBottom: 4 }}>
                      자료 출처: {policySourceLabel} 및 각 운영기관 공고
                    </div>
                    <div>
                      {SOURCE_NOTICE_TEXT}
                    </div>
                  </div>
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
                  <button
                    onClick={handleStartApplicationCoaching}
                    style={{
                      padding: "12px 0", fontSize: 13, fontWeight: 700,
                      background: "#ecfeff",
                      color: "#0f766e",
                      border: "1px solid #99f6e4",
                      borderRadius: 10, cursor: "pointer",
                      display: "flex", justifyContent: "center", alignItems: "center", gap: 6,
                    }}
                  >
                    <ForumOutlinedIcon sx={{ fontSize: 16 }} /><span>AI와 신청 준비하기</span>
                  </button>
                  <button
                    onClick={handleOpenErrorReport}
                    style={{
                      padding: "12px 0", fontSize: 13, fontWeight: 600,
                      background: WHITE,
                      color: INK2,
                      border: `1px solid ${LINE}`,
                      borderRadius: 10, cursor: "pointer",
                      display: "flex", justifyContent: "center", alignItems: "center", gap: 6,
                    }}
                  >
                    <OutlinedFlagIcon sx={{ fontSize: 16 }} /><span>정책 오류 제보</span>
                  </button>
                </div>

                <hr style={{ border: 0, borderTop: `1px solid ${LINE}`, margin: "20px 0" }} />

                <div style={{ fontSize: 11, fontWeight: 700, color: INK3, marginBottom: 8 }}>공유하기</div>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(window.location.href);
                    setToast({ open: true, msg: "링크를 복사했어요", severity: "success" });
                  }}
                  style={{ width: "100%", padding: "10px 0", borderRadius: 10, background: LINE2, color: INK2, fontSize: 12, fontWeight: 700, border: 0, cursor: "pointer", display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 6 }}
                >
                  <ContentCopyOutlinedIcon sx={{ fontSize: 15 }} /><span>링크 복사</span>
                </button>
              </div>

              <div style={{ background: AS, border: `1px solid ${A}33`, borderRadius: 16, padding: 20, marginTop: 16 }}>
                <div style={{ fontSize: 13, fontWeight: 800, color: AI, display: "flex", alignItems: "center", gap: 4 }}><LightbulbOutlinedIcon sx={{ fontSize: 16 }} /><span>내가 자격될까?</span></div>
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

      {isMobile && (
        <button
          onClick={handleBack}
          style={{
            position: "fixed",
            bottom: 72,
            right: 16,
            zIndex: 1100,
            display: "flex",
            alignItems: "center",
            padding: "12px 16px",
            borderRadius: 999,
            border: `1px solid ${LINE}`,
            background: WHITE,
            color: INK2,
            fontSize: 20,
            fontWeight: 400,
            cursor: "pointer",
            boxShadow: "0 4px 16px rgba(0,0,0,0.15)",
          }}
        >
          ←
        </button>
      )}

      {reportModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 1400,
            background: "rgba(15,23,42,0.52)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 16,
          }}
          onClick={handleCloseErrorReport}
        >
          <div
            style={{
              width: "100%",
              maxWidth: 520,
              background: WHITE,
              borderRadius: 20,
              border: `1px solid ${LINE}`,
              boxShadow: "0 20px 60px rgba(15,23,42,0.20)",
            }}
            onClick={(event) => event.stopPropagation()}
          >
            <div style={{ padding: "22px 22px 18px", borderBottom: `1px solid ${LINE}` }}>
              <div style={{ fontSize: 12, fontWeight: 800, color: A7, letterSpacing: "0.05em" }}>
                POLICY ERROR REPORT
              </div>
              <div style={{ marginTop: 8, fontSize: 22, fontWeight: 800, color: INK, letterSpacing: "-0.02em" }}>
                정책 오류 제보
              </div>
              <div style={{ marginTop: 8, fontSize: 13, color: INK2, lineHeight: 1.65 }}>
                지역, 신청 기간, 자격조건, 링크 문제처럼 실제 정책 정보가 다르게 보이면 제보해주세요.
              </div>
            </div>
            <div style={{ padding: 22, display: "grid", gap: 16 }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: INK2, marginBottom: 8 }}>제보 사유</div>
                <select
                  value={reportReasonCode}
                  onChange={(event) => setReportReasonCode(event.target.value)}
                  style={{
                    width: "100%",
                    padding: "12px 14px",
                    borderRadius: 12,
                    border: `1px solid ${LINE}`,
                    background: WHITE,
                    fontSize: 14,
                    color: INK,
                    outline: "none",
                    boxSizing: "border-box",
                  }}
                >
                  {POLICY_ERROR_REPORT_REASONS.map((reason) => (
                    <option key={reason.value} value={reason.value}>{reason.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: INK2, marginBottom: 8 }}>추가 설명</div>
                <textarea
                  value={reportNote}
                  onChange={(event) => setReportNote(event.target.value.slice(0, 1000))}
                  placeholder="예: 본문은 서울인데 실제 링크는 인천 공고로 연결됩니다."
                  style={{
                    width: "100%",
                    minHeight: 120,
                    resize: "vertical",
                    padding: "12px 14px",
                    borderRadius: 12,
                    border: `1px solid ${LINE}`,
                    background: WHITE,
                    fontSize: 14,
                    color: INK,
                    outline: "none",
                    boxSizing: "border-box",
                    fontFamily: "inherit",
                    lineHeight: 1.6,
                  }}
                />
                <div style={{ marginTop: 6, fontSize: 12, color: INK3, textAlign: "right" }}>
                  {reportNote.length}/1000
                </div>
              </div>
            </div>
            <div style={{ padding: 22, paddingTop: 0, display: "flex", justifyContent: "flex-end", gap: 10, flexWrap: "wrap" }}>
              <button
                onClick={handleCloseErrorReport}
                style={{
                  padding: "12px 16px",
                  borderRadius: 12,
                  border: `1px solid ${LINE}`,
                  background: WHITE,
                  color: INK2,
                  fontSize: 14,
                  fontWeight: 700,
                  cursor: reportSubmitting ? "not-allowed" : "pointer",
                  opacity: reportSubmitting ? 0.7 : 1,
                }}
              >
                취소
              </button>
              <button
                onClick={handleSubmitErrorReport}
                disabled={reportSubmitting}
                style={{
                  padding: "12px 16px",
                  borderRadius: 12,
                  border: 0,
                  background: A,
                  color: WHITE,
                  fontSize: 14,
                  fontWeight: 800,
                  cursor: reportSubmitting ? "not-allowed" : "pointer",
                  opacity: reportSubmitting ? 0.75 : 1,
                }}
              >
                {reportSubmitting ? "접수 중..." : "오류 제보 보내기"}
              </button>
            </div>
          </div>
        </div>
      )}

      <Snackbar
        open={toast.open}
        autoHideDuration={2200}
        onClose={() => setToast(p => ({ ...p, open: false }))}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
      >
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
      <FloatingNav />
      <style>{`
        @media (max-width: 980px) {
          .policy-detail-layout {
            grid-template-columns: minmax(0, 1fr) !important;
          }

          .policy-detail-aside {
            position: static !important;
            top: auto !important;
          }

          .policy-detail-related-grid,
          .policy-detail-contact-grid {
            grid-template-columns: 1fr !important;
          }
        }

        @media (max-width: 640px) {
          .policy-detail-tabbar {
            overflow-x: auto;
            scrollbar-width: none;
          }

          .policy-detail-tabbar::-webkit-scrollbar {
            display: none;
          }
        }
      `}</style>
    </div>
  );
}
