let csrf;
export function resetCsrf() {
  csrf = undefined;
}
export async function request(
  path,
  { method = "GET", body, headers = {} } = {},
) {
  const modifying = !["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase());
  if (modifying && !csrf) {
    csrf = await request("/auth/csrf");
  }
  const outgoing = { Accept: "application/json", ...headers };
  if (modifying && csrf?.token)
    outgoing[csrf.headerName || "X-CSRF-TOKEN"] = csrf.token;
  const isForm = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isForm)
    outgoing["Content-Type"] = "application/json";
  let response;
  try {
    response = await fetch("/api" + path, {
      method,
      credentials: "same-origin",
      headers: outgoing,
      body:
        body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new Error("无法连接服务，请检查后端是否已启动。");
  }
  let payload;
  try {
    payload = await response.json();
  } catch {
    const error = new Error("服务暂时不可用，请稍后重试。");
    error.status = response.status;
    throw error;
  }
  if (!response.ok) {
    const error = new Error(payload.message || "操作失败，请重试。");
    error.status = response.status;
    error.details = payload.errors;
    throw error;
  }
  return payload.data;
}
export const get = (path) => request(path);
export const post = (path, body = {}) =>
  request(path, { method: "POST", body });
export const patch = (path, body) => request(path, { method: "PATCH", body });
export const del = (path) => request(path, { method: "DELETE" });
export const listOf = (data) =>
  Array.isArray(data) ? data : data?.records || [];
export async function getAll(path) {
  const records = [];
  for (let page = 1; ; page++) {
    const result = await get(
      `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=100`,
    );
    const rows = listOf(result);
    records.push(...rows);
    if (
      Array.isArray(result) ||
      !rows.length ||
      records.length >= Number(result.total ?? records.length)
    )
      return records;
  }
}
export const money = (value) =>
  Number(value || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
export const dateTime = (value) =>
  value ? String(value).replace("T", " ").slice(0, 16) : "—";
export const positive = (value) =>
  value == null ? "暂无评价" : `${Number(value).toFixed(0)}%`;
