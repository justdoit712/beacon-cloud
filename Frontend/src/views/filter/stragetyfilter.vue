<template>
  <div class="stragetyfilter-management">
    <page-header title="业务策略链配置" />

    <!-- Search Form -->
    <pro-search
      v-model="searchParam"
      :search-config="searchConfig"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- Action Bar -->
    <div class="table-actions flex space-x-2 mb-4">
      <el-button type="warning" plain :icon="Edit" :disabled="selectedIds.length !== 1" @click="handleEdit">修改策略</el-button>
    </div>

    <!-- Data Table -->
    <pro-table
      ref="tableRef"
      :columns="columns"
      :request-api="getStrategyFilterList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom Actions -->
      <template #action="{ row }">
        <action-buttons :actions="getRowActions(row)" />
      </template>
    </pro-table>

    <!-- Edit Dialog -->
    <form-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      size="medium"
      :loading="saving"
      @confirm="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="120px"
      >
        <el-form-item label="客户名称">
          <el-input :value="formModel.corpname" readonly disabled />
        </el-form-item>
        <el-form-item label="接入用户名">
          <el-input :value="formModel.usercode" readonly disabled />
        </el-form-item>
        <el-form-item label="过滤器配置" prop="selectedFilters">
          <el-checkbox-group v-model="formModel.selectedFilters">
            <el-row>
              <el-col
                v-for="item in supportedFilters"
                :key="item"
                :span="8"
                style="margin-bottom: 8px;"
              >
                <el-checkbox :value="item">{{ item }}</el-checkbox>
              </el-col>
            </el-row>
          </el-checkbox-group>
          <div class="help-text">选中过滤器将按先后顺序拼装为策略过滤链生效。</div>
        </el-form-item>
      </el-form>
    </form-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getStrategyFilterList, getStrategyFilterInfo, updateStrategyFilter, getAllSupportedFilters } from '@/api/filter'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '关键字', prop: 'search', type: 'input', placeholder: '请输入客户名称或账号' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'corpname', label: '客户名称' },
  { prop: 'usercode', label: '接入账号' },
  { prop: 'filters', label: '策略过滤器列表', showOverflowTooltip: true },
  { label: '操作', slot: 'action', width: 120, fixed: 'right', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('修改策略链')
const saving = ref(false)

const supportedFilters = ref<string[]>([])
const formModel = ref<any>({
  selectedFilters: []
})

const formRules = {
  selectedFilters: [{ type: 'array', required: true, message: '请至少选择一个过滤器', trigger: 'change' }]
}

function handleSearch() {
  tableRef.value?.reload()
}

function handleReset() {
  searchParam.value.search = ''
  tableRef.value?.reload()
}

function handleSelectionChange(selection: any[]) {
  selectedIds.value = selection.map(item => item.id)
}

async function loadSupportedFilters() {
  try {
    const res: any = await getAllSupportedFilters()
    if (res && res.code === 0) {
      supportedFilters.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  const id = selectedIds.value[0]
  try {
    await loadSupportedFilters()
    const res: any = await getStrategyFilterInfo(id)
    if (res && res.code === 0) {
      const data = res.data || {}
      // 将 comma-separated filters 字符串解析成 array 并绑定给 checkbox-group
      const activeFilters = data.filters
        ? data.filters.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0)
        : []
      formModel.value = {
        id: data.id,
        corpname: data.corpname,
        usercode: data.usercode,
        selectedFilters: activeFilters
      }
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取策略详情失败')
  }
}

function handleEditRow(row: any) {
  selectedIds.value = [row.id]
  handleEdit()
}

function getRowActions(row: any) {
  return [
    { text: '编辑策略', onClick: () => handleEditRow(row) }
  ]
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 拼装回后端接受的逗号分隔字符串
        const payload = {
          id: formModel.value.id,
          filters: formModel.value.selectedFilters.join(',')
        }
        const res: any = await updateStrategyFilter(payload)
        if (res && res.code === 0) {
          ElMessage.success(res.msg || '操作成功')
          dialogVisible.value = false
          tableRef.value?.reload()
        } else {
          ElMessage.error(res.msg || '操作失败')
        }
      } catch (error: any) {
        ElMessage.error(error.message || '网络请求错误')
      } finally {
        saving.value = false
      }
    }
  })
}
</script>

<style scoped>
.stragetyfilter-management {
  /* Use layout spacing */
}
.help-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
</style>
