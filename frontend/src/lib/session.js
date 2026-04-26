import api from "./axios";

export async function performServerLogout(logout) {
  try {
    await api.post("/api/auth/logout");
  } catch {
    // Refresh cookie may already be gone; local session cleanup still needs to run.
  } finally {
    logout();
  }
}
