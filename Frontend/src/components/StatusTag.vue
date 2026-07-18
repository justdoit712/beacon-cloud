<template>
  <el-tag :type="tagType" :effect="effect">
    <slot>{{ text }}</slot>
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  status?: 'success' | 'warning' | 'danger' | 'info' | string
  text?: string
  effect?: 'light' | 'dark' | 'plain'
}>(), {
  status: 'info',
  effect: 'light'
})

const tagType = computed(() => {
  const map: Record<string, string> = {
    success: 'success',
    warning: 'warning',
    danger: 'danger',
    info: 'info'
  }
  return map[props.status] || 'info'
})
</script>
