import { useQuery } from "@tanstack/react-query";
import api from "./axios";

export function useUnreadAlertCount(isLoggedIn) {
  const query = useQuery({
    queryKey: ["notification-unread-count", isLoggedIn],
    enabled: !!isLoggedIn,
    queryFn: async () => {
      const { data } = await api.get("/api/notifications/me/unread-count");
      return Number(data?.data?.unreadCount ?? 0);
    },
    staleTime: 30_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });

  return isLoggedIn ? (query.data ?? 0) : 0;
}
