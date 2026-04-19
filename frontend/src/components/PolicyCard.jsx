const C = {
  teal1: "#028090",
  teal2: "#00A896",
  white: "#FFFFFF",
  lightgray: "#E8F4F5",
  gray: "#64748B",
  dark: "#1A2E35",
};

export default function PolicyCard({ policy }) {
  const ddayColor =
    policy.dday === "상시"
      ? C.teal2
      : parseInt(policy.dday.replace("D-", "")) <= 14
      ? "#E53E3E"
      : C.teal1;

  return (
    <div
      style={{
        background: C.white,
        borderRadius: 14,
        padding: "20px 22px",
        boxShadow: "0 2px 12px rgba(2,128,144,0.08)",
        border: `1px solid ${C.lightgray}`,
        transition: "box-shadow 0.2s, transform 0.2s",
        cursor: "pointer",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.boxShadow = "0 6px 24px rgba(2,128,144,0.16)";
        e.currentTarget.style.transform = "translateY(-2px)";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = "0 2px 12px rgba(2,128,144,0.08)";
        e.currentTarget.style.transform = "translateY(0)";
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
        <span style={{ background: C.lightgray, color: C.teal1, fontSize: 11, fontWeight: 700, padding: "3px 9px", borderRadius: 20 }}>
          {policy.category}
        </span>
        <span style={{ color: ddayColor, fontSize: 12, fontWeight: 700 }}>
          {policy.dday}
        </span>
      </div>

      <h3 style={{ margin: "0 0 8px", fontSize: 15, fontWeight: 700, color: C.dark, lineHeight: 1.4 }}>
        {policy.title}
      </h3>

      <p style={{ margin: "0 0 14px", fontSize: 13, color: C.gray, lineHeight: 1.6 }}>
        {policy.summary}
      </p>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontSize: 11, color: C.gray }}>출처: {policy.source}</span>
        <span style={{ color: C.teal2, fontSize: 12, fontWeight: 600 }}>자세히 보기 →</span>
      </div>
    </div>
  );
}