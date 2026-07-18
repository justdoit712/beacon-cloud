<template>
  <div class="action-buttons flex items-center space-x-2">
    <template v-for="(btn, index) in primaryActions" :key="index">
      <el-popconfirm
        v-if="btn.danger && btn.confirmText"
        :title="btn.confirmText"
        @confirm="btn.onClick"
      >
        <template #reference>
          <el-button :type="btn.danger ? 'danger' : 'primary'" link>
            {{ btn.text }}
          </el-button>
        </template>
      </el-popconfirm>
      
      <el-button v-else :type="btn.danger ? 'danger' : 'primary'" link @click="btn.onClick">
        {{ btn.text }}
      </el-button>
    </template>

    <el-dropdown v-if="dropdownActions.length > 0" @command="handleCommand">
      <el-button type="primary" link>
        更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="(btn, index) in dropdownActions"
            :key="index"
            :command="index"
            :class="{'text-[var(--danger)]': btn.danger}"
          >
            {{ btn.text }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

interface ActionBtn {
  text: string
  onClick: () => void
  danger?: boolean
  confirmText?: string
}

const props = defineProps<{
  actions: ActionBtn[]
}>()

const primaryActions = computed(() => {
  return props.actions.slice(0, 3)
})

const dropdownActions = computed(() => {
  return props.actions.slice(3)
})

function handleCommand(index: number) {
  const btn = dropdownActions.value[index]
  if (btn.danger && btn.confirmText) {
    ElMessageBox.confirm(btn.confirmText, '确认操作', {
      type: 'warning'
    }).then(() => {
      btn.onClick()
    }).catch(() => {})
  } else {
    btn.onClick()
  }
}
</script>
