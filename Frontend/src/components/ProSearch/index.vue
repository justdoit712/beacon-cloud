<template>
  <div class="pro-search">
    <el-form :model="modelValue" inline class="search-form">
      <el-form-item
        v-for="item in searchConfig"
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
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { Search, Refresh } from '@element-plus/icons-vue'


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
  background-color: #fff;
  padding: 16px 16px 0 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  margin-bottom: 16px;
}
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
</style>
