<script setup>
import { ref, onMounted, computed } from "vue";
import { get, getAll, post, listOf, money, dateTime } from "../api.js";
import { askReason, confirmAction, label } from "../composables.js";
import { ElMessage } from "element-plus";
const props = defineProps({ seller: Boolean });
const rows = ref([]),
  busy = ref(false),
  error = ref(""),
  status = ref(""),
  acting = ref(false);
const visible = computed(() =>
  status.value
    ? rows.value.filter((r) => r.status === status.value)
    : rows.value,
);
async function load() {
  busy.value = true;
  error.value = "";
  try {
    rows.value = await getAll(props.seller ? "/seller/returns" : "/returns");
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function review(row, approved) {
  let reason = "";
  if (approved) {
    if (
      !(await confirmAction(
        "审核通过将立即退款给买家；商品标为在途退货，实际收到商品后再确认交回并恢复库存。",
      ))
    )
      return;
  } else {
    reason = await askReason("拒绝退货原因");
    if (!reason) return;
  }
  await act(
    () => post("/seller/returns/" + row.id + "/reviews", { approved, reason }),
    "退货审核已处理",
  );
}
async function receipt(row) {
  if (await confirmAction("确认已经实际收到退回商品？系统将恢复该商品库存。"))
    act(
      () => post("/seller/returns/" + row.id + "/receipts"),
      "已确认退货交回并恢复库存",
    );
}
async function act(fn, message) {
  acting.value = true;
  try {
    await fn();
    ElMessage.success(message);
    load();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    acting.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="busy">
    <div class="page-heading">
      <div>
        <h1>{{ seller ? "退货管理" : "退货进度" }}</h1>
        <p class="muted">审核通过立即退款；实际交回后才恢复库存。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    /><el-tabs v-model="status"
      ><el-tab-pane label="全部" name="" /><el-tab-pane
        label="待审核"
        name="PENDING" /><el-tab-pane
        label="在途退货"
        name="TRANSIT" /><el-tab-pane
        label="已退货"
        name="RETURNED" /><el-tab-pane
        label="已拒绝"
        name="REJECTED" /></el-tabs
    ><el-table :data="visible" empty-text="暂无退货记录"
      ><el-table-column label="商品 / 交易对象" min-width="180"
        ><template #default="{ row }"
          ><strong>{{ row.productName }}</strong>
          <div class="muted small">
            {{
              seller
                ? "买家：" + (row.buyerName || row.buyerId)
                : "卖家：" + (row.sellerName || "")
            }}
            · {{ row.quantity }} 件
          </div></template
        ></el-table-column
      ><el-table-column label="退货原因" min-width="180"
        ><template #default="{ row }"
          ><div class="reason-text">{{ row.reason }}</div>
          <p v-if="row.reviewReason" class="muted small">
            审核说明：{{ row.reviewReason }}
          </p></template
        ></el-table-column
      ><el-table-column label="退款金额" width="115"
        ><template #default="{ row }"
          >¥{{ money(row.paidAmount) }}</template
        ></el-table-column
      ><el-table-column label="状态" width="110"
        ><template #default="{ row }">{{
          label(row.status)
        }}</template></el-table-column
      ><el-table-column label="申请时间" width="150"
        ><template #default="{ row }">{{
          dateTime(row.createdAt)
        }}</template></el-table-column
      ><el-table-column v-if="seller" label="操作" min-width="155"
        ><template #default="{ row }"
          ><div class="table-actions">
            <template v-if="row.status === 'PENDING'"
              ><el-button
                link
                type="primary"
                :disabled="acting"
                @click="review(row, true)"
                >通过并退款</el-button
              ><el-button link :disabled="acting" @click="review(row, false)"
                >拒绝</el-button
              ></template
            ><el-button
              v-if="row.status === 'TRANSIT'"
              link
              type="primary"
              :disabled="acting"
              @click="receipt(row)"
              >确认收到退货</el-button
            >
          </div></template
        ></el-table-column
      ></el-table
    >
  </div>
</template>
