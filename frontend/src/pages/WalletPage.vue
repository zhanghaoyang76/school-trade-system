<script setup>
import { ref, onMounted } from "vue";
import { get, getAll, listOf, money, dateTime } from "../api.js";
const wallet = ref({}),
  points = ref({}),
  entries = ref([]),
  pointEntries = ref([]),
  busy = ref(false),
  error = ref(""),
  tab = ref("money");
const types = {
  RECHARGE: "管理员充值",
  PAYMENT: "下单付款",
  ESCROW: "交易托管",
  SETTLEMENT: "收货结算",
  FEE: "交易手续费",
  REFUND: "退货退款",
  CANCEL: "取消退回",
  RELEASE: "冻结解除",
  FREEZE: "收入冻结",
  SPEND: "积分抵扣",
  REWARD: "消费奖励",
  RETURN: "退货返还",
};
async function load() {
  busy.value = true;
  error.value = "";
  try {
    [wallet.value, points.value] = await Promise.all([
      get("/wallet"),
      get("/points"),
    ]);
    [entries.value, pointEntries.value] = await Promise.all([
      getAll("/wallet/entries"),
      getAll("/points/entries"),
    ]);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="page-content" v-loading="busy">
    <div class="page-heading">
      <div>
        <h1>钱包与积分</h1>
        <p class="muted">余额变动有迹可查，冻结资金与积分暂不可消费。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <dl class="summary-grid">
      <div>
        <dt>可用余额</dt>
        <dd>¥{{ money(wallet.available) }}</dd>
      </div>
      <div>
        <dt>冻结余额</dt>
        <dd>¥{{ money(wallet.frozen) }}</dd>
      </div>
      <div>
        <dt>可用积分</dt>
        <dd>{{ points.available || 0 }} <small>分</small></dd>
      </div>
      <div>
        <dt>冻结积分</dt>
        <dd>{{ points.frozen || 0 }} <small>分</small></dd>
      </div>
    </dl>
    <div class="info-panel">
      <p>
        每100积分抵扣1元，按钱包实际消费金额发放奖励积分。确认收货后，卖家收入及买家新积分冻结24小时；存在售后时继续冻结对应部分。
      </p>
      <p>本平台为课程模拟交易系统，钱包由管理员充值，不接入真实支付。</p>
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane label="资金流水" name="money" /><el-tab-pane
        label="积分流水"
        name="points" /></el-tabs
    ><el-table
      :data="tab === 'money' ? entries : pointEntries"
      empty-text="暂无流水记录"
      ><el-table-column label="时间" min-width="150"
        ><template #default="{ row }">{{
          dateTime(row.createdAt)
        }}</template></el-table-column
      ><el-table-column label="业务" min-width="160"
        ><template #default="{ row }">{{
          types[row.type] || row.type || row.businessKey
        }}</template></el-table-column
      ><el-table-column label="可用变动" min-width="120"
        ><template #default="{ row }"
          ><span :class="{ 'error-text': Number(row.availableDelta) < 0 }"
            >{{ Number(row.availableDelta) > 0 ? "+" : ""
            }}{{
              tab === "money" ? money(row.availableDelta) : row.availableDelta
            }}</span
          ></template
        ></el-table-column
      ><el-table-column label="冻结变动" min-width="120"
        ><template #default="{ row }"
          >{{ Number(row.frozenDelta) > 0 ? "+" : ""
          }}{{
            tab === "money" ? money(row.frozenDelta) : row.frozenDelta
          }}</template
        ></el-table-column
      ><el-table-column prop="businessKey" label="业务流水号" min-width="240"
    /></el-table>
  </section>
</template>
