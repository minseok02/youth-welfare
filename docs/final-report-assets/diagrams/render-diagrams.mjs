import playwright from "../../../frontend/node_modules/playwright/index.js";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const { chromium } = playwright;
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = __dirname;

const diagrams = [
  {
    file: "figure-01-system-architecture.png",
    title: "전체 시스템 아키텍처",
    subtitle: "React 프론트엔드와 Spring Boot API 서버를 중심으로 정책 검색, 추천, 챗봇, 알림, 관리자 기능을 통합한다.",
    groups: [
      {
        title: "사용자 계층",
        nodes: ["사용자 브라우저", "관리자 브라우저"],
      },
      {
        title: "서비스 계층",
        nodes: ["React / Vite Frontend", "nginx", "Spring Boot API"],
      },
      {
        title: "데이터 계층",
        nodes: ["PostgreSQL 16 + pgvector", "Redis / ElastiCache Valkey"],
      },
      {
        title: "외부 연동",
        nodes: ["온통청년 API", "복지로 API", "Gov24 API", "OpenAI API", "SMTP / Web Push"],
      },
    ],
  },
  {
    file: "figure-02-policy-data-integration.png",
    title: "정책 데이터 통합 구조",
    subtitle: "출처별 공공 API 응답을 내부 정책 모델로 정규화하여 검색, 추천, 챗봇, 관리자 기능에서 공통 활용한다.",
    groups: [
      {
        title: "수집 출처",
        nodes: ["온통청년", "복지로 중앙", "복지로 지자체", "Gov24"],
      },
      {
        title: "수집/보존",
        nodes: ["API Client", "Raw Payload 저장", "수집 로그"],
      },
      {
        title: "정규화 저장",
        nodes: ["welfare_services", "welfare_service_details", "service_regions", "service_tags", "service_facts"],
      },
      {
        title: "서비스 활용",
        nodes: ["정책 검색", "맞춤 추천", "챗봇 상담", "관리자 품질 관리"],
      },
    ],
  },
  {
    file: "figure-03-recommendation-pipeline.png",
    title: "AI 개인화 추천 파이프라인",
    subtitle: "명확한 조건은 룰 기반으로 처리하고, OpenAI API는 상위 후보의 보조 평가와 추천 사유 생성을 담당한다.",
    groups: [
      {
        title: "입력",
        nodes: ["사용자 프로필", "우선순위", "정책 데이터"],
      },
      {
        title: "후보/룰 점수",
        nodes: ["후보 정책 추출", "룰 기반 점수", "우선순위 가중치"],
      },
      {
        title: "AI 보조 평가",
        nodes: ["상위 후보 선별", "OpenAI 점수", "추천 사유 생성", "rule-only fallback"],
      },
      {
        title: "결과",
        nodes: ["final_score 계산", "추천 결과 저장", "사용자 추천 화면"],
      },
    ],
  },
  {
    file: "figure-04-chatbot-structure.png",
    title: "챗봇 기반 정책 상담 구조",
    subtitle: "사용자 질문을 정책 후보 검색과 evidence 기반 답변 생성 흐름으로 처리하여 임의 답변 위험을 줄인다.",
    groups: [
      {
        title: "질문 처리",
        nodes: ["사용자 질문", "민감정보 정제", "최근 대화 반영"],
      },
      {
        title: "검색",
        nodes: ["키워드/의미 검색", "정책 후보 구성", "상세 evidence 구성"],
      },
      {
        title: "답변 생성",
        nodes: ["OpenAI JSON 응답", "후보 정책 allowlist", "fallback 답변"],
      },
      {
        title: "응답",
        nodes: ["상담 답변", "관련 정책 참조", "신청 준비 코칭"],
      },
    ],
  },
  {
    file: "figure-05-user-service-flow.png",
    title: "사용자 서비스 흐름",
    subtitle: "정책 검색에서 상세 조회, 프로필 설정, 맞춤 추천, 챗봇 상담, 알림 확인으로 이어지는 사용자 경험을 제공한다.",
    groups: [
      {
        title: "탐색",
        nodes: ["서비스 접속", "정책 검색/필터", "정책 상세 조회"],
      },
      {
        title: "개인화",
        nodes: ["회원가입/로그인", "프로필 설정", "우선순위 설정"],
      },
      {
        title: "활용",
        nodes: ["맞춤 추천", "북마크", "AI와 신청 준비하기"],
      },
      {
        title: "재방문",
        nodes: ["인앱 알림", "웹푸시", "알림함 확인"],
      },
    ],
  },
  {
    file: "figure-06-admin-operation-flow.png",
    title: "관리자 운영 흐름",
    subtitle: "관리자는 정책 데이터 품질과 서비스 상태를 확인하고 수집, 오류 제보, 중복 후보, 링크, 알림 상태를 관리한다.",
    groups: [
      {
        title: "대시보드",
        nodes: ["관리자 로그인", "운영 요약", "주의 항목 확인"],
      },
      {
        title: "정책 품질",
        nodes: ["오류 제보", "중복 후보", "링크 검토", "정책 보정 이력"],
      },
      {
        title: "수집/추천",
        nodes: ["수집 상태", "수집 실패/partial", "추천 실행 요약", "추천 분포"],
      },
      {
        title: "알림/조치",
        nodes: ["알림 상태", "실패/적체 확인", "운영 조치"],
      },
    ],
  },
  {
    file: "figure-07-deployment-topology.png",
    title: "배포 및 운영 구조",
    subtitle: "제출 및 시연 기준으로 ALB 뒤 EC2 2대가 요청을 분산 처리하고, RDS와 ElastiCache를 공통 저장소로 사용한다.",
    groups: [
      {
        title: "외부 진입",
        nodes: ["사용자", "Route 53 / 도메인", "Application Load Balancer"],
      },
      {
        title: "웹 노드",
        nodes: ["EC2-1 nginx + app", "EC2-2 nginx + app", "Scheduler: EC2-1 활성"],
      },
      {
        title: "공통 저장소",
        nodes: ["RDS PostgreSQL", "ElastiCache Valkey"],
      },
      {
        title: "운영 연동",
        nodes: ["공공 API 수집", "OpenAI API", "SMTP / Web Push", "Health Check"],
      },
    ],
  },
];

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function renderDiagram(diagram) {
  const groups = diagram.groups
    .map((group, groupIndex) => {
      const nodes = group.nodes
        .map((node) => `<div class="node">${escapeHtml(node)}</div>`)
        .join("");
      const arrow = groupIndex < diagram.groups.length - 1 ? `<div class="arrow">→</div>` : "";
      return `
        <section class="group">
          <h2>${escapeHtml(group.title)}</h2>
          <div class="nodes">${nodes}</div>
        </section>
        ${arrow}
      `;
    })
    .join("");

  return `
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      background: #ffffff;
      color: #18212f;
      font-family: "Noto Sans CJK KR", "Noto Sans KR", FreeSans, Arial, sans-serif;
    }
    .canvas {
      width: 1600px;
      height: 900px;
      padding: 58px 64px 54px;
      background: linear-gradient(180deg, #ffffff 0%, #f7fafc 100%);
      border: 1px solid #d8e0ea;
    }
    .header {
      border-left: 12px solid #2563eb;
      padding-left: 22px;
      margin-bottom: 44px;
    }
    h1 {
      margin: 0 0 12px;
      font-size: 44px;
      font-weight: 800;
      letter-spacing: 0;
      color: #111827;
    }
    .subtitle {
      font-size: 22px;
      line-height: 1.48;
      color: #4b5563;
      max-width: 1380px;
    }
    .flow {
      display: grid;
      grid-template-columns: 1fr 70px 1fr 70px 1fr 70px 1fr;
      align-items: stretch;
      gap: 0;
      height: 635px;
    }
    .group {
      border: 2px solid #cbd5e1;
      background: #ffffff;
      border-radius: 18px;
      padding: 28px 24px;
      box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .group h2 {
      margin: 0 0 24px;
      font-size: 28px;
      line-height: 1.25;
      color: #1d4ed8;
      font-weight: 800;
      text-align: center;
      letter-spacing: 0;
    }
    .nodes {
      display: flex;
      flex-direction: column;
      gap: 16px;
      justify-content: center;
      flex: 1;
    }
    .node {
      min-height: 70px;
      border: 2px solid #dbe4ef;
      background: #f8fafc;
      border-radius: 12px;
      padding: 14px 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      text-align: center;
      font-size: 21px;
      line-height: 1.32;
      font-weight: 700;
      color: #1f2937;
    }
    .arrow {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 46px;
      color: #64748b;
      font-weight: 800;
    }
    .footer {
      margin-top: 26px;
      display: flex;
      justify-content: flex-end;
      color: #64748b;
      font-size: 18px;
    }
  </style>
</head>
<body>
  <main class="canvas">
    <div class="header">
      <h1>${escapeHtml(diagram.title)}</h1>
      <div class="subtitle">${escapeHtml(diagram.subtitle)}</div>
    </div>
    <div class="flow">${groups}</div>
    <div class="footer">YouthMoa 청년 복지 통합 플랫폼</div>
  </main>
</body>
</html>`;
}

await fs.writeFile(path.join(outDir, "README.md"), `# 결과보고서 도표 이미지

이 폴더는 결과보고서에 삽입할 도표 PNG를 보관한다.

재생성:

\`\`\`bash
node docs/final-report-assets/diagrams/render-diagrams.mjs
\`\`\`
`);

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 2 });

for (const diagram of diagrams) {
  await page.setContent(renderDiagram(diagram), { waitUntil: "load" });
  const target = path.join(outDir, diagram.file);
  await page.screenshot({ path: target, fullPage: false });
  console.log(`wrote ${target}`);
}

await browser.close();
