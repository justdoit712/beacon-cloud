<template>
  <div class="pro-search">
    <el-form :model="modelValue" inline class="search-form">
      <el-form-item
        v-for="(item, index) in visibleConfig"
        :key="item.prop"
        :label="item.label"
      >
        <!-- Input -->
        <el-input
          v-if="item.type === 'input'"
          v-model="modelValue[item.prop]"
          :placeholder="item.placeholder || '请输入'"
          clearable
          @keyup.enter="handleSearch"
        />

        <!-- Select -->
        <el-select
          v-else-if="item.type === 'select'"
          v-model="modelValue[item.prop]"
          :placeholder="item.placeholder || '请选择'"
          clearable
        >
          <el-option
            v-for="opt in item.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button v-if="searchConfig.length > 4" type="primary" link @click="isExpanded = !isExpanded">
          {{ isExpanded ? '收起' : '展开' }}
          <el-icon class="ml-1"><component :is="isExpanded ? ArrowUp : ArrowDown" /></el-icon>
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, Refresh, ArrowDown, ArrowUp } from '@element-plus/icons-vue'

const isExpanded = ref(false)
interface SearchItem {
  label: string
  prop: string
  type: 'input' | 'select'
  placeholder?: string
  options?: Array<{ label: string; value: any }>
}

const props = defineProps<{
  searchConfig: SearchItem[]
  modelValue: any
}>()

const emit = defineEmits(['search', 'reset', 'update:modelValue'])

const visibleConfig = computed(() => {
  if (isExpanded.value) return props.searchConfig
  return props.searchConfig.slice(0, 4)
})

function handleSearch() {
  emit('search')
}

function handleReset() {
  const resetForm = { ...props.modelValue }
  Object.keys(resetForm).forEach(key => {
    resetForm[key] = ''
  })
  emit('update:modelValue', resetForm)
  emit('reset')
}
</script>

<style scoped>
.pro-search {
  background-color: var(--panel-bg);
  padding: 16px 16px 0 16px;
  border-radius: 6px;
  box-shadow: var(--shadow-soft);
  margin-bottom: 16px;
}
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
</style>
