<script setup>
import { ref, reactive, computed, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, post, resetCsrf } from "../api.js";
import { refreshSession } from "../state.js";
import UploadField from "../components/UploadField.vue";
import { ElMessage } from "element-plus";
const route = useRoute(),
  router = useRouter(),
  register = computed(() => route.path === "/register"),
  busy = ref(false),
  error = ref(""),
  captcha = ref(""),
  license = ref([]),
  identity = ref([]);
const form = reactive({
  username: "",
  password: "",
  captcha: "",
  role: "USER",
  name: "",
  phone: "",
  email: "",
  city: "",
  gender: "UNSPECIFIED",
  bankAccount: "",
  shopName: "",
});
async function refreshCaptcha() {
  try {
    const data = await get("/auth/captcha");
    captcha.value = data.image;
    form.captcha = "";
  } catch (e) {
    error.value = e.message;
  }
}
watch(
  () => route.path,
  () => {
    error.value = "";
    refreshCaptcha();
  },
  { immediate: true },
);
async function submit() {
  if (register.value && !/^\d{16}$/.test(form.bankAccount)) {
    error.value = "银行账号必须为16位数字";
    return;
  }
  if (
    register.value &&
    form.role === "MERCHANT" &&
    (!license.value.length || !identity.value.length)
  ) {
    error.value = "商家注册需要上传营业执照和身份证图片";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (register.value) {
      await post("/auth/registrations", {
        ...form,
        licenseFileId: license.value[0]?.id,
        identityFileId: identity.value[0]?.id,
      });
      ElMessage.success("注册资料已提交，管理员审核通过后可登录");
      router.push("/login");
    } else {
      await post("/auth/login", {
        username: form.username,
        password: form.password,
        captcha: form.captcha,
      });
      resetCsrf();
      const user = await refreshSession();
      ElMessage.success("登录成功");
      router.push(
        typeof route.query.redirect === "string" &&
          route.query.redirect.startsWith("/") &&
          !route.query.redirect.startsWith("//")
          ? route.query.redirect
          : user.role === "ADMIN"
            ? "/admin/registrations"
            : "/",
      );
    }
  } catch (e) {
    error.value = e.message;
    refreshCaptcha();
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="auth-page">
    <div class="auth-header">
      <h1>{{ register ? "创建校园交易账号" : "欢迎回来" }}</h1>
      <p class="muted">
        {{
          register
            ? "提交资料后，需管理员审核通过才能登录使用。"
            : "登录后购买好物，或发布你的闲置。"
        }}
      </p>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />
    <form @submit.prevent="submit" class="form-stack">
      <label v-if="register"
        >注册类型<el-radio-group v-model="form.role"
          ><el-radio-button value="USER">普通用户 / 个人卖家</el-radio-button
          ><el-radio-button value="MERCHANT"
            >商家</el-radio-button
          ></el-radio-group
        ></label
      ><label
        >账号<el-input
          v-model="form.username"
          required
          maxlength="32"
          autocomplete="username"
          placeholder="请输入账号" /></label
      ><label
        >密码<el-input
          v-model="form.password"
          required
          type="password"
          show-password
          minlength="8"
          maxlength="72"
          :autocomplete="register ? 'new-password' : 'current-password'"
          placeholder="至少8位，建议包含字母与数字"
      /></label>
      <template v-if="register"
        ><div class="form-grid">
          <label
            >姓名<el-input v-model="form.name" required maxlength="50" /></label
          ><label
            >手机号<el-input
              v-model="form.phone"
              required
              maxlength="11"
              inputmode="tel" /></label
          ><label
            >邮箱<el-input
              v-model="form.email"
              type="email"
              :required="form.role === 'USER'"
              maxlength="100" /></label
          ><label
            >城市<el-input
              v-model="form.city"
              :required="form.role === 'USER'"
              maxlength="50" /></label
          ><label
            >性别<el-select v-model="form.gender"
              ><el-option label="男" value="MALE" /><el-option
                label="女"
                value="FEMALE" /><el-option
                label="不透露"
                value="UNSPECIFIED" /></el-select></label
          ><label
            >银行账号<el-input
              v-model="form.bankAccount"
              required
              maxlength="16"
              minlength="16"
              inputmode="numeric"
              placeholder="16位数字"
          /></label>
        </div>
        <label
          >店铺名称<el-input
            v-model="form.shopName"
            maxlength="50"
            placeholder="例如：小林的闲置店" /></label
        ><template v-if="form.role === 'MERCHANT'"
          ><label
            >营业执照<UploadField v-model="license" purpose="LICENSE" /></label
          ><label
            >身份证图片<UploadField v-model="identity" purpose="IDENTITY"
          /></label>
          <p class="muted small">
            证件用于注册审核，仅本人和管理员有权查看。
          </p></template
        ></template
      >
      <label
        >图片验证码
        <div class="captcha-row">
          <el-input
            v-model="form.captcha"
            required
            maxlength="8"
            autocomplete="off"
            placeholder="输入图片中的字符"
          /><button
            type="button"
            @click="refreshCaptcha"
            title="点击刷新验证码"
          >
            <img
              v-if="captcha"
              :src="captcha"
              alt="注册登录图片验证码，点击刷新"
            /><span v-else>刷新验证码</span>
          </button>
        </div></label
      ><el-button
        class="wide-button"
        type="primary"
        native-type="submit"
        :loading="busy"
        >{{ register ? "提交注册资料" : "登录" }}</el-button
      >
    </form>
    <p class="auth-switch">
      {{ register ? "已有账号？" : "还没有账号？"
      }}<RouterLink :to="register ? '/login' : '/register'" class="text-link">{{
        register ? "去登录" : "注册账号"
      }}</RouterLink>
    </p>
  </section>
</template>
