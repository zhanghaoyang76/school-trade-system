<script setup>
import { ref, onMounted, computed } from "vue";
import { get, getAll, post, patch, listOf, money, dateTime } from "../api.js";
import { askReason, confirmAction, label } from "../composables.js";
import { ElMessage } from "element-plus";
const props = defineProps({ seller: Boolean });
const orders = ref([]),
  busy = ref(false),
  error = ref(""),
  status = ref(""),
  acting = ref(false),
  review = ref(null),
  reviewForm = ref({
    productStars: 5,
    serviceStars: 5,
    buyerStars: 5,
    content: "",
  }),
  appointment = ref(null);
const visible = computed(() =>
  status.value
    ? orders.value.filter((o) => o.status === status.value)
    : orders.value,
);
async function load() {
  busy.value = true;
  error.value = "";
  try {
    const records = await getAll(props.seller ? "/seller/orders" : "/orders");
    orders.value = await Promise.all(
      records.map((o) => (o.items ? o : get("/orders/" + o.id))),
    );
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function act(task, message) {
  acting.value = true;
  try {
    await task();
    ElMessage.success(message);
    await load();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    acting.value = false;
  }
}
async function cancel(order) {
  const reason = await askReason("取消订单");
  if (reason)
    act(
      () =>
        post(
          (props.seller ? "/seller" : "") +
            "/orders/" +
            order.id +
            "/cancellations",
          { reason },
        ),
      "已取消，货款和积分已退回",
    );
}
async function confirm(order) {
  if (
    await confirmAction(
      "确认已收到商品？确认后货款结算并冻结24小时，期间可申请退货。",
    )
  )
    act(() => post("/orders/" + order.id + "/confirmations"), "已确认收货");
}
async function deliver(order) {
  if (
    await confirmAction(
      "确认已经向买家线下交付商品？从现在开始计算7天自动收货期限。",
    )
  )
    act(
      () => post("/seller/orders/" + order.id + "/deliveries"),
      "已标记线下交付",
    );
}
function canReturn(order, item) {
  return (
    order.status === "CONFIRMED" &&
    order.confirmedAt &&
    Date.now() - new Date(order.confirmedAt).getTime() < 86400000 &&
    !["PENDING", "TRANSIT", "RETURNED"].includes(item.returnStatus) &&
    item.settlementStatus !== "REFUNDED"
  );
}
async function returnItem(item) {
  const reason = await askReason("申请整项退货");
  if (reason)
    act(
      () => post("/order-items/" + item.id + "/returns", { reason }),
      "退货申请已提交",
    );
}
function startReview(item) {
  review.value = item;
  reviewForm.value = {
    productStars: 5,
    serviceStars: 5,
    buyerStars: 5,
    content: "",
  };
}
async function sendReview() {
  if (!reviewForm.value.content.trim()) {
    ElMessage.warning("请填写评价内容");
    return;
  }
  const item = review.value;
  await act(async () => {
    await post(
      (props.seller ? "/seller" : "") + "/order-items/" + item.id + "/reviews",
      reviewForm.value,
    );
    review.value = null;
  }, "评价已提交");
}
async function blacklist(order) {
  const reason = await askReason("将此买家加入本店黑名单");
  if (reason)
    act(
      () => post("/seller/blacklists", { buyerId: order.buyerId, reason }),
      "已拉黑，仅影响该买家在本店的新购买",
    );
}
async function saveAppointment() {
  const a = appointment.value;
  if (!a.appointmentTime || !a.appointmentPlace.trim()) {
    ElMessage.warning("请完整填写时间地点");
    return;
  }
  await act(async () => {
    await patch("/seller/orders/" + a.id + "/appointment", {
      appointmentTime: a.appointmentTime,
      appointmentPlace: a.appointmentPlace,
    });
    appointment.value = null;
  }, "交付约定已更新");
}
onMounted(load);
defineExpose({ load });
</script>
<template>
  <div v-loading="busy">
    <div class="page-heading">
      <div>
        <h1>{{ seller ? "卖家订单" : "我的订单" }}</h1>
        <p class="muted">
          {{
            seller
              ? "按约定线下交付，及时处理买家的交易。"
              : "查看交付约定、收货及售后进度。"
          }}
        </p>
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
        label="待交付"
        name="PAID" /><el-tab-pane
        label="已交付"
        name="DELIVERED" /><el-tab-pane
        label="已确认"
        name="CONFIRMED" /><el-tab-pane label="已取消" name="CANCELLED"
    /></el-tabs>
    <div v-if="!visible.length && !busy" class="empty-page">
      <p class="muted">暂无对应订单</p>
    </div>
    <article v-for="order in visible" :key="order.id" class="order-card">
      <header class="order-header">
        <div>
          <strong>{{
            seller
              ? "买家：" + (order.buyerName || order.buyerId)
              : order.shopName || order.sellerName
          }}</strong>
          <p class="muted">
            订单 {{ order.orderNo || order.id }} ·
            {{ dateTime(order.createdAt) }}
          </p>
        </div>
        <el-tag effect="plain">{{ label(order.status) }}</el-tag>
      </header>
      <div class="order-appointment">
        交付约定：{{ dateTime(order.appointmentTime) }} ·
        {{ order.appointmentPlace || "未填写" }}
      </div>
      <div v-for="item in order.items" :key="item.id" class="order-item">
        <div class="order-item-info">
          <RouterLink :to="'/products/' + item.productId"
            ><strong>{{ item.productName }}</strong></RouterLink
          >
          <p class="muted">
            ¥{{ money(item.unitPrice) }} × {{ item.quantity }} · 实付 ¥{{
              money(item.paidAmount)
            }}
            · 抵扣 {{ item.usedPoints || 0 }} 积分
          </p>
          <p v-if="item.returnStatus" class="muted">
            售后：{{ label(item.returnStatus) }}
          </p>
          <p v-if="item.settlementStatus === 'FROZEN'" class="muted small">
            货款 / 奖励积分冻结中，退货期限结束后可用
          </p>
          <p v-if="seller" class="muted small">手续费 ¥{{ money(item.fee) }}</p>
        </div>
        <div class="order-item-actions">
          <el-button
            v-if="!seller && canReturn(order, item)"
            size="small"
            :disabled="acting"
            @click="returnItem(item)"
            >申请退货</el-button
          ><el-button
            v-if="order.status === 'CONFIRMED'"
            size="small"
            :disabled="acting"
            @click="startReview(item)"
            >{{ seller ? "评价买家" : "评价商品与服务" }}</el-button
          >
        </div>
      </div>
      <footer class="order-footer">
        <span
          >实付
          <strong
            >¥{{
              money(
                order.paidAmount ??
                  order.items?.reduce((s, i) => s + Number(i.paidAmount), 0),
              )
            }}</strong
          ></span
        >
        <div class="table-actions">
          <el-button
            v-if="order.status === 'PAID'"
            :disabled="acting"
            @click="cancel(order)"
            >取消订单</el-button
          ><el-button
            v-if="seller && order.status === 'PAID'"
            :disabled="acting"
            @click="appointment = { ...order }"
            >修改交付约定</el-button
          ><el-button
            v-if="seller && order.status === 'PAID'"
            type="primary"
            :loading="acting"
            @click="deliver(order)"
            >已线下交付</el-button
          ><el-button
            v-if="!seller && order.status === 'DELIVERED'"
            type="primary"
            :loading="acting"
            @click="confirm(order)"
            >确认收货</el-button
          ><el-button v-if="seller" :disabled="acting" @click="blacklist(order)"
            >拉黑买家</el-button
          >
        </div>
      </footer>
    </article>
    <el-dialog
      :model-value="!!review"
      :title="seller ? '评价买家行为' : '评价商品与服务'"
      width="500px"
      @close="review = null"
      ><form class="form-stack" @submit.prevent="sendReview">
        <template v-if="!seller"
          ><label>商品评分<el-rate v-model="reviewForm.productStars" /></label
          ><label
            >服务态度<el-rate
              v-model="reviewForm.serviceStars" /></label></template
        ><label v-else
          >购买 / 退货行为评分<el-rate v-model="reviewForm.buyerStars" /></label
        ><label
          >评价内容<el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            required
        /></label>
        <p class="muted small">
          每项交易每个方向只能评价一次，不支持修改和追评。
        </p>
        <el-button type="primary" native-type="submit" :loading="acting"
          >提交评价</el-button
        >
      </form></el-dialog
    >
    <el-dialog
      :model-value="!!appointment"
      title="修改线下交付约定"
      width="500px"
      @close="appointment = null"
      ><form
        v-if="appointment"
        class="form-stack"
        @submit.prevent="saveAppointment"
      >
        <label
          >交易时间<el-date-picker
            v-model="appointment.appointmentTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /></label
        ><label
          >交易地点<el-input
            v-model="appointment.appointmentPlace"
            required
            maxlength="150" /></label
        ><el-button native-type="submit" type="primary" :loading="acting"
          >保存约定</el-button
        >
      </form></el-dialog
    >
  </div>
</template>
