import { useState, useEffect, useCallback } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Snackbar, Alert, CircularProgress,
} from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import IncomeCalculatorModal from "../components/IncomeCalculatorModal";
import api from "../lib/axios";
import {
  deletePushSubscription,
  fetchMyPushSubscriptions,
  fetchPushPublicKey,
  getCurrentPushSubscription,
  isWebPushSupported,
  registerCurrentBrowserPush,
  unsubscribeCurrentBrowserPush,
} from "../lib/webPush";
import { performServerLogout } from "../lib/session";
import { useAuthStore } from "../store/authStore";

// Design tokens
const A = "#2563eb", A7 = "#1d4ed8", AS = "#e8efff", AI = "#1e3a8a";
const WARN = "#ef4444";
const BG = "#f7f8fc", WHITE = "#fff";
const INK = "#11131a", INK2 = "#4a4f5c", INK3 = "#6b7280";
const LINE = "#e5e7eb", LINE2 = "#f3f4f6";

// Static data
const INCOME_ROWS = [
  { value: 1, left: "1~2분위 (하위 20%)", right: "기초생활수급자" },
  { value: 3, left: "3~4분위 (하위 40%)", right: "차상위계층" },
  { value: 5, left: "5~6분위 (중간)",     right: "소득 하위 50% 이하" },
  { value: 7, left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: 9, left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const PRIORITY_OPTIONS = [
  { value: "HOUSING",       label: "주거",          bg: "#fef3c7" },
  { value: "JOB",           label: "일자리",         bg: "#dbeafe" },
  { value: "EDUCATION",     label: "교육·직업훈련",  bg: "#dcfce7" },
  { value: "FINANCE",       label: "금융·생활",      bg: "#fce7f3" },
  { value: "CULTURE",       label: "문화·여가",      bg: "#ede9fe" },
  { value: "PARTICIPATION", label: "참여·기회",      bg: "#fed7aa" },
  { value: "FAMILY",        label: "가족·돌봄",      bg: "#d1fae5" },
  { value: "DEADLINE",      label: "마감임박",        bg: "#fee2e2" },
];

const REGIONS = ["서울","부산","대구","인천","광주","대전","울산","세종","경기","강원","충북","충남","전북","전남","경북","경남","제주"];
const HOUSEHOLD_TYPES = ["1인가구", "한부모", "다자녀", "조손", "기타"];
const INTEREST_FIELD_OPTIONS = ["주거", "취업", "교육", "금융", "문화", "건강", "가족돌봄", "참여"];
const TARGET_TYPE_OPTIONS = ["농어촌", "자립준비청년", "가족돌봄", "다문화", "북한이탈", "한부모", "조손", "보훈", "장애", "병역"];
const NOTIFICATION_SCORE_OPTIONS = [
  { value: 0.3, label: "낮음 이상" },
  { value: 0.5, label: "보통 이상" },
  { value: 0.7, label: "높음 이상" },
  { value: 0.9, label: "매우 높은 추천만" },
];
const DISPLAY_COUNT_OPTIONS = [5, 10, 20, 30];

const SIDO_TO_REGION = {
  "서울특별시": "서울", "부산광역시": "부산", "대구광역시": "대구",
  "인천광역시": "인천", "광주광역시": "광주", "대전광역시": "대전",
  "울산광역시": "울산", "세종특별자치시": "세종", "경기도": "경기",
  "강원특별자치도": "강원", "충청북도": "충북", "충청남도": "충남",
  "전북특별자치도": "전북", "전라남도": "전남", "경상북도": "경북",
  "경상남도": "경남", "제주특별자치도": "제주",
};

const REGION_TO_SIDO = {
  "서울": "서울특별시", "부산": "부산광역시", "대구": "대구광역시",
  "인천": "인천광역시", "광주": "광주광역시", "대전": "대전광역시",
  "울산": "울산광역시", "세종": "세종특별자치시", "경기": "경기도",
  "강원": "강원특별자치도", "충북": "충청북도", "충남": "충청남도",
  "전북": "전북특별자치도", "전남": "전라남도", "경북": "경상북도",
  "경남": "경상남도", "제주": "제주특별자치도",
};

const DISTRICT_MAP = {
  "서울": ["강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"],
  "부산": ["강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"],
  "대구": ["남구","달서구","달성군","동구","북구","서구","수성구","중구"],
  "인천": ["강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"],
  "광주": ["광산구","남구","동구","북구","서구"],
  "대전": ["대덕구","동구","서구","유성구","중구"],
  "울산": ["남구","동구","북구","울주군","중구"],
  "경기": ["가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"],
  "강원": ["강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"],
  "충북": ["괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"],
  "충남": ["계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"],
  "전북": ["고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"],
  "전남": ["강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"],
  "경북": ["경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"],
  "경남": ["거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"],
  "제주": ["서귀포시","제주시"],
};

const TAB_IDS = ['info', 'pref', 'bookmark', 'noti', 'filter', 'account'];
const TAB_META = {
  info:     { l: "내 정보",      sub: "기본 인적사항",  color: "#2563eb" },
  pref:     { l: "우선순위",     sub: "관심 카테고리",  color: "#7c3aed" },
  bookmark: { l: "북마크",       sub: "저장한 정책",    color: "#f59e0b" },
  noti:     { l: "알림 설정",    sub: "이메일·푸시",    color: "#ef4444" },
  filter:   { l: "필터 기본값",  sub: "메인 화면 필터", color: "#059669" },
  account:  { l: "계정",         sub: "비밀번호·탈퇴",  color: "#374151" },
};
const TAB_TITLES = {
  info:     { t: "내 정보",      d: "정확한 정보는 더 잘 맞는 정책 추천으로 이어져요" },
  pref:     { t: "우선순위",     d: "관심있는 카테고리 순서가 추천 가중치에 반영돼요" },
  bookmark: { t: "북마크",       d: "저장해 둔 정책을 모아볼 수 있어요" },
  noti:     { t: "알림 설정",    d: "놓치기 쉬운 마감 알림을 받을 수 있어요" },
  filter:   { t: "필터 기본값",  d: "메인 페이지의 기본 필터를 정해두세요" },
  account:  { t: "계정",         d: "비밀번호·탈퇴 관련 설정" },
};

const formatDday = (dateText, status) => {
  if (status === "CLOSED") return "종료";
  if (!dateText) return status === "UPCOMING" ? "예정" : "상시/문의";
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const end = new Date(`${dateText}T00:00:00`);
  if (Number.isNaN(end.getTime())) return "상시/문의";
  const diff = Math.ceil((end - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
};

const mapBookmark = (p) => ({
  id: p.id, title: p.title,
  category: p.unifiedCategory || "기타",
  dday: formatDday(p.applyEndDate, p.status),
  source: p.hostOrg || p.sido || p.operatingOrg || p.applyMethodName || "",
  applyEndDate: p.applyEndDate,
  status: p.status,
});

// ── Shared UI components ─────────────────────────────────────────────────────

const iCss = (err, disabled) => ({
  width: "100%", padding: "11px 14px", borderRadius: 10,
  border: `1px solid ${err ? WARN : LINE}`,
  background: disabled ? LINE2 : WHITE,
  fontSize: 14, fontFamily: "inherit", color: INK, outline: "none", boxSizing: "border-box",
});

const selCss = (disabled) => ({
  width: "100%", padding: "11px 14px", borderRadius: 10,
  border: `1px solid ${LINE}`, background: disabled ? LINE2 : WHITE,
  fontSize: 14, fontFamily: "inherit", color: INK, outline: "none", boxSizing: "border-box",
  appearance: "none", cursor: disabled ? "not-allowed" : "pointer",
});

function Field({ label, error, hint, action, children }) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
        <label style={{ fontSize: 13, fontWeight: 700, color: INK2 }}>{label}</label>
        {action}
      </div>
      {children}
      {error && <div style={{ fontSize: 12, color: WARN, marginTop: 5 }}>⚠ {error}</div>}
      {hint && !error && <div style={{ fontSize: 12, color: INK3, marginTop: 5 }}>{hint}</div>}
    </div>
  );
}

function SectionCard({ title, desc, children }) {
  return (
    <div style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 18, padding: 28, marginBottom: 16 }}>
      {title && (
        <div style={{ marginBottom: desc ? 4 : 20 }}>
          <div style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.01em", color: INK }}>{title}</div>
          {desc && <div style={{ fontSize: 13, color: INK3, marginTop: 4, marginBottom: 20 }}>{desc}</div>}
        </div>
      )}
      {children}
    </div>
  );
}

