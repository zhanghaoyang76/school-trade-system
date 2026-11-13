<script setup>
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { session, logout } from "./state.js";
import { ElMessage } from "element-plus";
const route = useRoute(),
  router = useRouter(),
  query = ref(""),
  menu = ref(false);
watch(
  () => route.fullPath,
  () => {
    menu.value = false;
    query.value = route.query.keyword || "";
  },
  { immediate: true },
);
function search() {
  router.push({
    path: "/",
    query: query.value ? { keyword: query.value } : {},
  });
}
async function signOut() {
  try {
    await logout();
    router.push("/");
    ElMessage.success("已退出登录");
  } catch (e) {
    ElMessage.error(e.message);
  }
}
</script>
<template>
  <div class="app-wrap">
    <header class="site-header">
      <RouterLink to="/" class="brand"
        ><svg
          viewBox="0 0 30 30"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          aria-hidden="true"
        >
          <path d="M5 10h15l-4-4M25 20H10l4 4M5 10v11M25 20V9" /></svg
        ><span>校园二手<small>CAMPUS MARKET</small></span></RouterLink
      >
      <form class="global-search" @submit.prevent="search">
        <input
          v-model="query"
          aria-label="搜索商品名称"
          placeholder="搜索商品名称"
        /><button aria-label="搜索">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
          >
            <circle cx="10" cy="10" r="6.5" />
            <path d="m15 15 5 5" />
          </svg>
        </button>
      </form>
      <nav class="header-actions">
        <RouterLink
          v-if="session.user?.role !== 'ADMIN'"
          to="/cart"
          class="cart-link"
          >购物车</RouterLink
        ><RouterLink
          v-if="session.user?.role !== 'ADMIN'"
          to="/seller/products"
          class="publish-link"
          >发布闲置</RouterLink
        ><RouterLink v-else to="/admin/registrations" class="publish-link"
          >管理后台</RouterLink
        >
        <div v-if="session.user" class="account-menu">
          <button
            class="account-button"
            @click="menu = !menu"
            :aria-expanded="menu"
          >
            {{ session.user.name || session.user.username }} ⌄
          </button>
          <div v-if="menu" class="menu-panel">
            <RouterLink v-if="session.user.role !== 'ADMIN'" to="/orders"
              >我的订单</RouterLink
            ><RouterLink v-if="session.user.role !== 'ADMIN'" to="/returns"
              >退货进度</RouterLink
            ><RouterLink v-if="session.user.role !== 'ADMIN'" to="/wallet"
              >钱包与积分</RouterLink
            ><RouterLink to="/profile">个人资料</RouterLink
            ><RouterLink
              :to="
                session.user.role === 'ADMIN'
                  ? '/admin/registrations'
                  : '/seller/products'
              "
              >{{
                session.user.role === "ADMIN" ? "管理后台" : "卖家中心"
              }}</RouterLink
            ><button @click="signOut">退出登录</button>
          </div>
        </div>
        <RouterLink v-else to="/login" class="login-link">登录</RouterLink>
      </nav>
    </header>
    <el-alert
      v-if="session.error"
      :title="session.error"
      type="warning"
      show-icon
      :closable="false"
      class="connection-alert"
    />
    <main><RouterView /></main>
    <footer class="site-footer">
      <span>校园二手商品交易平台</span>
      <nav>
        <RouterLink to="/">商品市场</RouterLink
        ><RouterLink v-if="session.user?.role !== 'ADMIN'" to="/seller/products"
          >卖家中心</RouterLink
        ><RouterLink
          v-if="session.user?.role === 'ADMIN'"
          to="/admin/registrations"
          >管理后台</RouterLink
        >
      </nav>
      <span>线上下单 · 线下交付</span>
    </footer>
  </div>
</template>
