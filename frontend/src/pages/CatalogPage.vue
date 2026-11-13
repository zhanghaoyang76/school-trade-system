<script setup>
import { ref, watch, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, listOf, money, positive } from "../api.js";
import CategoryIcon from "../components/CategoryIcon.vue";
const route = useRoute(),
  router = useRouter(),
  busy = ref(false),
  error = ref(""),
  products = ref([]),
  categories = ref([]),
  total = ref(0),
  page = ref(1),
  sort = ref("default"),
  category = ref(""),
  shop = ref(null);
const title = computed(
  () =>
    shop.value?.name ||
    shop.value?.shopName ||
    (route.query.keyword ? "搜索结果" : "发现校园好物"),
);
let loadVersion = 0;
async function load() {
  const version = ++loadVersion,
    shopId = route.params.id;
  products.value = [];
  shop.value = null;
  busy.value = true;
  error.value = "";
  try {
    const q = new URLSearchParams({
      page: String(page.value),
      size: "12",
      sort: sort.value,
    });
    if (route.query.keyword) q.set("keyword", route.query.keyword);
    if (category.value) q.set("categoryId", category.value);
    const data = await get(
      (shopId ? `/shops/${shopId}/products` : "/products") + "?" + q,
    );
    const shopData = shopId ? await get("/shops/" + shopId) : null;
    if (version !== loadVersion) return;
    products.value = listOf(data);
    total.value = data.total ?? products.value.length;
    shop.value = shopData;
  } catch (e) {
    if (version === loadVersion) error.value = e.message;
  } finally {
    if (version === loadVersion) busy.value = false;
  }
}
async function loadCategories() {
  try {
    categories.value = listOf(await get("/categories"));
  } catch (e) {
    error.value = e.message;
  }
}
function filter() {
  page.value = 1;
  load();
}
watch(
  () => route.fullPath,
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
onMounted(loadCategories);
</script>
<template>
  <section class="catalog">
    <nav class="category-tabs" aria-label="商品类别">
      <button
        :class="{ active: category === '' }"
        @click="
          category = '';
          filter();
        "
      >
        <CategoryIcon />全部商品</button
      ><button
        v-for="(c, i) in categories"
        :key="c.id"
        :class="{ active: category === String(c.id) }"
        @click="
          category = String(c.id);
          filter();
        "
      >
        <CategoryIcon :name="c.name" />{{ c.name }}
      </button>
    </nav>
    <div class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p class="muted">
          {{ total }} 件商品 ·
          {{ shop ? "店铺商品" : "来自校园里的个人卖家与商家" }}
        </p>
        <p v-if="shop?.status === 'CLOSED'" class="error-text">
          店铺暂时关闭，已有订单和售后仍可处理。
        </p>
      </div>
      <el-select
        v-model="sort"
        class="sort-select"
        aria-label="商品排序"
        @change="filter"
        ><el-option label="综合排序" value="default" /><el-option
          label="价格从低到高"
          value="priceAsc" /><el-option
          label="价格从高到低"
          value="priceDesc" /><el-option
          label="销量优先"
          value="sales" /><el-option label="好评率优先" value="positive"
      /></el-select>
    </div>
    <section v-if="shop" class="shop-summary">
      <dl class="summary-grid">
        <div>
          <dt>历史销量</dt>
          <dd>{{ shop.soldCount || 0 }} <small>件</small></dd>
        </div>
        <div>
          <dt>服务好评率</dt>
          <dd>{{ positive(shop.servicePositiveRate) }}</dd>
        </div>
        <div>
          <dt>店铺状态</dt>
          <dd class="shop-status">
            {{ shop.status === "CLOSED" ? "暂时关闭" : "营业中" }}
          </dd>
        </div>
        <div>
          <dt>卖家</dt>
          <dd class="shop-status">{{ shop.sellerName || shop.name }}</dd>
        </div>
      </dl>
      <details v-if="shop.reviews?.length" class="shop-reviews">
        <summary>
          查看购买者评价（{{ shop.reviewCount ?? shop.reviews.length }}条）
        </summary>
        <article v-for="r in shop.reviews" :key="r.id" class="review-item">
          <strong>{{ r.authorName || r.reviewerName || "购买者" }}</strong>
          <p class="muted small">
            商品 {{ r.productStars }} 星 · 服务 {{ r.serviceStars }} 星
          </p>
          <p>{{ r.content }}</p>
        </article>
      </details>
      <p v-else class="muted small">暂无购买者评价</p>
    </section>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      ><el-button
        link
        @click="
          load();
          loadCategories();
        "
        >重试</el-button
      ></el-alert
    >
    <div v-loading="busy" class="product-grid">
      <article v-for="p in products" :key="p.id" class="product-card">
        <RouterLink :to="'/products/' + p.id" class="product-image"
          ><img v-if="p.imageUrls?.[0]" :src="p.imageUrls[0]" :alt="p.name" />
          <div v-else class="image-empty">暂无商品图片</div>
          <span class="condition-tag">{{ p.conditionText }}</span></RouterLink
        >
        <div class="product-copy">
          <div class="product-title-row">
            <RouterLink :to="'/products/' + p.id" class="product-name">{{
              p.name
            }}</RouterLink><span class="rating">{{
              p.positiveRate == null
                ? "暂无评价"
                : "★ " + positive(p.positiveRate)
            }}</span>
          </div>
          <RouterLink :to="'/shops/' + p.shopId" class="seller-name">{{
            p.shopName || p.sellerName
          }}</RouterLink>
          <p class="product-meta">
            库存 {{ p.stock }} 件 · 已售 {{ p.soldCount || 0 }} 件
          </p>
          <div class="product-price">
            ¥{{ money(p.price)
            }}<del v-if="Number(p.originalPrice) > Number(p.price)"
              >¥{{ money(p.originalPrice) }}</del
            ><small v-if="p.negotiable">可议价</small>
          </div>
        </div>
      </article>
      <div v-if="!busy && !products.length && !error" class="empty-page">
        <h2>暂时没有找到商品</h2>
        <p class="muted">试试其他名称或分类，或发布你的第一件闲置。</p>
        <RouterLink to="/seller/products" class="text-link"
          >发布闲置 ↗</RouterLink
        >
      </div>
    </div>
    <el-pagination
      v-if="total > 12"
      v-model:current-page="page"
      :page-size="12"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
    <p v-else-if="products.length" class="list-end">已展示全部商品</p>
  </section>
</template>
