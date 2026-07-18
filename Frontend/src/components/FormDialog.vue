<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="dialogWidth"
    :close-on-click-modal="false"
    :destroy-on-close="true"
    @closed="handleClosed"
  >
    <div class="dialog-body">
      <slot></slot>
    </div>
    <template #footer>
      <div class="dialog-footer flex justify-end space-x-2">
        <el-button @click="handleCancel">{{ cancelText }}</el-button>
        <el-button type="primary" :loading="loading" @click="handleConfirm">{{ confirmText }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  size?: 'small' | 'medium' | 'large' // small=480, medium=720, large=960
  confirmText?: string
  cancelText?: string
  loading?: boolean
}>(), {
  size: 'medium',
  confirmText: '确 定',
  cancelText: '取 消',
  loading: false
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const dialogWidth = computed(() => {
  if (props.size === 'small') return '480px'
  if (props.size === 'large') return '960px'
  return '720px'
})

function handleCancel() {
  emit('cancel')
  visible.value = false
}

function handleConfirm() {
  emit('confirm')
}

function handleClosed() {
  emit('closed')
}
</script>
