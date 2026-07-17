<template>
  <div class="user-management">
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
      :request-api="getUserList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom Password Render -->
      <template #passwordSlot>
        <span>******</span>
      </template>

      <!-- Custom Type Render -->
      <template #typeSlot="scope">
        <el-tag :type="scope.row.type === 1 ? 'danger' : 'info'">
          {{ scope.row.type === 1 ? '管理员' : '普通客户' }}
        </el-tag>
      </template>

      <!-- Custom Status Render -->
      <template #statusSlot="scope">
        <el-tag :type="scope.row.status === 1 ? 'success' : 'warning'">
          {{ scope.row.status === 1 ? '有效' : '无效' }}
        </el-tag>
      </template>
    </pro-table>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="usercode">
          <el-input v-model="formModel.usercode" placeholder="请输入用户名" :disabled="formModel.id !== undefined" />
        </el-form-item>
        <el-form-item label="密码" prop="password" :rules="formModel.id ? [] : formRules.password">
          <el-input v-model="formModel.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formModel.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="formModel.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="formModel.type">
            <el-radio :value="1">管理员</el-radio>
            <el-radio :value="2">普通客户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formModel.status">
            <el-radio :value="1">有效</el-radio>
            <el-radio :value="0">无效</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属客户" prop="clientid">
          <el-select v-model="formModel.clientid" placeholder="请选择所属客户" style="width: 100%">
            <el-option
              v-for="item in clientOptions"
              :key="item.id"
              :label="item.corpname"
              :value="item.id"
            />
          </el-select>
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
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getUserList, getUserInfo, saveUser, updateUser, deleteUsers, getAllClients } from '@/api/user'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '关键字', prop: 'search', type: 'input', placeholder: '用户名/真实姓名' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'usercode', label: '用户名' },
  { prop: 'password', label: '密码', slot: 'passwordSlot' },
  { prop: 'email', label: '邮箱' },
  { prop: 'realName', label: '真实姓名' },
  { prop: 'type', label: '类型', slot: 'typeSlot' },
  { prop: 'status', label: '状态', slot: 'statusSlot' },
  { prop: 'clientid', label: '客户ID' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  type: 1,
  status: 1
})

const clientOptions = ref<any[]>([])

const formRules = {
  usercode: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  clientid: [{ required: true, message: '请选择所属客户', trigger: 'change' }]
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
    type: 1,
    status: 1
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getUserInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data.user || res.data
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取用户信息失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateUser(formModel.value) : await saveUser(formModel.value)
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
      const res: any = await deleteUsers(selectedIds.value)
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

async function loadClients() {
  try {
    const res: any = await getAllClients()
    if (res && res.code === 0) {
      clientOptions.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

onMounted(() => {
  loadClients()
})
</script>

<style scoped>
.user-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
