<script setup>
import { ref } from "vue";
import { request } from "../api.js";
import { ElMessage } from "element-plus";
const props = defineProps({
  modelValue: { default: () => [] },
  purpose: { default: "PRODUCT" },
  multiple: Boolean,
});
const emit = defineEmits(["update:modelValue"]);
const busy = ref(false);
async function upload(event) {
  const files = [...event.target.files];
  if (!files.length) return;
  busy.value = true;
  try {
    const values = props.multiple ? [...props.modelValue] : [];
    for (const file of files) {
      if (!["image/jpeg", "image/png", "image/webp"].includes(file.type))
        throw new Error("请选择 JPG、PNG 或 WebP 图片");
      if (file.size > 5 * 1024 * 1024) throw new Error("单张图片不能超过5MB");
      const data = new FormData();
      data.append("file", file);
      data.append("purpose", props.purpose);
      values.push(await request("/files", { method: "POST", body: data }));
    }
    emit("update:modelValue", values);
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    busy.value = false;
    event.target.value = "";
  }
}
function remove(index) {
  emit(
    "update:modelValue",
    props.modelValue.filter((_, i) => i !== index),
  );
}
</script>
<template>
  <div class="upload-field">
    <div class="uploaded-list">
      <div v-for="(file, i) in modelValue" :key="file.id" class="uploaded">
        <img
          :src="file.url || '/api/files/' + file.id"
          alt="已上传图片"
        /><button type="button" aria-label="移除图片" @click="remove(i)">
          ×
        </button>
      </div>
    </div>
    <label class="upload-button"
      ><input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        :multiple="multiple"
        :disabled="busy"
        @change="upload"
      />{{ busy ? "正在上传…" : "+ 选择图片" }}</label
    ><span class="muted small">JPG / PNG / WebP，单张不超过5MB</span>
  </div>
</template>
