import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { nonemptyReason } from "./validation.js";
export function useOperation() {
  const busy = ref(false),
    error = ref("");
  async function run(task, success) {
    if (busy.value) return;
    busy.value = true;
    error.value = "";
    try {
      const r = await task();
      if (success) ElMessage.success(success);
      return r;
    } catch (e) {
      error.value = e.message;
      ElMessage.error(e.message);
      throw e;
    } finally {
      busy.value = false;
    }
  }
  return { busy, error, run };
}
export async function confirmAction(message) {
  try {
    await ElMessageBox.confirm(message, "确认操作", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    });
    return true;
  } catch {
    return false;
  }
}
export async function askReason(title) {
  try {
    const { value } = await ElMessageBox.prompt(
      "请填写原因，便于交易双方了解情况。",
      title,
      {
        confirmButtonText: "提交",
        cancelButtonText: "取消",
        inputType: "textarea",
        inputValidator: nonemptyReason,
      },
    );
    return value.trim();
  } catch {
    return null;
  }
}
export function escapeError(task) {
  return task.catch(() => {});
}
export const labels = {
  PENDING: "待审核",
  APPROVED: "审核通过",
  REJECTED: "已拒绝",
  ON_SHELF: "出售中",
  OFF_SHELF: "已下架",
  PAID: "待线下交付",
  DELIVERED: "已交付待确认",
  CONFIRMED: "已确认收货",
  CANCELLED: "已取消",
  TRANSIT: "在途退货",
  RETURNED: "已退货",
  OPEN: "营业中",
  CLOSED: "已关闭",
  AUTO: "自动等级",
  MANUAL: "手动等级",
  USER: "普通用户",
  MERCHANT: "商家",
  ADMIN: "管理员",
};
export const label = (s) => labels[s] || s || "—";
