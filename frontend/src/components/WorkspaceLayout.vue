<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
const route = useRoute();
const props = defineProps({ admin: Boolean });
const links = computed(() =>
  props.admin
    ? [
        ["registrations", "注册审核"],
        ["products", "商品审核"],
        ["users", "用户管理"],
        ["shops", "店铺管理"],
        ["levels", "商家等级"],
        ["fees", "手续费记录"],
        ["blacklists", "平台黑名单"],
      ]
    : [
        ["products", "商品管理"],
        ["orders", "订单管理"],
        ["returns", "退货管理"],
        ["blacklists", "买家黑名单"],
        ["wallet", "钱包与结算"],
      ],
);
</script>
<template>
  <div class="workspace">
    <aside class="workspace-nav">
      <h2>{{ admin ? "管理后台" : "卖家中心" }}</h2>
      <RouterLink
        v-for="[key, title] in links"
        :key="key"
        :class="{ active: route.params.section === key }"
        :to="
          key === 'wallet' ? '/wallet' : (admin ? '/admin/' : '/seller/') + key
        "
        >{{ title }}</RouterLink><RouterLink to="/" class="back-market"
        >返回商品市场 ↗</RouterLink
      >
    </aside>
    <section class="workspace-main"><slot /></section>
  </div>
</template>
