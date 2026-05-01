import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import AddCommentOutlinedIcon from "@mui/icons-material/AddCommentOutlined";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import ForumOutlinedIcon from "@mui/icons-material/ForumOutlined";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import Header from "../components/Header";
import api from "../lib/axios";

const SUGGESTED_PROMPTS = [
  "서울에서 월세 지원 받을 수 있는 청년 정책이 궁금해요.",
  "취업 준비 중인데 교육비나 훈련비 지원이 있나요?",
  "지금 바로 신청 가능한 생활비 지원 정책을 알려주세요.",
];

const formatSessionTitle = (title) => title?.trim() || "새 대화";

const formatRelativeTime = (dateText) => {
  if (!dateText) {
    return "";
  }

  const value = new Date(dateText);
  if (Number.isNaN(value.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(value);
};

const formatMessageTime = (dateText) => {
  if (!dateText) {
    return "방금 전";
  }

  const value = new Date(dateText);
  if (Number.isNaN(value.getTime())) {
    return "방금 전";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    hour: "numeric",
    minute: "2-digit",
  }).format(value);
};

const mapSession = (session) => ({
  sessionId: session.sessionId,
  title: formatSessionTitle(session.title),
  lastMessageAt: session.lastMessageAt,
  createdAt: session.createdAt,
});

const mapMessage = (message) => ({
  messageId: message.messageId,
  role: message.role,
  content: message.content,
  referencedServiceIds: message.referencedServiceIds ?? [],
  createdAt: message.createdAt,
});

export default function ChatPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState([]);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [policyMeta, setPolicyMeta] = useState({});
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [creatingSession, setCreatingSession] = useState(false);
  const [sending, setSending] = useState(false);
  const [deletingSessionId, setDeletingSessionId] = useState(null);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const showToast = useCallback((msg, severity = "info") => {
    setToast({ open: true, msg, severity });
  }, []);

  const enrichPolicyMeta = useCallback(async (serviceIds) => {
    const uniqueIds = [...new Set(serviceIds)].filter((serviceId) => !policyMeta[serviceId]);
    if (!uniqueIds.length) {
      return;
    }

    const results = await Promise.allSettled(
      uniqueIds.map((serviceId) => api.get(`/api/policies/${serviceId}`))
    );

    const nextMeta = {};
    results.forEach((result, index) => {
      if (result.status !== "fulfilled") {
        return;
      }

      const payload = result.value?.data?.data;
      if (!payload) {
        return;
      }

      const serviceId = uniqueIds[index];
      nextMeta[serviceId] = {
        title: payload.title,
        description: payload.description || "상세 페이지에서 지원 조건과 신청 방법을 확인해보세요.",
      };
    });

    if (Object.keys(nextMeta).length) {
      setPolicyMeta((prev) => ({ ...prev, ...nextMeta }));
    }
  }, [policyMeta]);

  const loadSessions = useCallback(async (preferredSessionId = null) => {
    setLoadingSessions(true);
    try {
      const { data } = await api.get("/api/chat/sessions");
      const nextSessions = (data?.data ?? []).map(mapSession);
      setSessions(nextSessions);
      setActiveSessionId((current) => {
        const candidate = preferredSessionId ?? current;
        if (candidate && nextSessions.some((session) => session.sessionId === candidate)) {
          return candidate;
        }
        return nextSessions[0]?.sessionId ?? null;
      });
      if (!nextSessions.length) {
        setMessages([]);
      }
    } catch {
      setSessions([]);
      setMessages([]);
      showToast("대화 세션을 불러오지 못했습니다.", "error");
    } finally {
      setLoadingSessions(false);
    }
  }, [showToast]);

  const loadMessages = useCallback(async (sessionId) => {
    if (!sessionId) {
      setMessages([]);
      return;
    }

    setLoadingMessages(true);
    try {
      const { data } = await api.get(`/api/chat/sessions/${sessionId}/messages`);
      const nextMessages = (data?.data ?? []).map(mapMessage);
      setMessages(nextMessages);
      const serviceIds = nextMessages.flatMap((message) => message.referencedServiceIds);
      if (serviceIds.length) {
        await enrichPolicyMeta(serviceIds);
      }
    } catch (error) {
      setMessages([]);
      if (error.response?.data?.errorCode === "CH001") {
        showToast("선택한 대화 세션을 찾지 못했습니다. 목록을 새로고침했습니다.", "warning");
        await loadSessions();
      } else {
        showToast("대화 내용을 불러오지 못했습니다.", "error");
      }
    } finally {
      setLoadingMessages(false);
    }
  }, [enrichPolicyMeta, loadSessions, showToast]);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    void loadMessages(activeSessionId);
  }, [activeSessionId, loadMessages]);

  const activeSession = useMemo(
    () => sessions.find((session) => session.sessionId === activeSessionId) ?? null,
    [activeSessionId, sessions]
  );

  const createSession = useCallback(async () => {
    setCreatingSession(true);
    try {
      const { data } = await api.post("/api/chat/sessions");
      const createdSession = mapSession(data?.data ?? {});
      setSessions((prev) => [createdSession, ...prev.filter((session) => session.sessionId !== createdSession.sessionId)]);
      setActiveSessionId(createdSession.sessionId);
      setMessages([]);
      return createdSession.sessionId;
    } catch {
      showToast("새 대화를 시작하지 못했습니다.", "error");
      return null;
    } finally {
      setCreatingSession(false);
    }
  }, [showToast]);

  const handleDeleteSession = async (sessionId) => {
    setDeletingSessionId(sessionId);
    try {
      await api.delete(`/api/chat/sessions/${sessionId}`);
      setSessions((prev) => {
        const nextSessions = prev.filter((session) => session.sessionId !== sessionId);
        if (!nextSessions.length) {
          setActiveSessionId(null);
          setMessages([]);
          return [];
        }

        if (sessionId === activeSessionId) {
          setActiveSessionId(nextSessions[0].sessionId);
        }

        return nextSessions;
      });
      showToast("대화를 삭제했습니다.");
    } catch {
      showToast("대화를 삭제하지 못했습니다.", "error");
    } finally {
      setDeletingSessionId(null);
    }
  };

  const handleSend = async (event) => {
    event.preventDefault();
    const content = draft.trim();
    if (!content || sending) {
      return;
    }

    setDraft("");
    setSending(true);

    let sessionId = activeSessionId;
    if (!sessionId) {
      sessionId = await createSession();
    }

    if (!sessionId) {
      setDraft(content);
      setSending(false);
      return;
    }

    const tempUserId = `temp-user-${Date.now()}`;
    const tempAssistantId = `temp-assistant-${Date.now()}`;
    setMessages((prev) => [
      ...prev,
      {
        messageId: tempUserId,
        role: "USER",
        content,
        referencedServiceIds: [],
        createdAt: new Date().toISOString(),
      },
      {
        messageId: tempAssistantId,
        role: "ASSISTANT",
        content: "질문에 맞는 정책을 찾는 중입니다...",
        referencedServiceIds: [],
        createdAt: new Date().toISOString(),
      },
    ]);

    try {
      const { data } = await api.post(`/api/chat/sessions/${sessionId}/messages`, { content });
      const references = data?.data?.references ?? [];
      if (references.length) {
        setPolicyMeta((prev) => ({
          ...prev,
          ...Object.fromEntries(
            references.map((reference) => [
              reference.serviceId,
              {
                title: reference.title,
                description: reference.reason,
              },
            ])
          ),
        }));
      }

      await Promise.all([loadSessions(sessionId), loadMessages(sessionId)]);
    } catch (error) {
      setMessages((prev) => prev.filter(
        (message) => message.messageId !== tempUserId && message.messageId !== tempAssistantId
      ));
      setDraft(content);

      const errorCode = error.response?.data?.errorCode;
      if (errorCode === "CH002") {
        showToast("질문 전송이 너무 빠릅니다. 잠시 후 다시 시도해주세요.", "warning");
      } else if (errorCode === "CH001") {
        showToast("선택한 대화 세션을 찾지 못했습니다. 목록을 새로고침했습니다.", "warning");
        await loadSessions();
      } else {
        showToast("답변을 생성하지 못했습니다.", "error");
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      <Container maxWidth="xl" sx={{ py: { xs: 2, md: 3 } }}>
        <Stack spacing={2.5}>
          <Paper
            elevation={0}
            sx={{
              p: { xs: 2.5, md: 3 },
              borderRadius: 4,
              border: "1px solid #DCEEEE",
              background:
                "linear-gradient(135deg, rgba(2,128,144,0.14) 0%, rgba(255,255,255,0.94) 56%, rgba(0,168,150,0.12) 100%)",
            }}
          >
            <Stack
              direction={{ xs: "column", lg: "row" }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ xs: "flex-start", lg: "center" }}
            >
              <Box>
                <Chip
                  icon={<AutoAwesomeIcon />}
                  label="로그인 전용 청년 정책 상담"
                  color="primary"
                  variant="outlined"
                  sx={{ mb: 1.5, bgcolor: "rgba(255,255,255,0.72)" }}
                />
                <Typography variant="h4" fontWeight={800} sx={{ mb: 1 }}>
                  지금 조건에서 바로 볼 정책만 골라서 답합니다
                </Typography>
                <Typography color="text.secondary" sx={{ maxWidth: 760 }}>
                  질문을 보내면 정책 후보를 먼저 좁힌 뒤 답변합니다. 관련 정책은 카드로 연결되며,
                  너무 빠른 재요청은 자동으로 제한됩니다.
                </Typography>
              </Box>

              <Stack direction="row" spacing={1.25}>
                <Button
                  variant="outlined"
                  startIcon={<AddCommentOutlinedIcon />}
                  onClick={() => void createSession()}
                  disabled={creatingSession}
                >
                  새 대화
                </Button>
                <Button
                  variant="contained"
                  onClick={() => navigate("/policies")}
                >
                  정책 목록 보기
                </Button>
              </Stack>
            </Stack>
          </Paper>

          <Box
            sx={{
              display: "grid",
              gap: 2,
              gridTemplateColumns: { xs: "1fr", lg: "320px minmax(0, 1fr)" },
              alignItems: "start",
            }}
          >
            <Card>
              <CardContent sx={{ p: 0 }}>
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                  sx={{ px: 2, py: 1.75 }}
                >
                  <Box>
                    <Typography variant="subtitle1" fontWeight={700}>
                      대화 세션
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      최근 대화부터 최대 20개
                    </Typography>
                  </Box>
                  <Button
                    size="small"
                    startIcon={<AddCommentOutlinedIcon />}
                    onClick={() => void createSession()}
                    disabled={creatingSession}
                  >
                    시작
                  </Button>
                </Stack>

                <Divider />

                {loadingSessions ? (
                  <Box sx={{ py: 6, display: "flex", justifyContent: "center" }}>
                    <CircularProgress size={28} />
                  </Box>
                ) : sessions.length ? (
                  <List sx={{ p: 1 }}>
                    {sessions.map((session) => (
                      <Paper
                        key={session.sessionId}
                        elevation={0}
                        sx={{
                          mb: 1,
                          borderRadius: 3,
                          border: session.sessionId === activeSessionId
                            ? "1px solid #028090"
                            : "1px solid #E8F4F5",
                          bgcolor: session.sessionId === activeSessionId
                            ? "rgba(2,128,144,0.08)"
                            : "background.paper",
                        }}
                      >
                        <ListItemButton
                          selected={session.sessionId === activeSessionId}
                          onClick={() => setActiveSessionId(session.sessionId)}
                          sx={{ borderRadius: 3, alignItems: "flex-start", pr: 1 }}
                        >
                          <ListItemText
                            primary={
                              <Typography fontWeight={700} noWrap>
                                {session.title}
                              </Typography>
                            }
                            secondary={
                              <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                <Typography variant="caption" color="text.secondary" noWrap>
                                  {formatRelativeTime(session.lastMessageAt || session.createdAt)}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" noWrap>
                                  세션 #{session.sessionId}
                                </Typography>
                              </Stack>
                            }
                          />
                          <IconButton
                            size="small"
                            color="error"
                            disabled={deletingSessionId === session.sessionId}
                            onClick={(event) => {
                              event.stopPropagation();
                              void handleDeleteSession(session.sessionId);
                            }}
                          >
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        </ListItemButton>
                      </Paper>
                    ))}
                  </List>
                ) : (
                  <Box sx={{ p: 3 }}>
                    <Typography fontWeight={700} sx={{ mb: 0.75 }}>
                      아직 시작한 대화가 없습니다
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                      새 대화를 열고 지역, 소득, 상황을 함께 적으면 답변이 더 빨라집니다.
                    </Typography>
                    <Button
                      fullWidth
                      variant="contained"
                      onClick={() => void createSession()}
                      disabled={creatingSession}
                    >
                      첫 대화 시작하기
                    </Button>
                  </Box>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 0 }}>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="center"
                  sx={{ px: 2.5, py: 2 }}
                >
                  <Box>
                    <Typography variant="h6" fontWeight={800}>
                      {activeSession?.title ?? "청년 정책 상담"}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      정책 추천이 아니라 질문 기반 상담입니다. 마감일과 상세 조건은 정책 페이지에서 다시 확인하세요.
                    </Typography>
                  </Box>
                  <Chip
                    icon={<ForumOutlinedIcon />}
                    label={activeSessionId ? `세션 #${activeSessionId}` : "새 대화 준비"}
                    color="secondary"
                    variant="outlined"
                  />
                </Stack>

                <Divider />

                <Box
                  sx={{
                    minHeight: 520,
                    maxHeight: 620,
                    overflowY: "auto",
                    px: { xs: 1.5, md: 2.5 },
                    py: 2,
                    bgcolor: "#F8FCFC",
                  }}
                >
                  {loadingMessages ? (
                    <Box sx={{ py: 10, display: "flex", justifyContent: "center" }}>
                      <CircularProgress size={30} />
                    </Box>
                  ) : messages.length ? (
                    <Stack spacing={1.5}>
                      {messages.map((message) => (
                        <Box
                          key={message.messageId}
                          sx={{
                            display: "flex",
                            justifyContent: message.role === "USER" ? "flex-end" : "flex-start",
                          }}
                        >
                          <Paper
                            elevation={0}
                            sx={{
                              maxWidth: { xs: "94%", md: "78%" },
                              px: 1.75,
                              py: 1.4,
                              borderRadius: 3,
                              border: message.role === "USER"
                                ? "1px solid rgba(2,128,144,0.18)"
                                : "1px solid #E2EEF0",
                              bgcolor: message.role === "USER" ? "#DFF4F2" : "white",
                            }}
                          >
                            <Stack spacing={1}>
                              <Stack direction="row" justifyContent="space-between" spacing={1}>
                                <Typography variant="caption" fontWeight={700} color="text.secondary">
                                  {message.role === "USER" ? "나" : "정책 상담"}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {formatMessageTime(message.createdAt)}
                                </Typography>
                              </Stack>

                              <Typography sx={{ whiteSpace: "pre-wrap", lineHeight: 1.7 }}>
                                {message.content}
                              </Typography>

                              {message.referencedServiceIds.length > 0 ? (
                                <Stack spacing={1}>
                                  <Typography variant="caption" color="text.secondary" fontWeight={700}>
                                    연결 정책
                                  </Typography>
                                  {message.referencedServiceIds.map((serviceId) => {
                                    const meta = policyMeta[serviceId];
                                    return (
                                      <Paper
                                        key={serviceId}
                                        elevation={0}
                                        onClick={() => navigate(`/policies/${serviceId}`)}
                                        sx={{
                                          p: 1.2,
                                          borderRadius: 2.5,
                                          border: "1px solid #D9ECEE",
                                          bgcolor: "#F8FFFF",
                                          cursor: "pointer",
                                          transition: "transform 0.15s ease, border-color 0.15s ease",
                                          "&:hover": {
                                            transform: "translateY(-1px)",
                                            borderColor: "#028090",
                                          },
                                        }}
                                      >
                                        <Typography fontWeight={700}>
                                          {meta?.title ?? `정책 #${serviceId}`}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                          {meta?.description ?? "상세 페이지에서 조건과 신청 방법을 확인해보세요."}
                                        </Typography>
                                      </Paper>
                                    );
                                  })}
                                </Stack>
                              ) : null}
                            </Stack>
                          </Paper>
                        </Box>
                      ))}
                    </Stack>
                  ) : (
                    <Box
                      sx={{
                        minHeight: 420,
                        display: "flex",
                        flexDirection: "column",
                        justifyContent: "center",
                        alignItems: "center",
                        textAlign: "center",
                        px: 2,
                      }}
                    >
                      <Box
                        sx={{
                          width: 68,
                          height: 68,
                          borderRadius: "24px",
                          display: "grid",
                          placeItems: "center",
                          bgcolor: "rgba(2,128,144,0.12)",
                          color: "primary.main",
                          mb: 2,
                        }}
                      >
                        <ForumOutlinedIcon sx={{ fontSize: 34 }} />
                      </Box>
                      <Typography variant="h6" fontWeight={800} sx={{ mb: 1 }}>
                        지금 상황을 자연스럽게 적어주세요
                      </Typography>
                      <Typography color="text.secondary" sx={{ maxWidth: 560, mb: 2 }}>
                        예산, 지역, 취업 상태, 마감일처럼 정책 판단에 필요한 단서를 섞어 쓰면
                        연결 정책을 더 빨리 좁힐 수 있습니다.
                      </Typography>
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" justifyContent="center">
                        {SUGGESTED_PROMPTS.map((prompt) => (
                          <Chip
                            key={prompt}
                            label={prompt}
                            onClick={() => setDraft(prompt)}
                            sx={{ maxWidth: "100%" }}
                          />
                        ))}
                      </Stack>
                    </Box>
                  )}
                </Box>

                <Divider />

                <Box component="form" onSubmit={handleSend} sx={{ p: { xs: 1.5, md: 2 } }}>
                  <Stack spacing={1.25}>
                    <TextField
                      fullWidth
                      multiline
                      minRows={3}
                      maxRows={6}
                      placeholder="예: 서울에 사는 미취업 청년인데 월세나 교육비 지원을 같이 보고 싶어요."
                      value={draft}
                      onChange={(event) => setDraft(event.target.value)}
                      disabled={sending}
                    />
                    <Stack
                      direction={{ xs: "column", sm: "row" }}
                      spacing={1}
                      justifyContent="space-between"
                      alignItems={{ xs: "stretch", sm: "center" }}
                    >
                      <Typography variant="caption" color="text.secondary">
                        정책 추천 재계산 없이 질문 기반으로만 답합니다. 너무 빠른 연속 요청은 자동 제한됩니다.
                      </Typography>
                      <Button
                        type="submit"
                        variant="contained"
                        endIcon={sending ? <CircularProgress size={16} color="inherit" /> : <SendRoundedIcon />}
                        disabled={!draft.trim() || sending}
                      >
                        {sending ? "전송 중" : "질문 보내기"}
                      </Button>
                    </Stack>
                  </Stack>
                </Box>
              </CardContent>
            </Card>
          </Box>
        </Stack>
      </Container>

      <Snackbar
        open={toast.open}
        autoHideDuration={2600}
        onClose={() => setToast((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((prev) => ({ ...prev, open: false }))}>
          {toast.msg}
        </Alert>
      </Snackbar>
    </Box>
  );
}
