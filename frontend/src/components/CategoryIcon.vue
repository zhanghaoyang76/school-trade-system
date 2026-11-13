<script setup>
defineProps({ name: { type: String, default: "" } });
</script>
<template>
  <svg
    class="category-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="!name">
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </template>
    <template v-else-if="name.includes('数码')">
      <rect x="3" y="4" width="18" height="12" rx="2" />
      <path d="M8 21h8m-4-5v5" />
    </template>
    <path
      v-else-if="name.includes('书')"
      d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Zm0 0v15"
    />
    <path
      v-else-if="name.includes('生活')"
      d="M3 11 12 3l9 8v10H3V11Zm6 10v-8h6v8"
    />
    <template v-else-if="name.includes('运动')">
      <circle cx="6" cy="17" r="4" />
      <circle cx="18" cy="17" r="4" />
      <path d="m6 17 5-10 7 10M8 7h5m2-3h3v4" />
    </template>
    <path
      v-else-if="name.includes('服')"
      d="m8 3-6 4 3 5 3-2v11h8V10l3 2 3-5-6-4c0 4-8 4-8 0Z"
    />
    <template v-else>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4m8-4v4" />
    </template>
  </svg>
</template>