function SaveBar({ onSave, onCancel, loading, note }) {
  return (
    <div style={{
      position: "sticky", bottom: 16, marginTop: 20, zIndex: 10,
      background: WHITE, border: `1px solid ${LINE}`, borderRadius: 16, padding: "14px 20px",
      display: "flex", alignItems: "center", gap: 12,
      boxShadow: "0 8px 24px rgba(20,30,80,0.08)",
    }}>
      <span style={{ flex: 1, fontSize: 13, color: INK3 }}>{note || "변경한 내용은 저장 버튼을 눌러야 반영돼요"}</span>
      {onCancel && (
        <button onClick={onCancel} style={{ padding: "10px 18px", borderRadius: 10, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          취소
        </button>
      )}
      <button
        onClick={onSave}
        disabled={loading}
        style={{ padding: "10px 22px", borderRadius: 10, border: 0, background: A, color: WHITE, fontSize: 13, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", gap: 8 }}
        onMouseEnter={e => { e.currentTarget.style.background = A7; }}
        onMouseLeave={e => { e.currentTarget.style.background = A; }}
      >
        {loading ? <CircularProgress size={16} sx={{ color: WHITE }} /> : "저장하기"}
      </button>
    </div>
  );
}

function Toggle({ on, onChange }) {
  return (
    <button onClick={onChange} style={{
      width: 44, height: 26, borderRadius: 99, border: 0, flexShrink: 0,
      background: on ? A : LINE, position: "relative", cursor: "pointer", transition: "background 0.15s",
    }}>
      <span style={{
        position: "absolute", top: 3, left: on ? 21 : 3,
        width: 20, height: 20, borderRadius: "50%",
        background: WHITE, boxShadow: "0 1px 3px rgba(0,0,0,0.15)", transition: "left 0.15s",
      }} />
    </button>
  );
}

function ProfileBanner({ pct, missing, onComplete }) {
  const r = 38, circumference = 2 * Math.PI * r;
  return (
    <div style={{
      background: WHITE, border: `1px solid ${LINE}`, borderRadius: 18, padding: 24,
      display: "grid", gridTemplateColumns: "88px 1fr auto", gap: 20, alignItems: "center",
      boxShadow: "0 1px 2px rgba(20,30,80,0.03)", marginBottom: 24,
    }}>
      <div style={{ position: "relative", width: 88, height: 88 }}>
        <svg width="88" height="88" viewBox="0 0 88 88">
          <circle cx="44" cy="44" r={r} fill="none" stroke={LINE2} strokeWidth="8" />
          <circle cx="44" cy="44" r={r} fill="none" stroke={A} strokeWidth="8"
            strokeDasharray={circumference}
            strokeDashoffset={circumference * (1 - pct / 100)}
            strokeLinecap="round" transform="rotate(-90 44 44)" />
        </svg>
        <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div style={{ fontSize: 20, fontWeight: 800, color: A }}>{pct}%</div>
        </div>
      </div>
      <div>
        <div style={{ fontSize: 16, fontWeight: 800, marginBottom: 6, color: INK }}>프로필을 완성하면 더 정확한 추천을 받을 수 있어요</div>
        {missing.length > 0 && (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center" }}>
            <span style={{ fontSize: 12, color: INK3 }}>아직 입력하지 않은 항목:</span>
            {missing.map(m => (
              <span key={m} style={{ fontSize: 12, color: INK2, background: BG, padding: "2px 10px", borderRadius: 99, fontWeight: 600 }}>{m}</span>
            ))}
          </div>
        )}
      </div>
      <button onClick={onComplete} style={{ padding: "12px 20px", borderRadius: 10, background: A, color: WHITE, border: 0, fontSize: 13, fontWeight: 700, cursor: "pointer", whiteSpace: "nowrap" }}
        onMouseEnter={e => { e.currentTarget.style.background = A7; }}
        onMouseLeave={e => { e.currentTarget.style.background = A; }}
      >
        지금 완성하기 →
      </button>
    </div>
  );
}

function SidebarNav({ active, onChange, bookmarkCount, alertUnreadCount }) {
  return (
    <div style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 18, padding: 12, position: "sticky", top: 80 }}>
      <div style={{ padding: "12px 16px 8px", fontSize: 11, color: INK3, fontWeight: 700, letterSpacing: "0.06em" }}>마이페이지</div>
      {TAB_IDS.map(id => {
        const m = TAB_META[id];
        const isActive = active === id;
        return (
          <button key={id} onClick={() => onChange(id)} style={{
            display: "flex", alignItems: "center", gap: 12, padding: "12px 14px", width: "100%",
            background: isActive ? AS : "transparent",
            border: `1px solid ${isActive ? AS : "transparent"}`,
            borderRadius: 12, textAlign: "left", cursor: "pointer",
            color: isActive ? AI : INK,
          }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: isActive ? m.color : m.color + "20", flexShrink: 0 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, display: "flex", alignItems: "center", gap: 6 }}>
                {m.l}
                {id === "bookmark" && bookmarkCount > 0 && (
                  <span style={{ background: A, color: WHITE, fontSize: 10, padding: "1px 7px", borderRadius: 99, fontWeight: 700 }}>{bookmarkCount}</span>
                )}
                {id === "noti" && alertUnreadCount > 0 && (
                  <span style={{ background: WARN, color: WHITE, fontSize: 10, padding: "1px 7px", borderRadius: 99, fontWeight: 700 }}>{alertUnreadCount}</span>
                )}
              </div>
              <div style={{ fontSize: 11, color: INK3, marginTop: 1 }}>{m.sub}</div>
            </div>
            {isActive && <span style={{ color: A, fontSize: 14 }}>›</span>}
          </button>
        );
      })}
      <div style={{ height: 1, background: LINE, margin: "12px 8px" }} />
      <button style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px", width: "100%", background: "transparent", border: 0, borderRadius: 12, textAlign: "left", cursor: "pointer", color: INK2 }}
        onClick={() => window._performLogout?.()}>
        <div style={{ width: 36, height: 36, borderRadius: 10, display: "flex", alignItems: "center", justifyContent: "center", background: BG, fontSize: 16 }}>↩</div>
        <div style={{ fontSize: 13, fontWeight: 700 }}>로그아웃</div>
      </button>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────
export default function MyPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { isLoggedIn, user, logout, filterSettings, setFilterSettings, setUser } = useAuthStore();

  const initTab = TAB_IDS[parseInt(searchParams.get("tab") ?? "0")] ?? "info";
  const [activeTab, setActiveTab] = useState(initTab);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "success" });
  const showToast = useCallback((msg, severity = "success") => setToast({ open: true, msg, severity }), []);

  useEffect(() => {
    if (!isLoggedIn) {
      navigate("/login", {
        replace: true,
        state: {
          from: location,
          reason: "login-required",
        },
      });
    }
  }, [isLoggedIn, location, navigate]);
  useEffect(() => {
    const tabFromUrl = TAB_IDS[parseInt(searchParams.get("tab") ?? "0")] ?? "info";
    if (tabFromUrl !== activeTab) {
      setActiveTab(tabFromUrl);
    }
  }, [activeTab, searchParams]);

  useEffect(() => {
    const nextTabIndex = String(Math.max(TAB_IDS.indexOf(activeTab), 0));
    if (searchParams.get("tab") === nextTabIndex) {
      return;
    }
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", nextTabIndex);
    setSearchParams(nextParams, { replace: true, state: location.state });
  }, [activeTab, location.state, searchParams, setSearchParams]);

  // expose logout to sidebar
  useEffect(() => {
    window._performLogout = () => performServerLogout(logout).then(() => navigate("/"));
    return () => { delete window._performLogout; };
  }, [logout, navigate]);

  const navigateToPolicyDetail = useCallback((policyId) => {
    navigate(`/policies/${policyId}`, {
      state: {
        from: location,
      },
    });
  }, [location, navigate]);

  // ── State ────────────────────────────────────────────────────────────────
  const [myInfo, setMyInfo] = useState({
    name: "", email: user?.email ?? "",
    birthYear: "", birthMonth: "", birthDay: "",
    region: "", subRegion: "", income: "", employ: "", householdType: "",
  });
  const [profileCompleteness, setProfileCompleteness] = useState(null);
  const [profileAgeBand, setProfileAgeBand] = useState("");
  const [hasPhone, setHasPhone] = useState(false);
  const [editing, setEditing] = useState(false);
  const [infoLoading, setInfoLoading] = useState(false);
  const [nameError, setNameError] = useState("");

  const [priorities, setPriorities] = useState([]);
  const [priorityLoading, setPriorityLoading] = useState(false);
  const [dragIdx, setDragIdx] = useState(null);
  const [interestFields, setInterestFields] = useState([]);
  const [targetTypes, setTargetTypes] = useState([]);

  const [bookmarks, setBookmarks] = useState([]);
  const [bookmarkLoading, setBookmarkLoading] = useState(false);

  const [notifOn, setNotifOn] = useState(false);
  const [notifFreq, setNotifFreq] = useState("DAILY");
  const [notifLoading, setNotifLoading] = useState(false);
  const [notifMinScore, setNotifMinScore] = useState(0.5);
  const [notifDisplayCount, setNotifDisplayCount] = useState(10);
  const [notificationConsentAt, setNotificationConsentAt] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [alertsLoading, setAlertsLoading] = useState(false);
  const [alertsLoaded, setAlertsLoaded] = useState(false);
  const [alertUnreadCount, setAlertUnreadCount] = useState(0);
  const [alertActionLoadingId, setAlertActionLoadingId] = useState(null);
  const [pushSupported, setPushSupported] = useState(false);
  const [pushPermission, setPushPermission] = useState("default");
  const [pushPublicKey, setPushPublicKey] = useState("");
  const [pushSubscriptions, setPushSubscriptions] = useState([]);
  const [currentPushEndpoint, setCurrentPushEndpoint] = useState("");
  const [pushLoading, setPushLoading] = useState(false);
  const [pushActionLoading, setPushActionLoading] = useState(false);
  const [pushStatusError, setPushStatusError] = useState("");
  const [pushActionError, setPushActionError] = useState("");
  const [filterIncludeExpired, setFilterIncludeExpired] = useState(filterSettings?.includeExpired ?? false);

  const [bookmarkSort, setBookmarkSort] = useState("latest");
  const [bookmarkView, setBookmarkView] = useState("list");
  const [incomeCalcOpen, setIncomeCalcOpen] = useState(false);

  const [currPw, setCurrPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [newPwConfirm, setNewPwConfirm] = useState("");
  const [pwLoading, setPwLoading] = useState(false);
  const [withdrawModal, setWithdrawModal] = useState(false);
  const [withdrawPw, setWithdrawPw] = useState("");

  // completion
  const localCompletionScore = [
    myInfo.name ? 20 : 0,
    myInfo.birthYear ? 20 : 0,
    myInfo.region ? 10 : 0,
    myInfo.income ? 10 : 0,
    myInfo.employ ? 10 : 0,
    myInfo.householdType ? 10 : 0,
    interestFields.length > 0 ? 10 : 0,
  ].reduce((sum, score) => sum + score, 0);
  const localCompletionPct = Math.min(localCompletionScore, 100);
  const completionPct = profileCompleteness ?? localCompletionPct;
  const missingLabels = [
    !myInfo.name && "이름",
    !myInfo.birthYear && "생년월일",
    !myInfo.region && "주소",
    !myInfo.income && "소득수준",
    !myInfo.employ && "취업상태",
    !myInfo.householdType && "가구 형태",
    interestFields.length === 0 && "관심 분야",
  ].filter(Boolean);

  // ── Data fetch ───────────────────────────────────────────────────────────
  useEffect(() => {
    if (!isLoggedIn) return;
    const controller = new AbortController();

    const fetchProfile = async () => {
      setInfoLoading(true);
      try {
        const { data } = await api.get("/api/users/me", { signal: controller.signal });
        const p = data.data ?? {};
        const bd = p.birthDate ? p.birthDate.split("-") : ["", "", ""];
        setMyInfo({
          name: p.name ?? "",
          email: p.email ?? "",
          birthYear: bd[0] ?? "",
          birthMonth: bd[1] ? String(parseInt(bd[1])) : "",
          birthDay:   bd[2] ? String(parseInt(bd[2])) : "",
          region:     SIDO_TO_REGION[p.sido] ?? p.sido ?? "",
          subRegion:  p.sgg ?? "",
          income:     p.incomeLevel != null ? String(p.incomeLevel) : "",
          employ:     p.employmentStatus ?? "",
          householdType: p.householdType ?? "",
        });
        setProfileCompleteness(Number.isFinite(p.profileCompleteness) ? p.profileCompleteness : null);
        setProfileAgeBand(p.ageBand ?? "");
        setHasPhone(Boolean(p.hasPhone));
        setNotifOn(p.notificationYn ?? false);
        setNotifFreq(p.notificationPeriod === "WEEKLY" ? "WEEKLY" : "DAILY");
        setNotifMinScore(typeof p.notificationMinScore === "number" ? p.notificationMinScore : 0.5);
        setNotifDisplayCount(Number.isFinite(p.displayCount) ? p.displayCount : 10);
        setNotificationConsentAt(p.notificationConsentAt ?? null);
        setPriorities((p.priorities ?? []).map(item => item.code));
        setInterestFields(Array.isArray(p.interestFields) ? p.interestFields : []);
        setTargetTypes(Array.isArray(p.targetTypes) ? p.targetTypes : []);
        setUser({
          ...(p.name ? { name: p.name } : {}),
          ...(p.email ? { email: p.email } : {}),
          hasPriorities: Array.isArray(p.priorities) && p.priorities.length > 0,
        });
      } catch (err) {
        if (err.name === "CanceledError" || err.code === "ERR_CANCELED") return;
        showToast("프로필을 불러오지 못했습니다", "error");
      } finally {
        if (!controller.signal.aborted) setInfoLoading(false);
      }
    };

    const fetchBookmarks = async () => {
      setBookmarkLoading(true);
      try {
        const { data } = await api.get("/api/users/me/bookmarks", { signal: controller.signal });
        setBookmarks((data.data ?? []).map(mapBookmark));
      } catch (err) {
        if (err.name === "CanceledError" || err.code === "ERR_CANCELED") return;
      } finally {
        if (!controller.signal.aborted) setBookmarkLoading(false);
      }
    };

    fetchProfile();
    fetchBookmarks();
    return () => controller.abort();
  }, [isLoggedIn, setUser, showToast]);

  // ── Handlers ─────────────────────────────────────────────────────────────
  const nameRegex = /^[가-힣]{2,10}$/;

  const handleSaveInfo = async () => {
    if (myInfo.name && !nameRegex.test(myInfo.name)) {
      setNameError("이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요.");
      return;
    }
    setNameError("");
    setInfoLoading(true);
    try {
      const birthDate = myInfo.birthYear && myInfo.birthMonth && myInfo.birthDay
        ? `${myInfo.birthYear}-${String(myInfo.birthMonth).padStart(2,"0")}-${String(myInfo.birthDay).padStart(2,"0")}`
        : undefined;
      await api.put("/api/users/me", {
        name:             myInfo.name || undefined,
        birthDate,
        sido:             myInfo.region ? (REGION_TO_SIDO[myInfo.region] ?? myInfo.region) : undefined,
        sgg:              myInfo.subRegion || undefined,
        incomeLevel:      myInfo.income ? parseInt(myInfo.income) : undefined,
        householdType:    myInfo.householdType || undefined,
        employmentStatus: myInfo.employ || undefined,
      });
      setProfileCompleteness(null);
      setEditing(false);
      showToast("저장되었습니다");
    } catch {
      showToast("저장에 실패했습니다", "error");
    } finally {
      setInfoLoading(false);
    }
  };

  const togglePriority = (val) => {
    if (priorities.includes(val)) setPriorities(priorities.filter(p => p !== val));
    else if (priorities.length < 5) setPriorities([...priorities, val]);
  };
  const removePriority = (val) => setPriorities(priorities.filter(p => p !== val));
  const toggleInterestField = (value) => {
    setInterestFields((prev) => prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]);
  };
  const toggleTargetType = (value) => {
    setTargetTypes((prev) => prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]);
  };
  const handleDragStart = (idx) => setDragIdx(idx);
  const handleDragOver = (e, idx) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const arr = [...priorities];
    const [moved] = arr.splice(dragIdx, 1);
    arr.splice(idx, 0, moved);
    setPriorities(arr);
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  const handleSavePriorities = async () => {
    if (priorities.length === 0) {
      showToast("우선순위는 최소 1개 이상 선택해주세요", "error");
      return;
    }
    setPriorityLoading(true);
    try {
      await api.put("/api/users/me", { interestFields, targetTypes });
      await api.put("/api/users/me/priorities", { priorityCodes: priorities });
      setUser({ hasPriorities: true });
      showToast("우선순위와 관심 설정이 저장되었습니다");
    } catch {
      showToast("우선순위 저장에 실패했습니다", "error");
    } finally {
      setPriorityLoading(false);
    }
  };

  const handleSaveNotif = async () => {
    setNotifLoading(true);
    try {
      await api.put("/api/users/me", {
        notificationYn: notifOn,
        notificationPeriod: notifOn ? notifFreq : "NONE",
        notificationMinScore: notifMinScore,
        displayCount: notifDisplayCount,
      });
      showToast("알림 설정이 저장되었습니다");
    } catch {
      showToast("알림 설정 저장에 실패했습니다", "error");
    } finally {
      setNotifLoading(false);
    }
  };

  const handlePasswordChange = async () => {
    if (!currPw || !newPw || newPw !== newPwConfirm) { showToast("입력값을 확인해주세요", "error"); return; }
    if (newPw.length < 8) { showToast("새 비밀번호는 8자 이상이어야 합니다", "error"); return; }
    if (newPw.length > 100) { showToast("새 비밀번호는 100자 이하여야 합니다", "error"); return; }
    setPwLoading(true);
    try {
      await api.patch("/api/users/me/password", { currentPassword: currPw, newPassword: newPw });
      showToast("비밀번호가 변경되었습니다. 다시 로그인해주세요");
      setCurrPw(""); setNewPw(""); setNewPwConfirm("");
      setTimeout(() => performServerLogout(logout).then(() => navigate("/login", {
        state: {
          from: {
            pathname: location.pathname,
            search: `?tab=${Math.max(TAB_IDS.indexOf(activeTab), 0)}`,
          },
          reason: "password-reset-complete",
          email: myInfo.email || user?.email || "",
        },
      })), 1500);
    } catch (err) {
      const code = err.response?.data?.errorCode;
      showToast(code === "A004" ? "현재 비밀번호가 올바르지 않습니다" : "비밀번호 변경에 실패했습니다", "error");
    } finally {
      setPwLoading(false);
    }
  };

  const handleWithdraw = async () => {
    if (!withdrawPw) { showToast("비밀번호를 입력해주세요", "error"); return; }
    try {
      await api.delete("/api/users/me", { data: { password: withdrawPw } });
      setWithdrawModal(false);
      setWithdrawPw("");
      await performServerLogout(logout);
      navigate("/");
    } catch (err) {
      const code = err.response?.data?.errorCode;
      showToast(code === "A004" ? "비밀번호가 올바르지 않습니다" : "탈퇴 처리에 실패했습니다", "error");
    }
  };

  const formatConsentDateTime = (value) => {
    if (!value) return "";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "";
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }).format(parsed);
  };

  const formatAlertTime = (value) => {
    if (!value) return "";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "";
    const diffMs = Date.now() - parsed.getTime();
    const diffMinutes = Math.floor(diffMs / 60000);
    if (diffMinutes < 1) return "방금 전";
    if (diffMinutes < 60) return `${diffMinutes}분 전`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}일 전`;
    return new Intl.DateTimeFormat("ko-KR", {
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }).format(parsed);
  };

  const fetchAlertUnreadCount = useCallback(async (signal) => {
    const { data } = await api.get("/api/notifications/me/unread-count", { signal });
    setAlertUnreadCount(Number(data?.data?.unreadCount ?? 0));
  }, []);

  const fetchAlerts = useCallback(async (signal) => {
    setAlertsLoading(true);
    try {
      const { data } = await api.get("/api/notifications/me", { signal });
      setAlerts(Array.isArray(data?.data) ? data.data : []);
      setAlertsLoaded(true);
    } finally {
      if (!signal?.aborted) setAlertsLoading(false);
    }
  }, []);

  const fetchPushStatus = useCallback(async () => {
    if (!isWebPushSupported()) {
      setPushSupported(false);
      setPushPermission(typeof window !== "undefined" && "Notification" in window ? Notification.permission : "default");
      setPushSubscriptions([]);
      setCurrentPushEndpoint("");
      setPushPublicKey("");
      setPushStatusError("");
      return;
    }

    setPushSupported(true);
    setPushPermission(Notification.permission);
    setPushLoading(true);
    try {
      const [publicKey, subscriptions, currentSubscription] = await Promise.all([
        fetchPushPublicKey(),
        fetchMyPushSubscriptions(),
        getCurrentPushSubscription(),
      ]);
      setPushPublicKey(publicKey.trim());
      setPushSubscriptions(subscriptions);
      setCurrentPushEndpoint(currentSubscription?.endpoint ?? "");
      setPushStatusError("");
      setPushActionError("");
    } catch (error) {
      setPushPublicKey("");
      setPushSubscriptions([]);
      setCurrentPushEndpoint("");
      setPushStatusError(error?.response?.data?.message || "웹푸시 준비 API가 아직 열려 있지 않거나 최신 백엔드가 배포되지 않았습니다.");
      throw new Error("push status unavailable");
    } finally {
      setPushLoading(false);
    }
  }, []);

  const ddayUrgent = (dday) => dday !== "종료" && dday !== "상시/문의" && dday !== "예정"
    && (dday === "D-Day" || (dday.startsWith("D-") && Number(dday.slice(2)) <= 14));
  const displayedBookmarks = [...bookmarks].sort((left, right) => {
    if (bookmarkSort === "deadline") {
      const leftDeadlineToday = left.dday === "D-Day" ? 0 : 1;
      const rightDeadlineToday = right.dday === "D-Day" ? 0 : 1;
      if (leftDeadlineToday !== rightDeadlineToday) {
        return leftDeadlineToday - rightDeadlineToday;
      }

      const leftUrgent = ddayUrgent(left.dday) ? 0 : 1;
      const rightUrgent = ddayUrgent(right.dday) ? 0 : 1;
      if (leftUrgent !== rightUrgent) {
        return leftUrgent - rightUrgent;
      }

      const leftDays = left.dday.startsWith("D-") ? Number(left.dday.slice(2)) : Number.POSITIVE_INFINITY;
      const rightDays = right.dday.startsWith("D-") ? Number(right.dday.slice(2)) : Number.POSITIVE_INFINITY;
      if (leftDays !== rightDays) {
        return leftDays - rightDays;
      }
    }

    const leftDate = left.applyEndDate ? new Date(`${left.applyEndDate}T00:00:00`).getTime() : 0;
    const rightDate = right.applyEndDate ? new Date(`${right.applyEndDate}T00:00:00`).getTime() : 0;
    return rightDate - leftDate;
  });

  useEffect(() => {
    if (!isLoggedIn) return;
    const controller = new AbortController();
    fetchAlertUnreadCount(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [fetchAlertUnreadCount, isLoggedIn]);

  useEffect(() => {
    if (!isLoggedIn || activeTab !== "noti" || alertsLoaded) return;
    const controller = new AbortController();
    fetchAlerts(controller.signal).catch(() => {
      if (!controller.signal.aborted) {
        showToast("알림함을 불러오지 못했습니다", "error");
      }
    });
    return () => controller.abort();
  }, [activeTab, alertsLoaded, fetchAlerts, isLoggedIn, showToast]);

  useEffect(() => {
    if (!isLoggedIn || activeTab !== "noti") return;
    fetchPushStatus().catch(() => {
      showToast("브라우저 푸시 상태를 불러오지 못했습니다", "error");
    });
  }, [activeTab, fetchPushStatus, isLoggedIn, showToast]);

  const syncAlertsAfterAction = useCallback(async () => {
    const controller = new AbortController();
    try {
      await Promise.all([
        fetchAlerts(controller.signal),
        fetchAlertUnreadCount(controller.signal),
      ]);
    } finally {
      controller.abort();
    }
  }, [fetchAlertUnreadCount, fetchAlerts]);

  const markAlertRead = useCallback(async (alertId, silent = false) => {
    setAlertActionLoadingId(alertId);
    try {
      await api.patch(`/api/notifications/${alertId}/read`);
      setAlerts((prev) => prev.map((alert) => (
        alert.id === alertId
          ? { ...alert, status: "READ", readAt: alert.readAt ?? new Date().toISOString() }
          : alert
      )));
      setAlertUnreadCount((prev) => Math.max(prev - 1, 0));
      if (!silent) showToast("읽음 처리했습니다");
    } catch {
      showToast("알림 읽음 처리에 실패했습니다", "error");
      throw new Error("alert read failed");
    } finally {
      setAlertActionLoadingId(null);
    }
  }, [showToast]);

  const hideAlert = useCallback(async (alertId) => {
    setAlertActionLoadingId(alertId);
    try {
      await api.patch(`/api/notifications/${alertId}/hide`);
      setAlerts((prev) => prev.filter((alert) => alert.id !== alertId));
      setAlertUnreadCount((prev) => {
        const target = alerts.find((alert) => alert.id === alertId);
        return target?.status === "UNREAD" ? Math.max(prev - 1, 0) : prev;
      });
      showToast("알림을 숨겼습니다");
    } catch {
      showToast("알림 숨김 처리에 실패했습니다", "error");
    } finally {
      setAlertActionLoadingId(null);
    }
  }, [alerts, showToast]);

  const openAlert = useCallback(async (alert) => {
    try {
      if (alert.status === "UNREAD") {
        await markAlertRead(alert.id, true);
      }
      if (alert.deeplinkUrl) {
        navigate(alert.deeplinkUrl, {
          state: {
            from: location,
          },
        });
      } else {
        showToast("연결된 화면이 없는 알림입니다", "info");
      }
    } catch {
      // markAlertRead already surfaced the error.
    }
  }, [location, markAlertRead, navigate, showToast]);

  const browserPushConnected = currentPushEndpoint
    && pushSubscriptions.some((subscription) => subscription.endpoint === currentPushEndpoint);
  const pushConnectDisabled = pushActionLoading
    || !pushSupported
    || pushPermission === "denied"
    || Boolean(pushStatusError);

  const handleConnectBrowserPush = async () => {
    if (!pushSupported) {
      showToast("이 브라우저에서는 웹푸시를 지원하지 않습니다", "error");
      return;
    }
    if (pushPermission === "denied") {
      showToast("브라우저 알림 권한이 거부되어 있습니다. 브라우저 설정에서 알림 권한을 먼저 허용해주세요", "error");
      return;
    }
    if (pushStatusError) {
      showToast("웹푸시 준비 API가 아직 열려 있지 않습니다. 최신 백엔드를 먼저 배포해주세요", "error");
      return;
    }
    if (!pushPublicKey) {
      showToast("웹푸시 공개키가 아직 설정되지 않았습니다", "error");
      return;
    }

    setPushActionLoading(true);
    setPushActionError("");
    try {
      const deviceLabel = "현재 브라우저";
      const { permission } = await registerCurrentBrowserPush({ publicKey: pushPublicKey, deviceLabel });
      setPushPermission(permission);
      await fetchPushStatus();
      if (permission === "granted") {
        showToast("브라우저 푸시 연결이 완료되었습니다");
      } else {
        showToast("브라우저 알림 권한이 허용되지 않았습니다", "info");
      }
    } catch (error) {
      const message = error?.response?.data?.message || error?.message || "브라우저 푸시 연결에 실패했습니다";
      setPushActionError(message);
      showToast(message, "error");
    } finally {
      setPushActionLoading(false);
    }
  };

  const handleDisconnectBrowserPush = async () => {
    setPushActionLoading(true);
    setPushActionError("");
    try {
      const currentSubscription = await unsubscribeCurrentBrowserPush();
      const matched = pushSubscriptions.find((subscription) => subscription.endpoint === currentSubscription?.endpoint);
      if (matched) {
        await deletePushSubscription(matched.id);
      }
      await fetchPushStatus();
      showToast("현재 브라우저 푸시 연결을 해제했습니다");
    } catch {
      showToast("브라우저 푸시 해제에 실패했습니다", "error");
    } finally {
      setPushActionLoading(false);
    }
  };

  const handleRemovePushSubscription = async (subscriptionId) => {
    setPushActionLoading(true);
    setPushActionError("");
    try {
      const target = pushSubscriptions.find((subscription) => subscription.id === subscriptionId);
      await deletePushSubscription(subscriptionId);
      if (target?.endpoint && target.endpoint === currentPushEndpoint) {
        await unsubscribeCurrentBrowserPush();
      }
      await fetchPushStatus();
      showToast("등록된 브라우저 푸시 연결을 제거했습니다");
    } catch {
      showToast("등록된 브라우저 푸시 제거에 실패했습니다", "error");
    } finally {
      setPushActionLoading(false);
    }
  };

  if (!isLoggedIn) return null;

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={{ minHeight: "100vh", background: BG }}>
      <Header />
      <div style={{ maxWidth: 1240, margin: "0 auto", padding: "0 24px 64px" }}>
        {/* Breadcrumb */}
        <nav style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: INK3, padding: "20px 0 8px" }}>
          <span style={{ cursor: "pointer" }} onClick={() => navigate("/")}>홈</span>
          <span>›</span>
          <span style={{ color: INK2, fontWeight: 600 }}>마이페이지</span>
        </nav>

        <div style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", color: INK, marginBottom: 20 }}>마이페이지</div>

        {completionPct < 100 && (
          <ProfileBanner
            pct={completionPct}
            missing={missingLabels}
            onComplete={() => setActiveTab(!myInfo.birthYear || !myInfo.region || !myInfo.income || !myInfo.employ ? "info" : "pref")}
          />
        )}

        <div style={{ display: "grid", gridTemplateColumns: "260px 1fr", gap: 28, alignItems: "flex-start" }}>
          {/* Sidebar */}
          <SidebarNav active={activeTab} onChange={setActiveTab} bookmarkCount={bookmarks.length} alertUnreadCount={alertUnreadCount} />

          {/* Content */}
          <div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>{TAB_TITLES[activeTab].t}</div>
              <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>{TAB_TITLES[activeTab].d}</div>
            </div>

            {/* ── 내 정보 ── */}
            {activeTab === "info" && (
              <>
                {infoLoading && !myInfo.name && (
                  <div style={{ display: "flex", justifyContent: "center", padding: 60 }}>
                    <CircularProgress />
                  </div>
                )}
                <SectionCard title="기본 정보" desc="이름과 이메일 정보를 확인하고 수정하세요">
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>
                    <Field label="이름" error={nameError}>
                      <input
                        style={iCss(!!nameError, !editing)}
                        value={myInfo.name}
                        disabled={!editing}
                        onChange={e => { setMyInfo({ ...myInfo, name: e.target.value }); if (nameError) setNameError(""); }}
                        onBlur={() => { if (myInfo.name && !nameRegex.test(myInfo.name)) setNameError("이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요."); }}
                      />
                    </Field>
                    <Field label="이메일">
                      <input style={iCss(false, true)} value={myInfo.email} disabled />
                    </Field>
                  </div>
                  <div style={{ marginTop: 10, fontSize: 12, color: INK3 }}>
                    연락처 등록 상태:{" "}
                    <strong style={{ color: hasPhone ? "#166534" : INK2 }}>
                      {hasPhone ? "등록됨" : "미등록"}
                    </strong>
                    {!hasPhone && " (현재 웹에서는 수정할 수 없어요)"}
                  </div>
                </SectionCard>

                <SectionCard title="생년월일" desc="생년월일은 맞춤 연령 필터에 활용돼요">
                  <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 1fr", gap: 8 }}>
                    <select style={selCss(!editing)} disabled={!editing} value={myInfo.birthYear} onChange={e => setMyInfo({ ...myInfo, birthYear: e.target.value, birthDay: "" })}>
                      <option value="">년도</option>
                      {Array.from({ length: 127 }, (_, i) => 2026 - i).map(y => <option key={y} value={String(y)}>{y}년</option>)}
                    </select>
                    <select style={selCss(!editing)} disabled={!editing} value={myInfo.birthMonth} onChange={e => setMyInfo({ ...myInfo, birthMonth: e.target.value, birthDay: "" })}>
                      <option value="">월</option>
                      {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={String(m)}>{m}월</option>)}
                    </select>
                    <select style={selCss(!editing || !myInfo.birthMonth)} disabled={!editing || !myInfo.birthMonth} value={myInfo.birthDay} onChange={e => setMyInfo({ ...myInfo, birthDay: e.target.value })}>
                      <option value="">일</option>
                      {Array.from({ length: myInfo.birthYear && myInfo.birthMonth ? new Date(myInfo.birthYear, myInfo.birthMonth, 0).getDate() : 31 }, (_, i) => i + 1).map(d => <option key={d} value={String(d)}>{d}일</option>)}
                    </select>
                  </div>
                  {profileAgeBand && (
                    <div style={{ marginTop: 10, fontSize: 12, color: INK3 }}>
                      현재 연령대 분류: <strong style={{ color: INK2 }}>{profileAgeBand}</strong>
                    </div>
                  )}
                </SectionCard>

                <SectionCard title="거주지" desc="지자체별 정책 추천에 사용돼요">
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                    <select style={selCss(!editing)} disabled={!editing} value={myInfo.region} onChange={e => setMyInfo({ ...myInfo, region: e.target.value, subRegion: "" })}>
                      <option value="">시/도 선택</option>
                      {REGIONS.map(r => <option key={r} value={r}>{r}</option>)}
                    </select>
                    <select style={selCss(!editing || !DISTRICT_MAP[myInfo.region])} disabled={!editing || !DISTRICT_MAP[myInfo.region]} value={myInfo.subRegion} onChange={e => setMyInfo({ ...myInfo, subRegion: e.target.value })}>
                      <option value="">시/군/구 선택</option>
                      {(DISTRICT_MAP[myInfo.region] || []).map(d => <option key={d} value={d}>{d}</option>)}
                    </select>
                  </div>
                </SectionCard>

                <SectionCard title="소득수준" desc="기초생활수급 등 자격조건 매칭에 사용돼요. 두 기준 중 하나만 선택하면 돼요.">
                  <div style={{ border: `1px solid ${LINE}`, borderRadius: 14, overflow: "hidden", opacity: editing ? 1 : 0.65, pointerEvents: editing ? "auto" : "none" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", background: BG, padding: "12px 18px", fontSize: 12, fontWeight: 700, color: INK2, borderBottom: `1px solid ${LINE}` }}>
                      <div>분위 기준</div>
                      <div style={{ borderLeft: `1px solid ${LINE}`, paddingLeft: 18 }}>보기 쉬운 기준</div>
                    </div>
                    {INCOME_ROWS.map(row => (
                      <label key={row.value} onClick={() => editing && setMyInfo({ ...myInfo, income: String(row.value) })} style={{
                        display: "grid", gridTemplateColumns: "1fr 1fr", padding: "14px 18px",
                        borderBottom: `1px solid ${LINE2}`, cursor: editing ? "pointer" : "default", alignItems: "center",
                        background: myInfo.income === String(row.value) ? AS : WHITE,
                      }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14 }}>
                          <input type="radio" readOnly checked={myInfo.income === String(row.value)} onChange={() => {}} style={{ accentColor: A }} />
                          {row.left}
                        </div>
                        <div style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14, color: INK2, borderLeft: `1px solid ${LINE2}`, paddingLeft: 18 }}>
                          <input type="radio" readOnly checked={myInfo.income === String(row.value)} onChange={() => {}} style={{ accentColor: A }} />
                          {row.right}
                        </div>
                      </label>
                    ))}
                  </div>
                  <button onClick={() => setIncomeCalcOpen(true)} style={{ background: "transparent", border: 0, color: A, fontSize: 13, fontWeight: 700, padding: "10px 0 0", cursor: "pointer" }}>
                    소득분위 확인하기 →
                  </button>
                </SectionCard>

                <SectionCard title="취업상태">
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10 }}>
                    {["재직중", "구직중", "학생", "기타"].map(v => {
                      const active = myInfo.employ === v;
                      return (
                        <button key={v} onClick={() => editing && setMyInfo({ ...myInfo, employ: v })} style={{
                          padding: "14px 12px", borderRadius: 12,
                          border: `1.5px solid ${active ? A : LINE}`,
                          background: active ? AS : WHITE,
                          color: active ? AI : INK2,
                          fontSize: 14, fontWeight: 700, textAlign: "center", cursor: editing ? "pointer" : "default",
                          opacity: editing ? 1 : 0.65,
                        }}>
                          {v}
                        </button>
                      );
                    })}
                  </div>
                </SectionCard>

                <SectionCard title="가구 형태" desc="특화 대상 정책 매칭에 활용돼요">
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 10 }}>
                    {HOUSEHOLD_TYPES.map(v => {
                      const active = myInfo.householdType === v;
                      return (
                        <button key={v} onClick={() => editing && setMyInfo({ ...myInfo, householdType: v })} style={{
                          padding: "14px 12px", borderRadius: 12,
                          border: `1.5px solid ${active ? A : LINE}`,
                          background: active ? AS : WHITE,
                          color: active ? AI : INK2,
                          fontSize: 14, fontWeight: 700, textAlign: "center", cursor: editing ? "pointer" : "default",
                          opacity: editing ? 1 : 0.65,
                        }}>
                          {v}
                        </button>
                      );
                    })}
                  </div>
                </SectionCard>

                <div style={{ display: "flex", gap: 12, marginBottom: 8 }}>
                  <button onClick={() => setEditing(!editing)} style={{
                    flex: 1, padding: "12px 0", borderRadius: 10,
                    border: `1px solid ${editing ? LINE : A}`,
                    background: editing ? WHITE : A, color: editing ? INK2 : WHITE,
                    fontSize: 14, fontWeight: 600, cursor: "pointer",
                  }}>
                    {editing ? "취소" : "수정"}
                  </button>
                  <button onClick={handleSaveInfo} disabled={!editing || infoLoading} style={{
                    flex: 1, padding: "12px 0", borderRadius: 10,
                    border: 0, background: editing ? A : LINE2,
                    color: editing ? WHITE : INK3,
                    fontSize: 14, fontWeight: 700, cursor: editing ? "pointer" : "not-allowed",
                    display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
                  }}>
                    {infoLoading ? <CircularProgress size={18} sx={{ color: WHITE }} /> : "저장"}
                  </button>
                </div>
              </>
            )}

            {/* ── 우선순위 ── */}
            {activeTab === "pref" && (
              <>
                <SectionCard title="관심있는 정책 카테고리" desc="최대 5개까지 선택할 수 있어요 · 순서가 곧 우선순위예요">
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
                    {PRIORITY_OPTIONS.map(c => {
                      const idx = priorities.indexOf(c.value);
                      const active = idx >= 0;
                      return (
                        <button key={c.value} onClick={() => togglePriority(c.value)}
                          disabled={!active && priorities.length >= 5}
                          style={{
                            position: "relative", padding: "24px 14px 18px", borderRadius: 16,
                            border: `2px solid ${active ? A : LINE}`,
                            background: active ? AS : WHITE,
                            textAlign: "center", cursor: (!active && priorities.length >= 5) ? "not-allowed" : "pointer",
                            opacity: (!active && priorities.length >= 5) ? 0.5 : 1,
                          }}>
                          {active && (
                            <span style={{ position: "absolute", top: 8, right: 8, width: 22, height: 22, borderRadius: "50%", background: A, color: WHITE, fontSize: 11, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center" }}>
                              {idx + 1}
                            </span>
                          )}
                          <div style={{ width: 40, height: 40, borderRadius: 12, background: c.bg, margin: "0 auto 10px" }} />
                          <div style={{ fontSize: 13, fontWeight: 700, color: active ? AI : INK }}>{c.label}</div>
                        </button>
                      );
                    })}
                  </div>
                </SectionCard>

                {priorities.length > 0 && (
                  <SectionCard title="내 우선순위" desc="드래그하여 순서를 바꿀 수 있어요 · 위로 갈수록 더 우선해서 추천돼요">
                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                      {priorities.map((val, i) => {
                        const c = PRIORITY_OPTIONS.find(o => o.value === val);
                        return (
                          <div key={val} draggable
                            onDragStart={() => handleDragStart(i)}
                            onDragOver={e => handleDragOver(e, i)}
                            onDragEnd={handleDragEnd}
                            style={{
                              display: "flex", alignItems: "center", gap: 14, padding: "14px 16px",
                              background: dragIdx === i ? AS : BG, borderRadius: 12, border: `1px solid ${dragIdx === i ? A : LINE}`, cursor: "grab",
                            }}>
                            <span style={{ color: INK3, fontSize: 16 }}>⋮⋮</span>
                            <span style={{ width: 28, height: 28, borderRadius: "50%", background: A, color: WHITE, fontSize: 13, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>{i + 1}</span>
                            <div style={{ width: 28, height: 28, borderRadius: 8, background: c?.bg, flexShrink: 0 }} />
                            <span style={{ fontSize: 14, fontWeight: 700, flex: 1, color: INK }}>{c?.label}</span>
                            <button onClick={() => removePriority(val)} style={{ background: "transparent", border: 0, color: INK3, fontSize: 16, cursor: "pointer", padding: "4px 8px" }}>✕</button>
                          </div>
                        );
                      })}
                    </div>
                  </SectionCard>
                )}

                <SectionCard title="관심 분야" desc="정책 관심사와 맞는 후보를 더 우선해서 보여줘요">
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10 }}>
                    {INTEREST_FIELD_OPTIONS.map((value) => {
                      const active = interestFields.includes(value);
                      return (
                        <button key={value} onClick={() => toggleInterestField(value)} style={{
                          padding: "12px 10px", borderRadius: 12,
                          border: `1.5px solid ${active ? A : LINE}`,
                          background: active ? AS : WHITE,
                          color: active ? AI : INK2,
                          fontSize: 13, fontWeight: 700, cursor: "pointer",
                        }}>
                          {value}
                        </button>
                      );
                    })}
                  </div>
                </SectionCard>

                <SectionCard title="특화 대상" desc="특수 대상 정책이 맞으면 bonus, 어긋나면 mismatch를 줄이는 기준이에요">
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 10 }}>
                    {TARGET_TYPE_OPTIONS.map((value) => {
                      const active = targetTypes.includes(value);
                      return (
                        <button key={value} onClick={() => toggleTargetType(value)} style={{
                          padding: "12px 10px", borderRadius: 12,
                          border: `1.5px solid ${active ? A : LINE}`,
                          background: active ? AS : WHITE,
                          color: active ? AI : INK2,
                          fontSize: 13, fontWeight: 700, cursor: "pointer",
                        }}>
                          {value}
                        </button>
                      );
                    })}
                  </div>
                </SectionCard>

                <SaveBar onSave={handleSavePriorities} loading={priorityLoading} />
              </>
            )}

            {/* ── 북마크 ── */}
            {activeTab === "bookmark" && (
              <SectionCard>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
                  <div>
                    <div style={{ fontSize: 18, fontWeight: 800, color: INK }}>
                      북마크한 정책 <span style={{ color: A }}>({bookmarks.length})</span>
                    </div>
                    <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>마감일을 놓치지 않도록 알림을 설정해보세요</div>
                  </div>
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <select
                      value={bookmarkSort}
                      onChange={e => setBookmarkSort(e.target.value)}
                      style={{ padding: "8px 12px", border: `1px solid ${LINE}`, borderRadius: 8, fontSize: 13, background: WHITE, fontFamily: "inherit", cursor: "pointer", outline: "none" }}
                    >
                      <option value="latest">최신순</option>
                      <option value="deadline">마감임박순</option>
                    </select>
                    <div style={{ display: "flex", border: `1px solid ${LINE}`, borderRadius: 8, overflow: "hidden" }}>
                      <button
                        onClick={() => setBookmarkView("list")}
                        style={{ padding: "8px 12px", background: bookmarkView === "list" ? A : WHITE, color: bookmarkView === "list" ? WHITE : INK3, border: 0, fontSize: 13, cursor: "pointer" }}
                      >☰</button>
                      <button
                        onClick={() => setBookmarkView("grid")}
                        style={{ padding: "8px 12px", background: bookmarkView === "grid" ? A : WHITE, color: bookmarkView === "grid" ? WHITE : INK3, border: 0, fontSize: 13, cursor: "pointer" }}
                      >▦</button>
                    </div>
                  </div>
                </div>
                {bookmarkLoading ? (
                  <div style={{ display: "flex", justifyContent: "center", padding: "40px 0" }}><CircularProgress /></div>
                ) : displayedBookmarks.length > 0 ? (
                  bookmarkView === "grid" ? (
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                      {displayedBookmarks.map(p => {
                        const urgent = ddayUrgent(p.dday);
                        const closed = p.dday === "종료";
                        return (
                          <div
                            key={p.id}
                            style={{
                              display: "flex",
                              flexDirection: "column",
                              gap: 14,
                              padding: "18px 20px",
                              background: closed ? BG : WHITE,
                              border: `1px solid ${urgent ? WARN : LINE}`,
                              borderRadius: 14,
                              opacity: closed ? 0.7 : 1,
                              cursor: "pointer",
                            }}
                            onClick={() => navigateToPolicyDetail(p.id)}
                          >
                            <div>
                              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                                <span style={{ padding: "3px 10px", borderRadius: 99, fontSize: 12, fontWeight: 600, background: AS, color: AI }}>{p.category}</span>
                                <span style={{
                                  padding: "3px 10px", borderRadius: 99, fontSize: 12, fontWeight: 600,
                                  background: closed ? LINE2 : urgent ? "#fee2e2" : "#dcfce7",
                                  color: closed ? INK3 : urgent ? "#991b1b" : "#166534",
                                }}>{p.dday}</span>
                              </div>
                              <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6, color: INK, lineHeight: 1.45 }}>{p.title}</div>
                              {p.source && <div style={{ fontSize: 12, color: INK3 }}>{p.source}</div>}
                            </div>
                            <div style={{ display: "flex", gap: 8, marginTop: "auto" }}>
                              <button style={{ flex: 1, padding: "8px 14px", borderRadius: 8, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 12, fontWeight: 600, cursor: "pointer" }}
                                onClick={async e => {
                                  e.stopPropagation();
                                  try {
                                    await api.post(`/api/policies/${p.id}/bookmark`);
                                    setBookmarks(prev => prev.filter(b => b.id !== p.id));
                                  } catch {
                                    showToast("북마크 해제에 실패했습니다", "error");
                                  }
                                }}>
                                해제
                              </button>
                              <button style={{ flex: 1, padding: "8px 14px", borderRadius: 8, border: 0, background: A, color: WHITE, fontSize: 12, fontWeight: 700, cursor: "pointer" }}
                                onClick={e => { e.stopPropagation(); navigateToPolicyDetail(p.id); }}>
                                상세 →
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                    {displayedBookmarks.map(p => {
                      const urgent = ddayUrgent(p.dday);
                      const closed = p.dday === "종료";
                      return (
                        <div key={p.id} style={{
                          display: "grid", gridTemplateColumns: "1fr auto", gap: 16, padding: "18px 20px",
                          background: closed ? BG : WHITE,
                          border: `1px solid ${urgent ? WARN : LINE}`,
                          borderRadius: 14, opacity: closed ? 0.7 : 1,
                          cursor: "pointer",
                        }}
                          onClick={() => navigateToPolicyDetail(p.id)}>
                          <div>
                            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                              <span style={{ padding: "3px 10px", borderRadius: 99, fontSize: 12, fontWeight: 600, background: AS, color: AI }}>{p.category}</span>
                              <span style={{
                                padding: "3px 10px", borderRadius: 99, fontSize: 12, fontWeight: 600,
                                background: closed ? LINE2 : urgent ? "#fee2e2" : "#dcfce7",
                                color: closed ? INK3 : urgent ? "#991b1b" : "#166534",
                              }}>{p.dday}</span>
                            </div>
                            <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 4, color: INK }}>{p.title}</div>
                            {p.source && <div style={{ fontSize: 12, color: INK3 }}>{p.source}</div>}
                          </div>
                          <div style={{ display: "flex", flexDirection: "column", gap: 6, justifyContent: "center" }}>
                            <button style={{ padding: "8px 14px", borderRadius: 8, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 12, fontWeight: 600, cursor: "pointer" }}
                              onClick={async e => {
                                e.stopPropagation();
                                try {
                                  await api.post(`/api/policies/${p.id}/bookmark`);
                                  setBookmarks(prev => prev.filter(b => b.id !== p.id));
                                } catch {
                                  showToast("북마크 해제에 실패했습니다", "error");
                                }
                              }}>
                              해제
                            </button>
                            <button style={{ padding: "8px 14px", borderRadius: 8, border: 0, background: A, color: WHITE, fontSize: 12, fontWeight: 700, cursor: "pointer" }}
                              onClick={e => { e.stopPropagation(); navigateToPolicyDetail(p.id); }}>
                              상세 →
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                  )
                ) : (
                  <div style={{ textAlign: "center", padding: "60px 0", color: INK3 }}>
                    <div style={{ fontSize: 16, fontWeight: 700, color: INK, marginBottom: 6 }}>북마크한 정책이 없어요</div>
                    <div style={{ fontSize: 13 }}>관심 정책을 저장해보세요</div>
                    <button onClick={() => navigate("/policies")} style={{ marginTop: 16, padding: "10px 24px", borderRadius: 8, background: A, color: WHITE, border: 0, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                      정책 둘러보기
                    </button>
                  </div>
                )}
              </SectionCard>
            )}

            {/* ── 알림 설정 ── */}
            {activeTab === "noti" && (() => {
              const NotiRow = ({ title, desc, on, onChange }) => (
                <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "16px 0", borderBottom: `1px solid ${LINE2}` }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: INK }}>{title}</div>
                    <div style={{ fontSize: 12, color: INK3, marginTop: 2 }}>{desc}</div>
                  </div>
                  <Toggle on={on} onChange={onChange} />
                </div>
              );
              return (
                <>
                  <SectionCard title="알림함" desc="최근 도착한 추천 알림을 앱 안에서 다시 확인할 수 있어요">
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
                      <div style={{ fontSize: 13, color: INK3 }}>
                        읽지 않은 알림 <strong style={{ color: alertUnreadCount > 0 ? WARN : INK2 }}>{alertUnreadCount}</strong>개
                      </div>
                      <button
                        onClick={() => {
                          setAlertsLoaded(false);
                          syncAlertsAfterAction().catch(() => showToast("알림함 새로고침에 실패했습니다", "error"));
                        }}
                        style={{ padding: "8px 12px", borderRadius: 10, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 12, fontWeight: 700, cursor: "pointer" }}
                      >
                        새로고침
                      </button>
                    </div>

                    {alertsLoading ? (
                      <div style={{ display: "flex", justifyContent: "center", padding: "30px 0" }}>
                        <CircularProgress size={24} />
                      </div>
                    ) : alerts.length > 0 ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                        {alerts.map((alert) => {
                          const unread = alert.status === "UNREAD";
                          const loading = alertActionLoadingId === alert.id;
                          return (
                            <div key={alert.id} style={{
                              border: `1px solid ${unread ? "#bfdbfe" : LINE}`,
                              background: unread ? "#f8fbff" : WHITE,
                              borderRadius: 16,
                              padding: 18,
                              boxShadow: unread ? "0 8px 18px rgba(37,99,235,0.08)" : "0 1px 2px rgba(20,30,80,0.03)",
                            }}>
                              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, marginBottom: 8 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                                  <span style={{
                                    padding: "4px 10px",
                                    borderRadius: 99,
                                    fontSize: 11,
                                    fontWeight: 700,
                                    background: unread ? "#dbeafe" : BG,
                                    color: unread ? AI : INK2,
                                  }}>
                                    {alert.kind === "RECOMMENDATION_DIGEST" ? "추천 알림" : alert.kind}
                                  </span>
                                  {unread && (
                                    <span style={{ color: WARN, fontSize: 11, fontWeight: 800 }}>NEW</span>
                                  )}
                                </div>
                                <div style={{ fontSize: 12, color: INK3 }}>{formatAlertTime(alert.createdAt)}</div>
                              </div>

                              <div style={{ fontSize: 15, fontWeight: 800, color: INK, marginBottom: 6 }}>{alert.title}</div>
                              {alert.body && (
                                <div style={{ fontSize: 13, color: INK2, lineHeight: 1.65, marginBottom: 14 }}>{alert.body}</div>
                              )}

                              <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                                <button
                                  onClick={() => openAlert(alert)}
                                  disabled={loading}
                                  style={{
                                    padding: "9px 14px",
                                    borderRadius: 10,
                                    border: 0,
                                    background: A,
                                    color: WHITE,
                                    fontSize: 12,
                                    fontWeight: 700,
                                    cursor: loading ? "wait" : "pointer",
                                    opacity: loading ? 0.75 : 1,
                                  }}
                                >
                                  열기
                                </button>
                                {unread && (
                                  <button
                                    onClick={() => markAlertRead(alert.id)}
                                    disabled={loading}
                                    style={{
                                      padding: "9px 14px",
                                      borderRadius: 10,
                                      border: `1px solid ${LINE}`,
                                      background: WHITE,
                                      color: INK2,
                                      fontSize: 12,
                                      fontWeight: 700,
                                      cursor: loading ? "wait" : "pointer",
                                      opacity: loading ? 0.75 : 1,
                                    }}
                                  >
                                    읽음
                                  </button>
                                )}
                                <button
                                  onClick={() => hideAlert(alert.id)}
                                  disabled={loading}
                                  style={{
                                    padding: "9px 14px",
                                    borderRadius: 10,
                                    border: `1px solid ${LINE}`,
                                    background: WHITE,
                                    color: INK3,
                                    fontSize: 12,
                                    fontWeight: 700,
                                    cursor: loading ? "wait" : "pointer",
                                    opacity: loading ? 0.75 : 1,
                                  }}
                                >
                                  숨기기
                                </button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <div style={{ textAlign: "center", padding: "32px 0 20px", color: INK3 }}>
                        <div style={{ fontSize: 16, fontWeight: 700, color: INK, marginBottom: 6 }}>도착한 알림이 아직 없어요</div>
                        <div style={{ fontSize: 13 }}>추천 digest가 생성되면 이곳에서 다시 확인할 수 있어요</div>
                      </div>
                    )}
                  </SectionCard>

                  <SectionCard title="수신 채널" desc="어떤 방법으로 알림을 받을지 선택하세요">
                    <NotiRow title="이메일 수신" desc={`${user?.email || myInfo.email || "이메일"} 으로 발송`} on={notifOn} onChange={() => setNotifOn(v => !v)} />
                  </SectionCard>

                  <SectionCard title="브라우저 푸시" desc="현재 브라우저를 연결하면 새 추천 알림을 즉시 받을 준비를 할 수 있어요">
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, marginBottom: 14 }}>
                      <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                        <div style={{ fontSize: 14, fontWeight: 700, color: INK }}>
                          현재 브라우저 상태:{" "}
                          <span style={{ color: browserPushConnected ? "#166534" : pushPermission === "denied" ? "#b91c1c" : INK2 }}>
                            {browserPushConnected ? "연결됨" : pushPermission === "denied" ? "권한 거부" : "미연결"}
                          </span>
                        </div>
                        <div style={{ fontSize: 12, color: INK3 }}>
                          {pushSupported
                            ? `브라우저 권한: ${pushPermission}`
                            : "이 브라우저/환경에서는 웹푸시를 지원하지 않습니다."}
                        </div>
                      </div>
                      <div style={{ display: "flex", gap: 8 }}>
                        {browserPushConnected ? (
                          <button
                            onClick={handleDisconnectBrowserPush}
                            disabled={pushActionLoading}
                            style={{ padding: "9px 14px", borderRadius: 10, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 12, fontWeight: 700, cursor: pushActionLoading ? "wait" : "pointer", opacity: pushActionLoading ? 0.75 : 1 }}
                          >
                            연결 해제
                          </button>
                        ) : (
                          <button
                            onClick={handleConnectBrowserPush}
                            disabled={pushConnectDisabled}
                            style={{ padding: "9px 14px", borderRadius: 10, border: 0, background: A, color: WHITE, fontSize: 12, fontWeight: 700, cursor: pushConnectDisabled ? "not-allowed" : "pointer", opacity: pushConnectDisabled ? 0.7 : 1 }}
                          >
                            {pushPermission === "denied" ? "권한 허용 필요" : "현재 브라우저 연결"}
                          </button>
                        )}
                      </div>
                    </div>

                    {pushStatusError && (
                      <div style={{ padding: 12, borderRadius: 12, background: "#fef2f2", color: "#b91c1c", fontSize: 12, lineHeight: 1.6, marginBottom: 12 }}>
                        {pushStatusError}
                      </div>
                    )}

                    {!pushStatusError && pushActionError && (
                      <div style={{ padding: 12, borderRadius: 12, background: "#fef2f2", color: "#b91c1c", fontSize: 12, lineHeight: 1.6, marginBottom: 12 }}>
                        {pushActionError}
                      </div>
                    )}

                    {!pushStatusError && pushPermission === "denied" && (
                      <div style={{ padding: 12, borderRadius: 12, background: "#fff7ed", color: "#c2410c", fontSize: 12, lineHeight: 1.6, marginBottom: 12 }}>
                        브라우저 알림 권한이 거부된 상태입니다. 주소창 옆 브라우저 권한 설정에서 알림을 허용한 뒤 다시 시도해주세요.
                      </div>
                    )}

                    <div style={{ padding: 14, borderRadius: 12, background: BG, color: INK2, fontSize: 12, lineHeight: 1.65, marginBottom: 14 }}>
                      실제 웹푸시 발송은 아직 열지 않았습니다. 지금 단계에서는 브라우저 권한과 구독 저장만 준비합니다.
                    </div>

                    {pushLoading ? (
                      <div style={{ display: "flex", justifyContent: "center", padding: "20px 0" }}>
                        <CircularProgress size={22} />
                      </div>
                    ) : pushSubscriptions.length > 0 ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                        {pushSubscriptions.map((subscription) => {
                          const isCurrentBrowser = subscription.endpoint === currentPushEndpoint;
                          return (
                            <div key={subscription.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "14px 16px", border: `1px solid ${LINE}`, borderRadius: 12, background: WHITE }}>
                              <div style={{ minWidth: 0 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 4 }}>
                                  <span style={{ fontSize: 13, fontWeight: 700, color: INK }}>
                                    {subscription.deviceLabel || "브라우저 구독"}
                                  </span>
                                  {isCurrentBrowser && (
                                    <span style={{ padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 800, background: "#dbeafe", color: AI }}>
                                      현재 브라우저
                                    </span>
                                  )}
                                </div>
                                <div style={{ fontSize: 12, color: INK3, overflow: "hidden", textOverflow: "ellipsis" }}>
                                  {subscription.endpoint}
                                </div>
                              </div>
                              <button
                                onClick={() => handleRemovePushSubscription(subscription.id)}
                                disabled={pushActionLoading}
                                style={{ padding: "8px 12px", borderRadius: 10, border: `1px solid ${LINE}`, background: WHITE, color: INK3, fontSize: 12, fontWeight: 700, cursor: pushActionLoading ? "wait" : "pointer", whiteSpace: "nowrap" }}
                              >
                                제거
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <div style={{ textAlign: "center", padding: "24px 0 8px", color: INK3 }}>
                        <div style={{ fontSize: 14, fontWeight: 700, color: INK, marginBottom: 6 }}>등록된 브라우저 푸시가 아직 없어요</div>
                        <div style={{ fontSize: 12 }}>현재 브라우저 연결 버튼으로 구독을 먼저 저장할 수 있습니다.</div>
                      </div>
                    )}
                  </SectionCard>

                  <SectionCard title="알림 기준" desc="추천 점수와 발송 개수를 조절할 수 있어요">
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                      <Field label="최소 추천 점수">
                        <select value={notifMinScore} onChange={(e) => setNotifMinScore(parseFloat(e.target.value))} style={selCss(false)}>
                          {NOTIFICATION_SCORE_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                      </Field>
                      <Field label="한 번에 받을 정책 수">
                        <select value={notifDisplayCount} onChange={(e) => setNotifDisplayCount(parseInt(e.target.value, 10))} style={selCss(false)}>
                          {DISPLAY_COUNT_OPTIONS.map((count) => (
                            <option key={count} value={count}>{count}개</option>
                          ))}
                        </select>
                      </Field>
                    </div>
                  </SectionCard>

                  <SectionCard>
                    <div style={{ padding: 16, background: AS, borderRadius: 12, fontSize: 13, color: INK2, lineHeight: 1.6 }}>
                      현재 알림 설정은 이메일 수신 여부, 발송 주기, 최소 추천 점수, 발송 개수를 저장합니다. 인앱 알림함은 추천 digest가 생성되면 자동으로 기록되고, 웹푸시는 현재 브라우저 구독 저장까지만 지원합니다.
                      {notificationConsentAt && (
                        <div style={{ marginTop: 8, color: INK3 }}>
                          최근 수신 동의 시각: {formatConsentDateTime(notificationConsentAt)}
                        </div>
                      )}
                    </div>
                  </SectionCard>

                  <SaveBar onSave={handleSaveNotif} loading={notifLoading} />
                </>
              );
            })()}

            {/* ── 필터 기본값 ── */}
            {activeTab === "filter" && (
              <>
                <SectionCard title="기본 표시 설정" desc="현재는 종료된 정책 포함 여부만 로컬에 저장됩니다">
                  <div style={{ display: "flex", alignItems: "center", padding: "14px 0", borderBottom: `1px solid ${LINE2}` }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 14, fontWeight: 700, color: INK }}>종료된 정책 보기</div>
                      <div style={{ fontSize: 12, color: INK3, marginTop: 2 }}>OFF로 두면 진행 중인 정책만 보여요</div>
                    </div>
                    <Toggle on={filterIncludeExpired} onChange={() => setFilterIncludeExpired(v => !v)} />
                  </div>
                </SectionCard>

                <SaveBar
                  onSave={() => { setFilterSettings({ includeExpired: filterIncludeExpired }); showToast("기본값이 저장되었습니다"); }}
                  note="저장된 설정은 다음 접속부터 적용돼요"
                />
              </>
            )}

            {/* ── 계정 ── */}
            {activeTab === "account" && (
              <>
                <SectionCard title="비밀번호 변경" desc="안전한 비밀번호는 영문 대/소문자, 숫자, 특수문자 조합 8자 이상이에요">
                  <Field label="현재 비밀번호">
                    <input style={iCss(false, false)} type="password" value={currPw} onChange={e => setCurrPw(e.target.value)} placeholder="••••••••" />
                  </Field>
                  <Field label="새 비밀번호">
                    <input style={iCss(false, false)} type="password" value={newPw} onChange={e => setNewPw(e.target.value)} placeholder="새 비밀번호 입력" />
                  </Field>
                  <Field label="새 비밀번호 확인" error={newPwConfirm.length > 0 && newPw !== newPwConfirm ? "비밀번호가 일치하지 않습니다" : ""}>
                    <input style={iCss(newPwConfirm.length > 0 && newPw !== newPwConfirm, false)} type="password" value={newPwConfirm} onChange={e => setNewPwConfirm(e.target.value)} placeholder="다시 한 번 입력" />
                  </Field>
                  <button onClick={handlePasswordChange} disabled={pwLoading} style={{
                    width: "100%", padding: 14, fontSize: 14, fontWeight: 700,
                    background: A, color: WHITE, border: 0, borderRadius: 10, cursor: "pointer",
                    display: "flex", alignItems: "center", justifyContent: "center", gap: 8, marginTop: 4,
                  }}>
                    {pwLoading ? <CircularProgress size={18} sx={{ color: WHITE }} /> : "비밀번호 변경"}
                  </button>
                </SectionCard>

                <SectionCard title="연결된 계정" desc="현재는 연결 상태만 안내하며, 소셜 계정 연동 기능은 준비 중이에요">
                  {[
                    { ico: "G", n: "Google", s: "연결 안 됨", bg: "#fff", color: INK2, linked: false },
                    { ico: "N", n: "네이버", s: "연결 안 됨", bg: "#03c75a", color: "#fff", linked: false },
                    { ico: "💬", n: "카카오", s: "연결 안 됨", bg: "#fee500", color: "#000", linked: false },
                  ].map((p, i) => (
                    <div key={i} style={{ display: "flex", alignItems: "center", gap: 14, padding: "16px 0", borderBottom: `1px solid ${LINE2}` }}>
                      <div style={{ width: 40, height: 40, borderRadius: 10, background: p.bg, border: `1px solid ${LINE}`, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 15, color: p.color, flexShrink: 0 }}>{p.ico}</div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 14, fontWeight: 700, color: INK }}>{p.n}</div>
                        <div style={{ fontSize: 12, color: p.linked ? "#047857" : INK3, marginTop: 2 }}>{p.linked && "✓ "}{p.s}</div>
                      </div>
                      <button
                        disabled
                        aria-disabled="true"
                        style={{
                          padding: "8px 14px",
                          borderRadius: 8,
                          border: `1px solid ${LINE}`,
                          background: LINE2,
                          color: INK3,
                          fontSize: 12,
                          fontWeight: 600,
                          cursor: "not-allowed",
                        }}
                      >
                        준비 중
                      </button>
                    </div>
                  ))}
                </SectionCard>

                <SectionCard title="회원탈퇴">
                  <div style={{ padding: 18, background: "#fef2f2", border: "1px solid #fecaca", borderRadius: 12, marginBottom: 14 }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: WARN, marginBottom: 6 }}>⚠ 탈퇴 시 다음 정보가 모두 삭제됩니다</div>
                    <ul style={{ margin: 0, padding: "0 0 0 20px", fontSize: 13, color: INK2, lineHeight: 1.7 }}>
                      <li>프로필·우선순위·필터 설정</li>
                      <li>북마크한 정책 {bookmarks.length}건</li>
                      <li>알림 수신 이력 및 구독 정보</li>
                    </ul>
                    <div style={{ fontSize: 12, color: INK3, marginTop: 8 }}>삭제된 정보는 복구할 수 없어요</div>
                  </div>
                  <button onClick={() => setWithdrawModal(true)} style={{
                    width: "100%", padding: 14, fontSize: 14, fontWeight: 700,
                    background: WHITE, border: `1.5px solid ${WARN}`, color: WARN,
                    borderRadius: 10, cursor: "pointer",
                  }}>
                    회원탈퇴
                  </button>
                </SectionCard>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Modals */}
      <IncomeCalculatorModal open={incomeCalcOpen} onClose={() => setIncomeCalcOpen(false)}
        onSelect={value => { setMyInfo(prev => ({ ...prev, income: value })); setEditing(true); }} />

      <Dialog open={withdrawModal} onClose={() => { setWithdrawModal(false); setWithdrawPw(""); }} maxWidth="xs" fullWidth>
        <DialogTitle>정말 탈퇴하시겠습니까?</DialogTitle>
        <DialogContent>
          <p style={{ fontSize: 14, color: INK2, marginBottom: 16 }}>탈퇴 시 북마크, 추천 기록이 모두 삭제됩니다</p>
          <input type="password" placeholder="비밀번호 확인" value={withdrawPw} onChange={e => setWithdrawPw(e.target.value)} onKeyDown={e => e.key === "Enter" && handleWithdraw()}
            style={{ ...iCss(false, false), marginTop: 4 }} autoFocus />
        </DialogContent>
        <DialogActions>
          <button onClick={() => { setWithdrawModal(false); setWithdrawPw(""); }} style={{ padding: "8px 16px", background: WHITE, border: `1px solid ${LINE}`, borderRadius: 8, cursor: "pointer", fontWeight: 600 }}>취소</button>
          <button onClick={handleWithdraw} style={{ padding: "8px 16px", background: WARN, color: WHITE, border: 0, borderRadius: 8, fontWeight: 700, cursor: "pointer" }}>탈퇴하기</button>
        </DialogActions>
      </Dialog>

      <Snackbar open={toast.open} autoHideDuration={2500} onClose={() => setToast(t => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
      <FloatingNav />
    </div>
  );
}
