<template>
  <div class="limit-management">
    <page-header title="限流规则配置">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增规则</el-button>
      </template>
    </page-header>

    <!-- Search Form -->
    <pro-search
      v-model="searchParam"
      :search-config="searchConfig"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- Action Bar -->
    <div class="table-actions flex space-x-2 mb-4">
      <el-button type="warning" plain :icon="Edit" :disabled="selectedIds.length !== 1" @click="handleEdit">修改</el-button>
      <el-button type="danger" plain :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">删除</el-button>
    </div>

    <!-- Data Table -->
    <pro-table
      ref="tableRef"
      :columns="columns"
      :request-api="getLimitList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom render for Limit State -->
      <template #stateSlot="scope">
        <status-tag :status="scope.row.limitState === 1 ? 'success' : 'danger'" :text="scope.row.limitState === 1 ? '启用' : '禁用'" />
      </template>

      <!-- Custom Actions -->
      <template #action="{ row }">
        <action-buttons :actions="getRowActions(row)" />
      </template>
    </pro-table>

    <!-- Add/Edit Dialog -->
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
        <el-form-item label="限流时间 (秒)" prop="limitTime">
          <el-input-number v-model="formModel.limitTime" :min="1" style="width: 100%" placeholder="请输入周期时间 (单位秒)" />
        </el-form-item>
        <el-form-item label="限流次数" prop="limitCount">
          <el-input-number v-model="formModel.limitCount" :min="1" style="width: 100%" placeholder="限流时间周期内最大请求次数" />
        </el-form-item>
        <el-form-item label="限流描述" prop="despcription">
          <el-input v-model="formModel.despcription" placeholder="限流规则的说明" />
        </el-form-item>
        <el-form-item label="规则状态" prop="limitState">
          <el-radio-group v-model="formModel.limitState">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </form-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getLimitList, getLimitInfo, saveLimit, updateLimit, deleteLimits } from '@/api/limit'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '描述查询', prop: 'search', type: 'input', placeholder: '请输入限流规则描述' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'limitTime', label: '限流周期(秒)', width: 130, align: 'center' },
  { prop: 'limitCount', label: '最大允许次数', width: 130, align: 'center' },
  { prop: 'despcription', label: '限流描述' },
  { prop: 'limitState', label: '状态', width: 100, slot: 'stateSlot', align: 'center' },
  { label: '操作', slot: 'action', width: 120, fixed: 'right', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  limitState: 1,
  limitTime: 60,
  limitCount: 10
})

const formRules = {
  limitTime: [{ required: true, message: '限流时间不能为空', trigger: 'blur' }],
  limitCount: [{ required: true, message: '限流次数不能为空', trigger: 'blur' }],
  despcription: [{ required: true, message: '描述信息不能为空', trigger: 'blur' }]
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

function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {
    limitState: 1,
    limitTime: 60,
    limitCount: 10
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getLimitInfo(id)
    if (res && res.code === 0) {
      // 兼容接口返回的 limit 对象
      formModel.value = res.limit || res.data || {}
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取限流配置详情失败')
  }
}

function handleEditRow(row: any) {
  dialogTitle.value = '修改'
  formModel.value = { ...row }
  dialogVisible.value = true
}

function getRowActions(row: any) {
  return [
    { text: '编辑', onClick: () => handleEditRow(row) },
    { 
      text: '删除', 
      danger: true, 
      confirmText: '确定删除该限流规则吗？',
      onClick: async () => {
        try {
          const res: any = await deleteLimits([row.id])
          if (res && res.code === 0) {
            ElMessage.success('删除成功')
            tableRef.value?.reload()
          } else {
            ElMessage.error(res.msg || '删除失败')
          }
        } catch (error: any) {
          ElMessage.error(error.message || '网络错误')
        }
      }
    }
  ]
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateLimit(formModel.value) : await saveLimit(formModel.value)
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

function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  ElMessageBox.confirm('您确定要删除所选数据吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res: any = await deleteLimits(selectedIds.value)
      if (res && res.code === 0) {
        ElMessage.success(res.msg || '删除成功')
        tableRef.value?.reload()
      } else {
        ElMessage.error(res.msg || '删除失败')
      }
    } catch (error: any) {
      ElMessage.error(error.message || '网络请求错误')
    }
  })
}
</script>

<style scoped>
.limit-management {
  /* Use layout spacing */
}
</style>
