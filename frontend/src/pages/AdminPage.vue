<script setup>
import { ref, reactive, computed, watch } from "vue";
import { useRoute } from "vue-router";
import {
  get,
  getAll,
  post,
  patch,
  del,
  listOf,
  money,
  dateTime,
} from "../api.js";
import { askReason, confirmAction, label } from "../composables.js";
import { ElMessage } from "element-plus";
import WorkspaceLayout from "../components/WorkspaceLayout.vue";
import BlacklistPanel from "../components/BlacklistPanel.vue";
const route = useRoute(),
  section = computed(() => route.params.section),
  rows = ref([]),
  busy = ref(false),
  saving = ref(false),
  error = ref(""),
  keyword = ref(""),
  status = ref(""),
  detail = ref(null),
  editUser = ref(null),
  recharge = ref(null),
  levelDialog = ref(null);
const rechargeForm = reactive({ amount: 100, requestKey: "" }),
  levelForm = reactive({ mode: "MANUAL", level: 1 });
const userForm = reactive({
  name: "",
  phone: "",
  email: "",
  city: "",
  gender: "UNSPECIFIED",
  bankAccount: "",
});
const titles = {
  registrations: "注册审核",
  products: "商品审核",
  users: "用户管理",
  shops: "店铺管理",
  levels: "商家等级",
  fees: "手续费记录",
};
const descriptions = {
  registrations: "核对用户及商家材料，通过后才能正式登录。",
  products: "审核商品内容，审核通过后才能展示购买。",
  users: "维护用户资料、钱包充值，删除前检查未完结交易。",
  shops: "关闭店铺会下架全部商品，已有订单和售后继续。",
  levels: "系统自动计算等级，也可手动指定并暂停自动调整。",
  fees: "查看交易收费与退货冲回，按订单下单费率计算。",
};
const visible = computed(() =>
  rows.value.filter(
    (r) =>
      (!keyword.value ||
        [r.name, r.username, r.shopName, r.sellerName, r.id].some((v) =>
          String(v || "").includes(keyword.value),
        )) &&
      (!status.value || r.status === status.value),
  ),
);
let revision = 0,
  detailRevision = 0;
