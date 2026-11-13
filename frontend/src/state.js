import { reactive } from "vue";
import { get, post, resetCsrf } from "./api.js";
export const session = reactive({ user: null, ready: false, error: "" });
export async function refreshSession() {
  try {
    session.user = await get("/auth/me");
    session.error = "";
  } catch (e) {
    session.user = null;
    if (e.status !== 401) session.error = e.message;
  } finally {
    session.ready = true;
  }
  return session.user;
}
export async function logout() {
  await post("/auth/logout");
  resetCsrf();
  session.user = null;
}
