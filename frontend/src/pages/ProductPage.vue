<script setup>
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, post, listOf, money, positive } from "../api.js";
import { session } from "../state.js";
import { ElMessage } from "element-plus";
const route = useRoute(),
  router = useRouter(),
  product = ref(null),
  reviews = ref([]),
  busy = ref(false),
  error = ref(""),
  selected = ref(0),
  quantity = ref(1),
  adding = ref(false);
let loadVersion = 0;
async function load() {
  const version = ++loadVersion,
    id = route.params.id;
  product.value = null;
  reviews.value = [];
  quantity.value = 1;
  busy.value = true;
  error.value = "";
  selected.value = 0;
  try {
    const [p, r] = await Promise.all([
      get("/products/" + id),
      get("/products/" + id + "/reviews"),
    ]);
    if (version !== loadVersion) return;
    product.value = p;
    reviews.value = listOf(r);
  } catch (e) {
    if (version === loadVersion) error.value = e.message;
  } finally {
    if (version === loadVersion) busy.value = false;
  }
}
watch(() => route.params.id, load, { immediate: true });
async function add(buy = false) {
  if (!product.value || busy.value || error.value) return;
  if (!session.user) {
    router.push({ path: "/login", query: { redirect: route.fullPath } });
    return;
  }
  adding.value = true;
  try {
    await post("/cart/items", {
      productId: product.value.id,
      quantity: quantity.value,
    });
    ElMessage.success("已加入购物车");
    if (buy) router.push("/cart");
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    adding.value = false;
  }
}
</script>
<template>
  <section v-loading="busy" class="product-detail-page">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    /><template v-if="product"
      ><div class="breadcrumbs">
        <RouterLink to="/">商品市场</RouterLink><span> / </span
        ><RouterLink :to="'/shops/' + product.shopId">{{
          product.shopName
        }}</RouterLink>
      </div>
      <div class="product-detail-grid">
        <div class="product-gallery">
          <img
            v-if="product.imageUrls?.length"
            :src="product.imageUrls[selected]"
            :alt="product.name"
            class="main-photo"
          />
          <div v-else class="main-photo image-empty">暂无商品图片</div>
          <div class="thumbnails">
            <button
              v-for="(url, i) in product.imageUrls"
              :key="url"
              @click="selected = i"
              :class="{ active: selected === i }"
              :aria-label="'查看第' + (i + 1) + '张图片'"
            >
              <img :src="url" alt="商品图片缩略图" />
            </button>
          </div>
        </div>
        <div class="product-summary">
          <p class="muted">
            {{ product.conditionText }} ·
            {{ product.negotiable ? "允许议价" : "不议价" }}
          </p>
          <h1>{{ product.name }}</h1>
          <p class="rating">
            {{
              product.positiveRate == null
                ? "暂无评价"
                : "★ " + positive(product.positiveRate) + " 好评"
            }}
            · 已售 {{ product.soldCount || 0 }} 件
          </p>
          <div class="detail-price">
            ¥{{ money(product.price)
            }}<del v-if="Number(product.originalPrice) > Number(product.price)"
              >¥{{ money(product.originalPrice) }}</del
            >
          </div>
          <dl class="product-facts">
            <dt>卖家</dt>
            <dd>
              <RouterLink :to="'/shops/' + product.shopId" class="text-link"
                >{{ product.shopName || product.sellerName }} ↗</RouterLink
              >
            </dd>
            <dt>尺寸 / 大小</dt>
            <dd>{{ product.size || "未填写" }}</dd>
            <dt>库存</dt>
            <dd>{{ product.stock }} 件</dd>
            <dt>交易方式</dt>
            <dd>线上下单，约定线下交付</dd>
          </dl>
          <div class="quantity-line">
            <span>购买数量</span
            ><el-input-number
              v-model="quantity"
              :min="1"
              :max="Math.max(1, product.stock)"
            />
          </div>
          <div class="purchase-actions">
            <el-button
              :disabled="product.stock < 1 || product.status !== 'ON_SHELF'"
              :loading="adding"
              @click="add()"
              >加入购物车</el-button
            ><el-button
              type="primary"
              :disabled="product.stock < 1 || product.status !== 'ON_SHELF'"
              :loading="adding"
              @click="add(true)"
              >立即购买</el-button
            >
          </div>
          <p class="muted small">
            {{
              product.stock < 1
                ? "当前商品已售罄。"
                : "确认收货后24小时内可按规则申请退货。"
            }}{{
              product.negotiable
                ? " 如需议价，请先与卖家在线下协商，按审核后的售价下单。"
                : ""
            }}
          </p>
        </div>
      </div>
      <div class="detail-bottom">
        <section>
          <h2>商品说明</h2>
          <p class="description-text">
            {{ product.description || product.instructions || "暂无说明" }}
          </p>
          <template v-if="product.description && product.instructions"
            ><h3>使用说明</h3>
            <p class="description-text">{{ product.instructions }}</p></template
          >
        </section>
        <section>
          <h2>
            购买者评价 <span class="muted small">{{ reviews.length }} 条</span>
          </h2>
          <p v-if="!reviews.length" class="muted">
            暂无评价，完成购买后可以分享使用感受。
          </p>
          <article v-for="r in reviews" :key="r.id" class="review-item">
            <strong>{{ r.reviewerName || r.authorName || "购买者" }}</strong>
            <p>
              <el-rate :model-value="r.productStars" disabled />
              <span v-if="r.serviceStars" class="muted small"
                >服务 {{ r.serviceStars }} 星</span
              >
            </p>
            <p>{{ r.content }}</p>
          </article>
        </section>
      </div></template
    >
  </section>
</template>
