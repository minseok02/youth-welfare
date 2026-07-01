// 카테고리용 커스텀 라인 아이콘 (이모지 대신). 24x24 viewBox, stroke 기반이라 색/크기 자유.
// name: job | housing | education | finance | health | culture | participation | family | deadline
const PATHS = {
  job: (
    <>
      <rect x="3" y="7.5" width="18" height="12" rx="2" />
      <path d="M8 7.5V6a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1.5" />
      <path d="M3 12.5h18" />
    </>
  ),
  housing: (
    <>
      <path d="M4 11.5 12 4l8 7.5" />
      <path d="M6 10.5V20h12v-9.5" />
      <path d="M10 20v-5h4v5" />
    </>
  ),
  education: (
    <>
      <path d="M12 4 2.5 9 12 14l9.5-5z" />
      <path d="M6.5 11v4.2c0 1.3 2.5 2.3 5.5 2.3s5.5-1 5.5-2.3V11" />
      <path d="M21.5 9v4.5" />
    </>
  ),
  finance: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8 9l1.7 6 2.3-5 2.3 5L16 9" />
      <path d="M7.4 12.2h9.2" />
    </>
  ),
  health: (
    <path d="M12 20.5C6.5 16.5 4 13 4 9.8 4 7.4 5.9 5.5 8.3 5.5c1.5 0 2.9.8 3.7 2 .8-1.2 2.2-2 3.7-2 2.4 0 4.3 1.9 4.3 4.3 0 3.2-2.5 6.7-8 10.7z" />
  ),
  culture: (
    <>
      <path d="M4 8.5A1.5 1.5 0 0 1 5.5 7h13A1.5 1.5 0 0 1 20 8.5a2 2 0 0 0 0 4 1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 12.5a2 2 0 0 0 0-4z" />
      <path d="M13 7.5v9" strokeDasharray="2 2" />
    </>
  ),
  participation: (
    <>
      <circle cx="9" cy="8.5" r="2.6" />
      <path d="M4 19c0-3.3 2.2-5.4 5-5.4s5 2.1 5 5.4" />
      <circle cx="16.6" cy="8" r="2" />
      <path d="M15.6 13.9c2 .5 3.4 2.4 3.4 5.1" />
    </>
  ),
  family: (
    <>
      <circle cx="8.5" cy="8" r="2.4" />
      <path d="M4 19c0-3 2-5 4.5-5s4.5 2 4.5 5" />
      <circle cx="16.6" cy="10.6" r="1.8" />
      <path d="M13.6 19c0-2.2 1.3-3.6 3-3.6s3 1.4 3 3.6" />
    </>
  ),
  deadline: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3.2 2" />
    </>
  ),
  // 마이페이지 메뉴/섹션용
  person: (
    <>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M5.5 19.5c0-3.6 2.9-6.3 6.5-6.3s6.5 2.7 6.5 6.3" />
    </>
  ),
  star: (
    <path d="M12 3.5l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z" />
  ),
  bookmark: (
    <path d="M6.5 4h11v16l-5.5-3.8L6.5 20z" />
  ),
  bell: (
    <>
      <path d="M6 9.5a6 6 0 0 1 12 0c0 4.5 1.8 5.8 1.8 5.8H4.2S6 14 6 9.5z" />
      <path d="M10 19a2 2 0 0 0 4 0" />
    </>
  ),
  filter: (
    <>
      <path d="M4 7h2.8" />
      <circle cx="9" cy="7" r="2.2" />
      <path d="M11.2 7H20" />
      <path d="M4 12h8.8" />
      <circle cx="15" cy="12" r="2.2" />
      <path d="M17.2 12H20" />
      <path d="M4 17h4.8" />
      <circle cx="11" cy="17" r="2.2" />
      <path d="M13.2 17H20" />
    </>
  ),
  lock: (
    <>
      <rect x="5.5" y="10.5" width="13" height="9" rx="2" />
      <path d="M8.5 10.5V7.5a3.5 3.5 0 0 1 7 0v3" />
      <circle cx="12" cy="14.5" r="1.3" />
      <path d="M12 15.8v1.8" />
    </>
  ),
};

export default function CategoryIcon({ name, size = 24, color = "currentColor", strokeWidth = 1.8 }) {
  const paths = PATHS[name];
  if (!paths) return null;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths}
    </svg>
  );
}
