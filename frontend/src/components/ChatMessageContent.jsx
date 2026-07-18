import { Link, Typography } from "@mui/material";
import { parseChatMessageSegments } from "../lib/chatDisplay";

// 챗봇 답변 본문 렌더러.
// - 마크다운 링크 [텍스트](url) → 클릭 가능한 링크(안전 URL만, 새 탭)
// - 내부 마스킹 토큰([REDACTED_*])은 파서 단계에서 제거됨
// dangerouslySetInnerHTML을 쓰지 않고 React 엘리먼트로만 구성한다(XSS 방지).
export default function ChatMessageContent({ text }) {
  const segments = parseChatMessageSegments(text);

  return (
    <Typography component="div" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.7 }}>
      {segments.map((segment, index) =>
        segment.type === "link" ? (
          <Link
            key={index}
            href={segment.href}
            target="_blank"
            rel="noopener noreferrer"
            underline="always"
          >
            {segment.text}
          </Link>
        ) : (
          <span key={index}>{segment.value}</span>
        )
      )}
    </Typography>
  );
}
