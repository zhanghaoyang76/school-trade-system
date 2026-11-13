import test from "node:test";
import assert from "node:assert/strict";
import { request, resetCsrf, getAll } from "../src/api.js";

test("分页列表读取后续页，不遗漏旧订单和资金记录", async () => {
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return Response.json({
      data: {
        records:
          count === 1
            ? Array.from({ length: 100 }, (_, i) => ({ id: i + 1 }))
            : [{ id: 101 }],
        total: 101,
      },
    });
  };
  const rows = await getAll("/orders");
  assert.equal(rows.length, 101);
  assert.equal(rows.at(-1).id, 101);
  assert.equal(count, 2);
});

test("读取接口携带会话，正确解包真实业务响应", async () => {
  let options;
  globalThis.fetch = async (url, init) => {
    assert.equal(url, "/api/products");
    options = init;
    return Response.json({
      data: { records: [{ id: 1 }], total: 1 },
      message: "成功",
    });
  };
  const result = await request("/products");
  assert.equal(options.credentials, "same-origin");
  assert.equal(result.records[0].id, 1);
});
test("状态修改请求先取得CSRF令牌并发送，正文编码为JSON", async () => {
  resetCsrf();
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return Response.json(
      url.endsWith("/auth/csrf")
        ? { data: { token: "test-token", headerName: "X-CSRF-TOKEN" } }
        : { data: { id: 5 } },
    );
  };
  await request("/orders/5/confirmations", { method: "POST", body: {} });
  assert.equal(calls.length, 2);
  assert.equal(calls[1].init.headers["X-CSRF-TOKEN"], "test-token");
  assert.equal(calls[1].init.body, "{}");
});
test("业务失败保留HTTP状态及服务端原因，不将失败当作成功", async () => {
  globalThis.fetch = async () =>
    Response.json({ message: "余额不足" }, { status: 409 });
  await assert.rejects(
    request("/wallet"),
    (e) => e.message === "余额不足" && e.status === 409,
  );
});
test("非JSON系统故障给出可见错误", async () => {
  globalThis.fetch = async () => new Response("bad gateway", { status: 502 });
  await assert.rejects(
    request("/wallet"),
    (e) => e.status === 502 && /服务/.test(e.message),
  );
});
