<script setup>
import { ref, reactive, onMounted } from "vue";
import { get, getAll, post, del, listOf, dateTime } from "../api.js";
import { confirmAction } from "../composables.js";
import { ElMessage } from "element-plus";
const props = defineProps({ admin: Boolean }),
  rows = ref([]),
  busy = ref(false),
  saving = ref(false),
  dialog = ref(false),
  error = ref(""),
  form = reactive({ buyerId: null, reason: "" });
const path = () => (props.admin ? "/admin/blacklists" : "/seller/blacklists");
async function load() {
  busy.value = true;
  try {
    rows.value = await getAll(path());
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function add() {
  if (!form.buyerId || !form.reason.trim()) {
    ElMessage.warning("请填写买家编号及原因");
    return;
  }
  saving.value = true;
  try {
    await post(path(), form);
    ElMessage.success("已加入黑名单");
    dialog.value = false;
    form.buyerId = null;
    form.reason = "";
    load();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  if (
    !(await confirmAction(
      "解除后，该买家将恢复此范围内的新购买资格。是否继续？",
    ))
  )
    return;
  try {
    await del(path() + "/" + row.id);
    ElMessage.success("已解除拉黑");
    load();
  } catch (e) {
    ElMessage.error(e.message);
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="busy">
    <div class="page-heading">
      <div>
        <h1>{{ admin ? "平台黑名单" : "买家黑名单" }}</h1>
        <p class="muted">
          {{
            admin ? "限制全平台新购买" : "仅限制本店新购买"
          }}，不影响已有订单与退货权利。
        </p>
      </div>
      <el-button type="primary" @click="dialog = true">添加买家</el-button>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    /><el-table :data="rows" empty-text="暂无黑名单记录"
      ><el-table-column
        prop="buyerId"
        label="买家编号"
        width="100"
      /><el-table-column label="买家" min-width="120"
        ><template #default="{ row }">{{
          row.buyerName || row.name || "—"
        }}</template></el-table-column
      ><el-table-column
        prop="reason"
        label="原因"
        min-width="200"
      /><el-table-column label="时间" width="155"
        ><template #default="{ row }">{{
          dateTime(row.createdAt)
        }}</template></el-table-column
      ><el-table-column label="状态" width="100"
        ><template #default="{ row }">{{
          row.active ? "生效中" : "已解除"
        }}</template></el-table-column
      ><el-table-column label="操作" width="100"
        ><template #default="{ row }"
          ><el-button v-if="row.active" link type="primary" @click="remove(row)"
            >解除拉黑</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-dialog
      v-model="dialog"
      :title="admin ? '平台拉黑买家' : '本店拉黑买家'"
      width="470px"
      ><form class="form-stack" @submit.prevent="add">
        <label
          >买家用户编号<el-input-number
            v-model="form.buyerId"
            :min="1"
            :precision="0" /></label
        ><label
          >拉黑原因<el-input
            v-model="form.reason"
            required
            type="textarea"
            :rows="3"
            maxlength="400"
        /></label>
        <p class="muted small">仅限制新购买，已有订单及售后继续处理。</p>
        <el-button type="primary" native-type="submit" :loading="saving"
          >确认拉黑</el-button
        >
      </form></el-dialog
    >
  </div>
</template>
