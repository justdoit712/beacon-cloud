<template>
  <div class="page-section mb-6 bg-[var(--panel-bg)] rounded-md shadow-sm p-6">
    <h3 class="section-title text-base font-semibold text-[var(--text-primary)] mb-4 border-l-4 border-[var(--brand-primary)] pl-3 -ml-6">
      {{ title }}
    </h3>
    <div class="section-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>
