<script setup>
import { ref, reactive, onMounted } from "vue";
import { get, patch, positive } from "../api.js";
import { refreshSession } from "../state.js";
import { label } from "../composables.js";
import { ElMessage } from "element-plus";
const busy = ref(false),
  saving = ref(false),
  error = ref(""),
  user = ref({}),
  credit = ref({}),
  form = reactive({
    name: "",
    phone: "",
    email: "",
    city: "",
    gender: "UNSPECIFIED",
    bankAccount: "",
  });
async function load() {
  busy.value = true;
  try {
    user.value = await get("/users/me");
    for (const key of ["name", "phone", "email", "city", "gender"])
      form[key] = user.value[key] || "";
    credit.value = await get("/users/" + user.value.id + "/reputation");
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function save() {
  if (form.bankAccount && !/^\d{16}$/.test(form.bankAccount)) {
    ElMessage.warning("新的银行账号必须为16位数字");
    return;
  }
  saving.value = true;
  try {
    const body = { ...form };
    if (!body.bankAccount) delete body.bankAccount;
    await patch("/users/me", body);
    await refreshSession();
    ElMessage.success("资料已保存");
    form.bankAccount = "";
    await load();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="page-content profile-shell" v-loading="busy">
    <div class="page-heading">
      <div>
        <h1>个人资料</h1>
        <p class="muted">
          账号 {{ user.username }} · {{ label(user.role) }} ·
          {{ label(user.status) }}
        </p>
      </div>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />
    <div class="info-panel">
      <p>
        卖家对我的好评率：<strong>{{
          positive(credit.buyerPositiveRate ?? credit.positiveRate)
        }}</strong>
      </p>
      <p v-if="credit.servicePositiveRate != null">
        买家对我的服务好评率：<strong>{{
          positive(credit.servicePositiveRate)
        }}</strong>
      </p>
      <p class="small muted">4～5星计为好评，买家行为与卖家服务分别统计。</p>
    </div>
    <form class="form-stack" @submit.prevent="save">
      <div class="form-grid">
        <label
          >姓名<el-input v-model="form.name" required maxlength="50" /></label
        ><label
          >手机号<el-input
            v-model="form.phone"
            required
            maxlength="11" /></label
        ><label
          >邮箱<el-input
            v-model="form.email"
            type="email"
            maxlength="100" /></label
        ><label>城市<el-input v-model="form.city" maxlength="50" /></label
        ><label
          >性别<el-select v-model="form.gender"
            ><el-option label="男" value="MALE" /><el-option
              label="女"
              value="FEMALE" /><el-option
              label="不透露"
              value="UNSPECIFIED" /></el-select></label
        ><label
          >修改银行账号<el-input
            v-model="form.bankAccount"
            maxlength="16"
            inputmode="numeric"
            placeholder="留空保持原账号，修改时输入16位数字"
        /></label>
      </div>
      <p class="muted small">
        当前银行账号：{{
          user.bankAccountMasked || user.bankAccount || "未显示"
        }}。修改操作会保留脱敏变更记录。
      </p>
      <div>
        <el-button native-type="submit" type="primary" :loading="saving"
          >保存资料</el-button
        >
      </div>
    </form>
  </section>
</template>
