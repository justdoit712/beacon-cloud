<template>
  <div class="notify-management">
    <!-- Search Form -->
    <pro-search
      v-model="searchParam"
      :search-config="searchConfig"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- Action Bar -->
    <div class="table-actions">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
      <el-button type="warning" :icon="Edit" :disabled="selectedIds.length !== 1" @click="handleEdit">修改</el-button>
      <el-button type="danger" :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">删除</el-button>
    </div>

    <!-- Data Table -->
    <pro-table
      ref="tableRef"
      :columns="columns"
      :request-api="requestList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom render for Notify State -->
      <template #notifySlot="scope">
        <el-tag :type="scope.row.notifyState == 1 ? 'success' : 'danger'">
          {{ scope.row.notifyState == 1 ? '启用' : '已停用' }}
        </el-tag>
      </template>

      <!-- Custom render for Cache State -->
      <template #cacheSlot="scope">
        <el-tag :type="scope.row.cacheState == 1 ? 'success' : 'danger'">
          {{ scope.row.cacheState == 1 ? '启用' : '已停用' }}
        </el-tag>
      </template>
    </pro-table>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="550px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="120px"
      >
        <el-form-item label="通知标记" prop="tag">
          <el-input v-model="formModel.tag" placeholder="如: balance_recharge" />
        </el-form-item>
        <el-form-item label="通知介绍" prop="desp">
          <el-input v-model="formModel.desp" placeholder="通知功能的用途简述" />
        </el-form-item>
        <el-form-item label="推送状态" prop="notifyState">
          <el-radio-group v-model="formModel.notifyState">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="同步缓存" prop="cacheState">
          <el-radio-group v-model="formModel.cacheState">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getLegacyList, getLegacyInfo, saveLegacy, updateLegacy, deleteLegacyBatch } from '@/api/legacy'

const tableRef = ref()
const formRef = ref<FormInstance>()
const FAMILY = 'notify'

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '通知标记', prop: 'search', type: 'input', placeholder: '请输入通知标记' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'tag', label: '通知标记', width: 180 },
  { prop: 'desp', label: '通知介绍' },
  { prop: 'notifyState', label: '推送消息', width: 100, slot: 'notifySlot', align: 'center' },
  { prop: 'cacheState', label: '同步缓存', width: 100, slot: 'cacheSlot', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  notifyState: 1,
  cacheState: 1
})

const formRules = {
  tag: [{ required: true, message: '通知标记不能为空', trigger: 'blur' }],
  desp: [{ required: true, message: '通知介绍不能为空', trigger: 'blur' }]
}

function requestList(params: any) {
  return getLegacyList(FAMILY, params)
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
    notifyState: 1,
    cacheState: 1
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getLegacyInfo(FAMILY, id)
    if (res && res.code === 0) {
      formModel.value = res.data || {}
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取通知配置失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit
          ? await updateLegacy(FAMILY, formModel.value)
          : await saveLegacy(FAMILY, formModel.value)
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
      const res: any = await deleteLegacyBatch(FAMILY, selectedIds.value)
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
.notify-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
