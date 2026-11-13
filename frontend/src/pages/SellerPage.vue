<script setup>
import { ref, computed, reactive, watch } from "vue";
import { useRoute } from "vue-router";
import { get, getAll, post, patch, listOf, money } from "../api.js";
import { confirmAction, label } from "../composables.js";
import { ElMessage } from "element-plus";
import WorkspaceLayout from "../components/WorkspaceLayout.vue";
import UploadField from "../components/UploadField.vue";
import BlacklistPanel from "../components/BlacklistPanel.vue";
import OrdersPage from "./OrdersPage.vue";
import ReturnsPage from "./ReturnsPage.vue";
const route = useRoute(),
  section = computed(() => route.params.section),
  products = ref([]),
  categories = ref([]),
  busy = ref(false),
  saving = ref(false),
  error = ref(""),
  status = ref(""),
  edit = ref(false),
  editId = ref(null),
  images = ref([]);
const blank = () => ({
  name: "",
  categoryId: null,
  originalPrice: 0,
  discount: 1,
  price: 0,
  size: "",
  instructions: "",
  description: "",
  negotiable: false,
  conditionText: "九成新",
  stock: 1,
});
const form = reactive(blank());
const filtered = computed(() =>
  status.value === "SOLD"
    ? products.value.filter((p) => Number(p.soldCount) > 0)
    : status.value
      ? products.value.filter((p) => p.status === status.value)
      : products.value,
);
async function load() {
  if (section.value !== "products") return;
  busy.value = true;
  error.value = "";
  try {
    [products.value, categories.value] = await Promise.all([
      getAll("/seller/products"),
      get("/categories").then(listOf),
    ]);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
function start(p) {
  editId.value = p?.id || null;
  Object.assign(form, blank(), p || {});
  images.value = (p?.imageIds || []).map((id, i) => ({
    id,
    url: p.imageUrls?.[i] || "/api/files/" + id,
  }));
  edit.value = true;
}
function priceFromDiscount() {
  form.price =
    Math.round(Number(form.originalPrice) * Number(form.discount) * 100) / 100;
}
async function save() {
  if (!images.value.length) {
    ElMessage.warning("请上传至少一张商品图片");
    return;
  }
  if (!form.categoryId || form.price <= 0 || !form.name.trim()) {
    ElMessage.warning("请填写商品名称、类别及有效价格");
    return;
  }
  saving.value = true;
  try {
    const body = { ...form, imageIds: images.value.map((f) => f.id) };
    if (editId.value) await patch("/seller/products/" + editId.value, body);
    else await post("/seller/products", body);
    ElMessage.success("已提交商品审核，通过后才可购买");
    edit.value = false;
    load();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
async function off(p) {
  if (!(await confirmAction("下架后商品不能接受新订单，已有订单继续处理。")))
    return;
  try {
    await post("/seller/products/" + p.id + "/off-shelf");
    ElMessage.success("商品已下架");
    load();
  } catch (e) {
    ElMessage.error(e.message);
  }
}
async function submit(p) {
  try {
    await post("/seller/products/" + p.id + "/submissions");
    ElMessage.success("已重新提交审核");
    load();
  } catch (e) {
    ElMessage.error(e.message);
  }
}
watch(section, load, { immediate: true });
</script>
<template>
  <WorkspaceLayout
    ><OrdersPage v-if="section === 'orders'" seller /><ReturnsPage
      v-else-if="section === 'returns'"
      seller
    /><BlacklistPanel v-else-if="section === 'blacklists'" />
    <div v-else v-loading="busy">
      <div class="page-heading">
        <div>
          <h1>商品管理</h1>
          <p class="muted">商品审核后上架，修改后需重新审核。</p>
        </div>
        <el-button type="primary" @click="start()">＋ 发布商品</el-button>
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      /><el-tabs v-model="status"
        ><el-tab-pane label="全部" name="" /><el-tab-pane
          label="出售中"
          name="ON_SHELF" /><el-tab-pane
          label="待审核"
          name="PENDING" /><el-tab-pane
          label="已售出"
          name="SOLD" /><el-tab-pane
          label="已下架"
          name="OFF_SHELF" /><el-tab-pane
          label="未通过"
          name="REJECTED" /></el-tabs
      ><el-table :data="filtered" empty-text="暂无商品，发布你的第一件闲置吧"
        ><el-table-column label="商品" min-width="200"
          ><template #default="{ row }"
            ><div class="table-product">
              <img
                v-if="row.imageUrls?.[0]"
                :src="row.imageUrls[0]"
                :alt="row.name"
              />
              <div>
                {{ row.name
                }}<small
                  >{{ row.conditionText }} · 已售
                  {{ row.soldCount || 0 }} 件</small
                ><small v-if="row.reviewReason"
                  >审核说明：{{ row.reviewReason }}</small
                >
              </div>
            </div></template
          ></el-table-column
        ><el-table-column label="售价" width="110"
          ><template #default="{ row }"
            >¥{{ money(row.price) }}</template
          ></el-table-column
        ><el-table-column
          prop="stock"
          label="库存"
          width="70"
        /><el-table-column label="状态" width="105"
          ><template #default="{ row }">{{
            label(row.status)
          }}</template></el-table-column
        ><el-table-column label="操作" min-width="160"
          ><template #default="{ row }"
            ><div class="table-actions">
              <RouterLink :to="'/products/' + row.id" class="text-link small"
                >查看</RouterLink
              ><el-button link @click="start(row)">编辑</el-button
              ><el-button
                v-if="row.status === 'ON_SHELF'"
                link
                @click="off(row)"
                >下架</el-button
              ><el-button
                v-if="['OFF_SHELF', 'REJECTED'].includes(row.status)"
                link
                type="primary"
                @click="submit(row)"
                >重新提审</el-button
              >
            </div></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog
      v-model="edit"
      :title="editId ? '编辑并重新审核' : '发布商品'"
      width="750px"
      :close-on-click-modal="false"
      ><form class="form-stack" @submit.prevent="save">
        <label
          >商品名称<el-input v-model="form.name" required maxlength="100"
        /></label>
        <div class="form-grid">
          <label
            >类别<el-select v-model="form.categoryId" placeholder="请选择"
              ><el-option
                v-for="c in categories"
                :key="c.id"
                :label="c.name"
                :value="c.id" /></el-select></label
          ><label
            >新旧程度<el-select v-model="form.conditionText"
              ><el-option
                v-for="s in ['全新', '九成新', '八成新', '七成新及以下']"
                :key="s"
                :label="s"
                :value="s" /></el-select></label
          ><label
            >原价（元）<el-input-number
              v-model="form.originalPrice"
              :min="0.01"
              :precision="2"
              @change="priceFromDiscount" /></label
          ><label
            >折扣系数（0.8表示八折）<el-input-number
              v-model="form.discount"
              :min="0.01"
              :max="1"
              :step="0.05"
              :precision="2"
              @change="priceFromDiscount" /></label
          ><label
            >售价（元）<el-input-number
              v-model="form.price"
              :min="0.01"
              disabled
              :precision="2" /></label
          ><label
            >数量<el-input-number
              v-model="form.stock"
              :min="0"
              :max="100000"
              :precision="0" /></label
          ><label
            >尺寸 / 大小<el-input v-model="form.size" maxlength="100" /></label
          ><label
            >允许议价<el-switch
              v-model="form.negotiable"
              active-text="允许"
              inactive-text="不议价"
          /></label>
        </div>
        <label
          >商品图片（最多9张）<UploadField v-model="images" multiple /></label
        ><label
          >商品描述<el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="3000" /></label
        ><label
          >使用说明<el-input
            v-model="form.instructions"
            type="textarea"
            :rows="3"
            maxlength="1500"
        /></label>
        <p class="muted small">
          修改后暂时停止接受新订单，管理员审核通过后重新上架；已有订单不受影响。
        </p>
        <div class="dialog-footer">
          <el-button @click="edit = false">取消</el-button
          ><el-button type="primary" native-type="submit" :loading="saving"
            >提交审核</el-button
          >
        </div>
      </form></el-dialog
    >
  </WorkspaceLayout>
</template>
