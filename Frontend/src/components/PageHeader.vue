<template>
  <div class="page-header flex justify-between items-center mb-4">
    <div class="left">
      <h2 class="text-xl font-semibold text-[var(--text-primary)] m-0 flex items-center">
        <slot name="icon"></slot>
        {{ title }}
      </h2>
      <p v-if="description" class="text-sm text-[var(--text-secondary)] mt-1 mb-0">{{ description }}</p>
    </div>
    <div class="right flex items-center space-x-2">
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>
