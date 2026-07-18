<template>
  <div class="client-management">
    <page-header title="客户管理">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增客户</el-button>
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
      :request-api="getClientList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
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
        label-width="100px"
      >
        <el-form-item label="公司名称" prop="corpname">
          <el-input v-model="formModel.corpname" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="公司地址" prop="address">
          <el-input v-model="formModel.address" placeholder="请输入公司地址" />
        </el-form-item>
        <el-form-item label="客户经理" prop="customermanager">
          <el-input v-model="formModel.customermanager" placeholder="请输入客户经理" />
        </el-form-item>
        <el-form-item label="联系人" prop="linkman">
          <el-input v-model="formModel.linkman" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="formModel.mobile" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formModel.email" placeholder="请输入邮箱 (name@example.com)" />
        </el-form-item>
      </el-form>
    </form-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getClientList, getClientInfo, saveClient, updateClient, deleteClients } from '@/api/client'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '公司名称', prop: 'search', type: 'input', placeholder: '请输入公司名称进行检索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'corpname', label: '公司名称' },
  { prop: 'address', label: '公司地址' },
  { prop: 'customermanager', label: '客户经理' },
  { prop: 'linkman', label: '联系人' },
  { prop: 'mobile', label: '手机号' },
  { prop: 'email', label: '邮箱' },
  { label: '操作', slot: 'action', width: 120, fixed: 'right', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({})

const formRules = {
  corpname: [{ required: true, message: '公司名称不能为空', trigger: 'blur' }],
  linkman: [{ required: true, message: '联系人不能为空', trigger: 'blur' }],
  mobile: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
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
  formModel.value = {}
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getClientInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data || {}
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取客户详情失败')
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
      confirmText: '确定删除该客户吗？',
      onClick: async () => {
        try {
          const res: any = await deleteClients([row.id])
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
        const res: any = isEdit ? await updateClient(formModel.value) : await saveClient(formModel.value)
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
      const res: any = await deleteClients(selectedIds.value)
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
.client-management {
  /* removed padding to respect layout padding */
}
</style>
