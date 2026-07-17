<template>
  <div class="api-grayrelease">
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
      <!-- Custom render for State -->
      <template #stateSlot="scope">
        <el-tag :type="scope.row.state == 1 ? 'success' : 'danger'">
          {{ scope.row.state == 1 ? '启用' : '已停用' }}
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
        <el-form-item label="服务名称" prop="serviceId">
          <el-input v-model="formModel.serviceId" placeholder="请输入服务名称" />
        </el-form-item>
        <el-form-item label="请求地址" prop="path">
          <el-input v-model="formModel.path" placeholder="请输入请求路径 (例如: /sms/send)" />
        </el-form-item>
        <el-form-item label="频次" prop="percent">
          <el-input v-model="formModel.percent" placeholder="输入分流比率/频次 (例如: 10)" />
        </el-form-item>
        <el-form-item label="版本标识" prop="forward">
          <el-input v-model="formModel.forward" placeholder="转发的目标版本标识" />
        </el-form-item>
        <el-form-item label="状态" prop="state">
          <el-radio-group v-model="formModel.state">
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
const FAMILY = 'gray-release'

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '服务名称', prop: 'search', type: 'input', placeholder: '服务名称搜索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'serviceId', label: '服务名称' },
  { prop: 'path', label: '请求地址' },
  { prop: 'percent', label: '频次', width: 100, align: 'center' },
  { prop: 'forward', label: '版本标识', width: 140 },
  { prop: 'state', label: '状态', width: 100, slot: 'stateSlot', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  state: 1
})

const formRules = {
  serviceId: [{ required: true, message: '服务名称不能为空', trigger: 'blur' }],
  path: [{ required: true, message: '请求地址不能为空', trigger: 'blur' }]
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
    state: 1
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
    ElMessage.error(error.message || '获取配置详情失败')
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
.api-grayrelease {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
