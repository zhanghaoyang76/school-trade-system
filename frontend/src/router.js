import { createRouter, createWebHistory } from "vue-router";
import { session, refreshSession } from "./state.js";
const routes = [
  { path: "/", component: () => import("./pages/CatalogPage.vue") },
  { path: "/shops/:id", component: () => import("./pages/CatalogPage.vue") },
  { path: "/products/:id", component: () => import("./pages/ProductPage.vue") },
  { path: "/login", component: () => import("./pages/AuthPage.vue") },
  { path: "/register", component: () => import("./pages/AuthPage.vue") },
  {
    path: "/cart",
    component: () => import("./pages/CartPage.vue"),
    meta: { auth: true },
  },
  {
    path: "/checkout",
    component: () => import("./pages/CartPage.vue"),
    meta: { auth: true },
  },
  {
    path: "/orders",
    component: () => import("./pages/OrdersPage.vue"),
    meta: { auth: true },
  },
  {
    path: "/returns",
    component: () => import("./pages/ReturnsPage.vue"),
    meta: { auth: true },
  },
  {
    path: "/wallet",
    component: () => import("./pages/WalletPage.vue"),
    meta: { auth: true },
  },
  {
    path: "/profile",
    component: () => import("./pages/ProfilePage.vue"),
    meta: { auth: true },
  },
  {
    path: "/seller/:section",
    component: () => import("./pages/SellerPage.vue"),
    meta: { auth: true, seller: true },
  },
  {
    path: "/admin/:section",
    component: () => import("./pages/AdminPage.vue"),
    meta: { auth: true, admin: true },
  },
  {
    path: "/:pathMatch(.*)*",
    component: () => import("./pages/NotFoundPage.vue"),
  },
];
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});
router.beforeEach(async (to) => {
  if (!session.ready) await refreshSession();
  if (to.meta.auth && !session.user)
    return { path: "/login", query: { redirect: to.fullPath } };
  if (to.meta.admin && session.user?.role !== "ADMIN") return "/";
  if (to.meta.seller && session.user?.role === "ADMIN")
    return "/admin/registrations";
});
export default router;
