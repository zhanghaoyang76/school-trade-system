import test from "node:test";
import assert from "node:assert/strict";
import { nonemptyReason } from "../src/validation.js";
test("有效的取消或退货原因必须允许提交", () =>
  assert.equal(nonemptyReason("商品不合适"), true));
test("空白原因显示错误提示", () =>
  assert.equal(nonemptyReason("  "), "请输入原因"));
