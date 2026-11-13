<script setup>
import { ref, computed, reactive, nextTick, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { get, post, patch, del, listOf, money } from "../api.js";
import { ElMessage } from "element-plus";
const router = useRouter(),
  route = useRoute(),
  rows = ref([]),
  selected = ref([]),
  table = ref(null),
  busy = ref(false),
  submitting = ref(false),
  error = ref(""),
  wallet = ref({ available: 0 }),
  points = ref({ available: 0 }),
  usedPoints = ref(0),
  appointments = reactive({});
let requestKey = crypto.randomUUID(),
  lastPayload = "";
const checkout = computed(() => route.path === "/checkout");
const total = computed(() =>
  selected.value.reduce(
    (s, r) => s + Number(r.product?.price || 0) * r.quantity,
    0,
  ),
);
const maxPoints = computed(() =>
  Math.max(
    0,
    Math.floor(
      Math.min(
        Number(points.value.available || 0),
        Math.floor(total.value) * 100,
      ) / 100,
    ) * 100,
  ),
);
const groups = computed(() => {
  const grouped = {};
  for (const r of selected.value) {
    const p = r.product;
    if (!p) continue;
    if (!grouped[p.shopId])
      grouped[p.shopId] = { id: p.shopId, name: p.shopName, items: [] };
    grouped[p.shopId].items.push(r);
    if (!appointments[p.shopId])
      appointments[p.shopId] = { appointmentTime: "", appointmentPlace: "" };
  }
  return Object.values(grouped);
});
async function load() {
  busy.value = true;
  error.value = "";
  try {
    rows.value = listOf(await get("/cart/items"));
    [wallet.value, points.value] = await Promise.all([
      get("/wallet"),
      get("/points"),
    ]);
    await nextTick();
    table.value?.toggleAllSelection();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function changeQuantity(row) {
  try {
    await patch("/cart/items/" + row.id, { quantity: row.quantity });
  } catch (e) {
    ElMessage.error(e.message);
    load();
  }
}
async function remove(row) {
  try {
    await del("/cart/items/" + row.id);
    rows.value = rows.value.filter((r) => r.id !== row.id);
    selected.value = selected.value.filter((r) => r.id !== row.id);
  } catch (e) {
    ElMessage.error(e.message);
  }
}
function goCheckout() {
  if (!selected.value.length) {
    ElMessage.warning("请先选择商品");
    return;
  }
  router.push("/checkout");
}
async function submit() {
  if (!selected.value.length) {
    ElMessage.warning("请先选择商品");
    return;
  }
  for (const group of groups.value) {
    const a = appointments[group.id];
    if (!a.appointmentTime || !a.appointmentPlace.trim()) {
      ElMessage.warning("请为每家店铺填写线下交易时间与地点");
      return;
    }
  }
  if (usedPoints.value > maxPoints.value) {
    ElMessage.warning("积分抵扣超过可用范围");
    return;
  }
  const body = {
    points: usedPoints.value,
    items: selected.value.map((r) => ({
      productId: r.productId,
      quantity: r.quantity,
    })),
    appointments: groups.value.map((g) => ({
      shopId: g.id,
      ...appointments[g.id],
    })),
  };
  const signature = JSON.stringify(body);
  if (lastPayload && lastPayload !== signature)
    requestKey = crypto.randomUUID();
  lastPayload = signature;
  submitting.value = true;
  error.value = "";
  try {
    await post("/checkouts", { ...body, requestKey });
    ElMessage.success("下单成功，已按卖家拆单并扣款");
    router.push("/orders");
  } catch (e) {
    error.value = e.message;
    ElMessage.error(e.message);
  } finally {
    submitting.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="page-content">
    <div class="page-heading">
      <div>
        <h1>{{ checkout ? "确认订单" : "购物车" }}</h1>
        <p class="muted">
          {{
            checkout
              ? "按卖家分别约定交付，一次结账统一扣款。"
              : "选好所需商品，线上下单锁定库存。"
          }}
        </p>
      </div>
      <RouterLink v-if="checkout" to="/cart" class="text-link"
        >返回购物车</RouterLink
      >
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    /><el-table
      ref="table"
      v-loading="busy"
      :data="rows"
      row-key="id"
      empty-text="购物车还是空的，去商品市场看看吧"
      @selection-change="selected = $event"
      ><el-table-column type="selection" width="42" /><el-table-column
        label="商品"
        min-width="230"
        ><template #default="{ row }"
          ><div class="table-product">
            <img
              v-if="row.product?.imageUrls?.[0]"
              :src="row.product.imageUrls[0]"
              :alt="row.product.name"
            />
            <div>
              <RouterLink :to="'/products/' + row.productId">{{
                row.product?.name
              }}</RouterLink><small
                >{{ row.product?.shopName }} ·
                {{ row.product?.conditionText }}</small
              ><small
                v-if="row.product?.status !== 'ON_SHELF'"
                class="error-text"
                >当前商品不可购买</small
              >
            </div>
          </div></template
        ></el-table-column
      ><el-table-column label="单价" width="110"
        ><template #default="{ row }"
          >¥{{ money(row.product?.price) }}</template
        ></el-table-column
      ><el-table-column label="数量" width="155"
        ><template #default="{ row }"
          ><el-input-number
            v-model="row.quantity"
            :min="1"
            :max="Math.max(1, row.product?.stock || 1)"
            size="small"
            @change="changeQuantity(row)" /></template></el-table-column
      ><el-table-column label="小计" width="110"
        ><template #default="{ row }"
          >¥{{ money(Number(row.product?.price) * row.quantity) }}</template
        ></el-table-column
      ><el-table-column label="操作" width="75"
        ><template #default="{ row }"
          ><el-button link @click="remove(row)">移除</el-button></template
        ></el-table-column
      ></el-table
    >
    <template v-if="checkout && rows.length"
      ><section class="checkout-section">
        <h2>线下交付约定</h2>
        <div v-for="group in groups" :key="group.id" class="checkout-shop">
          <strong>{{ group.name }}</strong>
          <p class="muted small">
            {{
              group.items
                .map((r) => r.product.name + " × " + r.quantity)
                .join("、")
            }}
          </p>
          <div class="form-grid">
            <label
              >交易时间<el-date-picker
                v-model="appointments[group.id].appointmentTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm"
                placeholder="选择线下交易时间" /></label
            ><label
              >交易地点<el-input
                v-model="appointments[group.id].appointmentPlace"
                maxlength="150"
                placeholder="例如：图书馆正门"
            /></label>
          </div>
        </div>
      </section>
      <section class="checkout-section">
        <h2>钱包与积分</h2>
        <p class="muted">
          钱包可用 ¥{{ money(wallet.available) }}，可用积分
          {{ points.available }}。
        </p>
        <div class="quantity-line">
          <span>使用积分</span
          ><el-input-number
            v-model="usedPoints"
            :step="100"
            step-strictly
            :min="0"
            :max="maxPoints"
          /><span class="muted small"
            >每100积分抵1元，抵扣 ¥{{ money(usedPoints / 100) }}</span
          >
        </div>
        <div class="info-panel">
          任一商品库存不足、店铺关闭或存在购买限制时，整次结账失败，所有款项和库存都会回滚。
        </div>
      </section></template
    >
    <div v-if="rows.length" class="cart-summary">
      <span class="muted">已选 {{ selected.length }} 项</span
      ><span
        >{{ checkout ? "钱包实付" : "商品合计" }}
        <strong
          >¥{{ money(total - (checkout ? usedPoints / 100 : 0)) }}</strong
        ></span
      ><el-button
        v-if="!checkout"
        type="primary"
        :disabled="!selected.length"
        @click="goCheckout"
        >去结账</el-button
      ><el-button
        v-else
        type="primary"
        :loading="submitting"
        :disabled="!selected.length"
        @click="submit"
        >确认下单并扣款</el-button
      >
    </div>
  </section>
</template>
