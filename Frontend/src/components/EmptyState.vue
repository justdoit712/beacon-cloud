<template>
  <el-empty
    class="custom-empty-state"
    :description="description"
    :image-size="imageSize"
  >
    <template #default>
      <slot></slot>
    </template>
  </el-empty>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  description?: string
  imageSize?: number
}>(), {
  description: '暂无数据',
  imageSize: 120
})
</script>

<style scoped>
.custom-empty-state {
  padding: 40px 0;
}
</style>