async function load() {
  const version = ++revision,
    origin = section.value;
  if (section.value === "blacklists") return;
  busy.value = true;
  error.value = "";
  try {
    const endpoints = {
      registrations: "/admin/registrations",
      products: "/admin/products",
      users: "/admin/users",
      shops: "/admin/shops",
      levels: "/admin/merchants",
      fees: "/admin/fees",
    };
    const result = await getAll(endpoints[origin] || "/admin/registrations");
    if (version === revision && origin === section.value) rows.value = result;
  } catch (e) {
    if (version === revision) error.value = e.message;
  } finally {
    if (version === revision) busy.value = false;
  }
}
async function act(fn, message) {
  saving.value = true;
  try {
    await fn();
    ElMessage.success(message);
    await load();
    return true;
  } catch (e) {
    ElMessage.error(e.message);
    return false;
  } finally {
    saving.value = false;
  }
}
async function review(row, approved) {
  const origin = section.value,
    version = revision;
  if (!["products", "registrations"].includes(origin)) return;
  let reason = "";
  if (!approved) {
    reason = await askReason("填写审核拒绝原因");
    if (!reason) return;
  } else if (
    !(await confirmAction(
      origin === "products"
        ? "通过后该商品将上架并可购买，是否确认？"
        : "确认资料完整且符合注册要求？通过后账号可正式使用。",
    ))
  )
    return;
  if (origin !== section.value || version !== revision) {
    ElMessage.warning("页面已切换，请在当前列表重新选择审核对象");
    return;
  }
  await act(
    () =>
      post(
        "/admin/" +
          (origin === "products" ? "products" : "registrations") +
          "/" +
          row.id +
          "/reviews",
        { approved, reason },
      ),
    "审核已处理",
  );
  detail.value = null;
}
async function view(row) {
  const version = ++detailRevision,
    context = revision,
    origin = section.value;
  if (section.value === "products") {
    try {
      const result = await get("/products/" + row.id);
      if (
        version === detailRevision &&
        context === revision &&
        origin === section.value
      )
        detail.value = result;
    } catch (e) {
      if (version === detailRevision && context === revision)
        ElMessage.error(e.message);
    }
  } else detail.value = row;
}
function startEdit(row) {
  editUser.value = row;
  for (const key of ["name", "phone", "email", "city", "gender"])
    userForm[key] = row[key] || "";
  userForm.bankAccount = "";
}
async function saveUser() {
  if (userForm.bankAccount && !/^\d{16}$/.test(userForm.bankAccount)) {
    ElMessage.warning("银行账号必须为16位数字");
    return;
  }
  const body = { ...userForm };
  if (!body.bankAccount) delete body.bankAccount;
  if (
    await act(
      () => patch("/admin/users/" + editUser.value.id, body),
      "用户资料已更新",
    )
  )
    editUser.value = null;
}
async function deleteUser(row) {
  if (
    await confirmAction(
      "删除将停用账号、关闭店铺并下架商品，历史交易会保留；有未完成交易时将拒绝删除。是否继续？",
    )
  )
    act(() => del("/admin/users/" + row.id), "用户已删除");
}
function startRecharge(row) {
  recharge.value = row;
  rechargeForm.amount = 100;
  rechargeForm.requestKey = crypto.randomUUID();
}
async function sendRecharge() {
  if (rechargeForm.amount <= 0) {
    ElMessage.warning("充值金额必须大于0");
    return;
  }
  if (
    await act(
      () =>
        post("/admin/users/" + recharge.value.id + "/recharges", rechargeForm),
      "钱包充值成功",
    )
  )
    recharge.value = null;
}
async function changeShop(row, close) {
  const message = close
    ? "关闭后全部商品下架，禁止新发布和购买；已有订单和售后继续。"
    : "恢复后允许营业，原商品保持下架，卖家需重新提交审核。";
  if (await confirmAction(message))
    act(
      () =>
        post("/admin/shops/" + row.id + (close ? "/closures" : "/reopenings")),
      close ? "店铺已关闭" : "店铺已恢复",
    );
}
function startLevel(row) {
  levelDialog.value = row;
  levelForm.mode = row.levelMode || "AUTO";
  levelForm.level = row.level || 1;
}
async function saveLevel() {
  if (
    await act(
      () => patch("/admin/shops/" + levelDialog.value.id + "/level", levelForm),
      "商家等级设置已保存",
    )
  )
    levelDialog.value = null;
}
watch(
  section,
  () => {
    revision++;
    detailRevision++;
    rows.value = [];
    detail.value = null;
    editUser.value = null;
    recharge.value = null;
    levelDialog.value = null;
    busy.value = false;
    keyword.value = "";
    status.value = "";
    load();
  },
  { immediate: true },
);
</script>
<template>
  <WorkspaceLayout admin
    ><BlacklistPanel v-if="section === 'blacklists'" admin />
    <section v-else v-loading="busy">
      <div class="page-heading">
        <div>
          <h1>{{ titles[section] || "管理后台" }}</h1>
          <p class="muted">{{ descriptions[section] }}</p>
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
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="按名称、账号或编号筛选"
          clearable
        /><el-select
          v-if="['registrations', 'products', 'shops'].includes(section)"
          v-model="status"
          placeholder="全部状态"
          clearable
          ><template v-if="section === 'registrations'"
            ><el-option label="待审核" value="PENDING" /><el-option
              label="已通过"
              value="APPROVED" /><el-option
              label="已拒绝"
              value="REJECTED" /></template
          ><template v-else-if="section === 'products'"
            ><el-option label="待审核" value="PENDING" /><el-option
              label="出售中"
              value="ON_SHELF" /><el-option
              label="已下架"
              value="OFF_SHELF" /><el-option
              label="已拒绝"
              value="REJECTED" /></template
          ><template v-else
            ><el-option label="营业中" value="OPEN" /><el-option
              label="已关闭"
              value="CLOSED" /></template
        ></el-select>
      </div>
      <template v-if="section === 'levels'"
        ><div class="info-panel">
          <p>
            等级1至5费率分别为0.1%、0.2%、0.5%、0.75%、1%，按课程要求保留。个人卖家不收费。
          </p>
          <p class="small muted">
            自动等级取满足成交额与服务好评率门槛的最高级；手动指定后暂停自动调整，恢复自动模式后重新计算。
          </p>
        </div></template
      >
      <el-table :data="visible" empty-text="暂无记录">
        <template v-if="section === 'registrations' || section === 'users'"
          ><el-table-column prop="id" label="编号" width="70" /><el-table-column
            label="账号 / 姓名"
            min-width="150"
            ><template #default="{ row }"
              ><strong>{{ row.username }}</strong>
              <div class="muted small">
                {{ row.name }} · {{ label(row.role) }}
              </div></template
            ></el-table-column
          ><el-table-column
            prop="phone"
            label="手机号"
            min-width="130"
          /><el-table-column label="状态" width="100"
            ><template #default="{ row }">{{
              row.deleted ? "已删除" : label(row.status)
            }}</template></el-table-column
          ><el-table-column label="操作" min-width="230"
            ><template #default="{ row }"
              ><div class="table-actions">
                <el-button link @click="view(row)">查看</el-button
                ><template
                  v-if="section === 'registrations' && row.status === 'PENDING'"
                  ><el-button
                    link
                    type="primary"
                    :disabled="saving"
                    @click="review(row, true)"
                    >通过</el-button
                  ><el-button
                    link
                    :disabled="saving"
                    @click="review(row, false)"
                    >拒绝</el-button
                  ></template
                ><template v-if="section === 'users' && !row.deleted"
                  ><el-button link @click="startEdit(row)">修改</el-button
                  ><el-button link type="primary" @click="startRecharge(row)"
                    >充值</el-button
                  ><el-button link :disabled="saving" @click="deleteUser(row)"
                    >删除</el-button
                  ></template
                >
              </div></template
            ></el-table-column
          ></template
        >
        <template v-else-if="section === 'products'"
          ><el-table-column prop="id" label="编号" width="70" /><el-table-column
            label="商品"
            min-width="200"
            ><template #default="{ row }"
              ><strong>{{ row.name }}</strong>
              <div class="muted small">
                {{ row.shopName || row.sellerName }} · {{ row.conditionText }}
              </div>
              <div v-if="row.reviewReason" class="muted small">
                {{ row.reviewReason }}
              </div></template
            ></el-table-column
          ><el-table-column label="售价" width="110"
            ><template #default="{ row }"
              >¥{{ money(row.price) }}</template
            ></el-table-column
          ><el-table-column
            prop="stock"
            label="库存"
            width="75"
          /><el-table-column label="状态" width="100"
            ><template #default="{ row }">{{
              label(row.status)
            }}</template></el-table-column
          ><el-table-column label="操作" min-width="160"
            ><template #default="{ row }"
              ><div class="table-actions">
                <el-button link @click="view(row)">查看</el-button
                ><template v-if="row.status === 'PENDING'"
                  ><el-button
                    link
                    type="primary"
                    :disabled="saving"
                    @click="review(row, true)"
                    >通过</el-button
                  ><el-button
                    link
                    :disabled="saving"
                    @click="review(row, false)"
                    >拒绝</el-button
                  ></template
                >
              </div></template
            ></el-table-column
          ></template
        >
        <template v-else-if="section === 'shops' || section === 'levels'"
          ><el-table-column
            prop="id"
            label="店铺编号"
            width="95"
          /><el-table-column label="店铺 / 卖家" min-width="170"
            ><template #default="{ row }"
              ><strong>{{ row.name || row.shopName }}</strong>
              <div class="muted small">
                {{
                  row.sellerName || row.username || "卖家编号 " + row.sellerId
                }}
              </div></template
            ></el-table-column
          ><el-table-column label="状态" width="100"
            ><template #default="{ row }">{{
              label(row.status)
            }}</template></el-table-column
          ><el-table-column
            v-if="section === 'levels'"
            label="等级 / 模式"
            width="130"
            ><template #default="{ row }"
              >{{ row.level }} 级 · {{ label(row.levelMode) }}</template
            ></el-table-column
          ><el-table-column
            v-if="section === 'levels'"
            label="累计成交 / 服务好评"
            min-width="170"
            ><template #default="{ row }"
              >¥{{ money(row.turnover || row.totalSales) }}
              <div class="muted small">
                {{
                  row.positiveRate == null ? "暂无评价" : row.positiveRate + "%"
                }}
              </div></template
            ></el-table-column
          ><el-table-column label="操作" min-width="150"
            ><template #default="{ row }"
              ><div class="table-actions">
                <RouterLink :to="'/shops/' + row.id" class="text-link small"
                  >查看店铺</RouterLink
                ><el-button
                  v-if="section === 'levels'"
                  link
                  type="primary"
                  @click="startLevel(row)"
                  >设置等级</el-button
                ><el-button
                  v-else
                  link
                  type="primary"
                  :disabled="saving"
                  @click="changeShop(row, row.status !== 'CLOSED')"
                  >{{
                    row.status === "CLOSED" ? "恢复营业" : "关闭店铺"
                  }}</el-button
                >
              </div></template
            ></el-table-column
          ></template
        >
        <template v-else-if="section === 'fees'"
          ><el-table-column
            prop="id"
            label="流水编号"
            width="95" /><el-table-column label="时间" min-width="150"
            ><template #default="{ row }">{{
              dateTime(row.createdAt)
            }}</template></el-table-column
          ><el-table-column
            prop="orderId"
            label="关联订单"
            width="110" /><el-table-column label="业务" min-width="150"
            ><template #default="{ row }">{{
              row.type === "REFUND"
                ? "退货冲回"
                : row.type === "FEE"
                  ? "收取手续费"
                  : row.type || row.businessKey
            }}</template></el-table-column
          ><el-table-column label="手续费变动" min-width="130"
            ><template #default="{ row }"
              >{{ Number(row.availableDelta) > 0 ? "+" : ""
              }}{{ money(row.availableDelta ?? row.fee) }} 元</template
            ></el-table-column
          ><el-table-column
            prop="businessKey"
            label="业务流水号"
            min-width="220"
        /></template>
      </el-table>
    </section>
    <el-dialog
      :model-value="!!detail"
      :title="section === 'products' ? '商品审核详情' : '用户资料'"
      width="680px"
      @close="detail = null"
      ><template v-if="detail"
        ><el-descriptions :column="2" border
          ><el-descriptions-item label="编号">{{
            detail.id
          }}</el-descriptions-item
          ><el-descriptions-item label="名称">{{
            detail.name
          }}</el-descriptions-item
          ><el-descriptions-item v-if="section !== 'products'" label="账号">{{
            detail.username
          }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{
            label(detail.status)
          }}</el-descriptions-item
          ><template v-if="section !== 'products'"
            ><el-descriptions-item label="类型">{{
              label(detail.role)
            }}</el-descriptions-item
            ><el-descriptions-item label="手机号">{{
              detail.phone
            }}</el-descriptions-item
            ><el-descriptions-item label="邮箱">{{
              detail.email
            }}</el-descriptions-item
            ><el-descriptions-item label="城市">{{
              detail.city
            }}</el-descriptions-item
            ><el-descriptions-item label="性别">{{
              { MALE: "男", FEMALE: "女", UNSPECIFIED: "不透露" }[
                detail.gender
              ] || detail.gender
            }}</el-descriptions-item
            ><el-descriptions-item label="银行账号">{{
              detail.bankAccountMasked || detail.bankAccount
            }}</el-descriptions-item></template
          ><template v-else
            ><el-descriptions-item label="售价"
              >¥{{ money(detail.price) }}</el-descriptions-item
            ><el-descriptions-item label="原价 / 折扣"
              >¥{{ money(detail.originalPrice) }} /
              {{ detail.discount }}</el-descriptions-item
            ><el-descriptions-item label="库存">{{
              detail.stock
            }}</el-descriptions-item
            ><el-descriptions-item label="成色">{{
              detail.conditionText
            }}</el-descriptions-item
            ><el-descriptions-item label="尺寸">{{
              detail.size
            }}</el-descriptions-item
            ><el-descriptions-item label="议价">{{
              detail.negotiable ? "允许" : "不允许"
            }}</el-descriptions-item
            ><el-descriptions-item label="描述" :span="2">{{
              detail.description
            }}</el-descriptions-item
            ><el-descriptions-item label="使用说明" :span="2">{{
              detail.instructions
            }}</el-descriptions-item></template
          ></el-descriptions
        >
        <div class="audit-images" style="margin-top: 20px">
          <template v-if="section === 'products'"
            ><el-image
              v-for="url in detail.imageUrls"
              :key="url"
              :src="url"
              :preview-src-list="detail.imageUrls"
              style="width: 140px; height: 130px"
              fit="cover" /></template
          ><template v-else
            ><div v-if="detail.licenseFileId">
              <p>营业执照</p>
              <el-image
                :src="'/api/files/' + detail.licenseFileId"
                :preview-src-list="['/api/files/' + detail.licenseFileId]"
                style="width: 180px; height: 160px"
                fit="contain"
              />
            </div>
            <div v-if="detail.identityFileId">
              <p>身份证图片</p>
              <el-image
                :src="'/api/files/' + detail.identityFileId"
                :preview-src-list="['/api/files/' + detail.identityFileId]"
                style="width: 180px; height: 160px"
                fit="contain"
              /></div
          ></template>
        </div>
        <p v-if="detail.reviewReason" class="muted">
          审核说明：{{ detail.reviewReason }}
        </p>
        <div v-if="detail.status === 'PENDING'" class="dialog-footer">
          <el-button :loading="saving" @click="review(detail, false)"
            >拒绝</el-button
          ><el-button
            type="primary"
            :loading="saving"
            @click="review(detail, true)"
            >审核通过</el-button
          >
        </div></template
      ></el-dialog
    >
    <el-dialog
      :model-value="!!editUser"
      title="修改用户资料"
      width="600px"
      @close="editUser = null"
      ><form class="form-stack" @submit.prevent="saveUser">
        <div class="form-grid">
          <label>姓名<el-input v-model="userForm.name" required /></label
          ><label
            >手机号<el-input
              v-model="userForm.phone"
              maxlength="11"
              required /></label
          ><label>邮箱<el-input v-model="userForm.email" type="email" /></label
          ><label>城市<el-input v-model="userForm.city" /></label
          ><label
            >性别<el-select v-model="userForm.gender"
              ><el-option label="男" value="MALE" /><el-option
                label="女"
                value="FEMALE" /><el-option
                label="不透露"
                value="UNSPECIFIED" /></el-select></label
          ><label
            >新银行账号<el-input
              v-model="userForm.bankAccount"
              maxlength="16"
              placeholder="留空不修改"
          /></label>
        </div>
        <el-button type="primary" native-type="submit" :loading="saving"
          >保存修改</el-button
        >
      </form></el-dialog
    >
    <el-dialog
      :model-value="!!recharge"
      title="钱包充值"
      width="460px"
      @close="recharge = null"
      ><form class="form-stack" @submit.prevent="sendRecharge">
        <p>充值用户：{{ recharge?.name }}（{{ recharge?.username }}）</p>
        <label
          >充值金额（元）<el-input-number
            v-model="rechargeForm.amount"
            :min="0.01"
            :max="1000000"
            :precision="2"
        /></label>
        <p class="muted small">仅增加模拟钱包余额，系统记录充值流水。</p>
        <el-button native-type="submit" type="primary" :loading="saving"
          >确认充值</el-button
        >
      </form></el-dialog
    >
    <el-dialog
      :model-value="!!levelDialog"
      title="设置商家等级"
      width="480px"
      @close="levelDialog = null"
      ><form class="form-stack" @submit.prevent="saveLevel">
        <p>{{ levelDialog?.name || levelDialog?.shopName }}</p>
        <label
          >等级模式<el-radio-group v-model="levelForm.mode"
            ><el-radio-button value="AUTO">自动调整</el-radio-button
            ><el-radio-button value="MANUAL"
              >手动指定</el-radio-button
            ></el-radio-group
          ></label
        ><label v-if="levelForm.mode === 'MANUAL'"
          >指定等级<el-select v-model="levelForm.level"
            ><el-option
              v-for="(fee, i) in ['0.1%', '0.2%', '0.5%', '0.75%', '1%']"
              :key="i"
              :label="i + 1 + '级 · 手续费' + fee"
              :value="i + 1" /></el-select
        ></label>
        <p class="muted small">
          手动模式暂停自动调整，恢复自动后根据真实成交额和服务好评率重新计算。
        </p>
        <el-button type="primary" native-type="submit" :loading="saving"
          >保存设置</el-button
        >
      </form></el-dialog
    >
  </WorkspaceLayout>
</template>
