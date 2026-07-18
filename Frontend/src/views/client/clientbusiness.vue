<template>
  <div class="clientbusiness-management">
    <page-header title="业务接入配置">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增配置</el-button>
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
      :request-api="getClientBusinessList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom render for Return Status -->
      <template #returnSlot="scope">
        <el-tag :type="scope.row.isreturnstatus === 1 ? 'success' : 'info'">
          {{ scope.row.isreturnstatus === 1 ? '是' : '否' }}
        </el-tag>
      </template>

      <!-- Custom render for Use Type -->
      <template #useTypeSlot="scope">
        <el-tag :type="scope.row.usertype == 1 ? 'primary' : 'warning'">
          {{ scope.row.usertype == 1 ? 'http' : 'WEB' }}
        </el-tag>
      </template>

      <!-- Custom render for State -->
      <template #stateSlot="scope">
        <status-tag :status="scope.row.state == 1 ? 'success' : 'danger'" :text="scope.row.state == 1 ? '已开通' : '未开通'" />
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
      size="large"
      :loading="saving"
      @confirm="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="140px"
      >
        <!-- Section 1: Access Identity -->
        <page-section title="接入身份">
          <el-form-item label="公司名称" prop="corpname">
            <el-input v-model="formModel.corpname" placeholder="请输入公司名称" />
          </el-form-item>
          <el-form-item label="接入用户名" prop="usercode">
            <el-input v-model="formModel.usercode" placeholder="请输入接入用户名" />
          </el-form-item>
          <el-form-item label="接入密码" prop="pwd">
            <el-input v-model="formModel.pwd" type="password" show-password placeholder="请输入接入密码" />
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model="formModel.mobile" placeholder="请输入手机号" />
          </el-form-item>
        </page-section>

        <!-- Section 2: Report & Callback -->
        <page-section title="回执与回调">
          <el-form-item label="接入 IP 地址" prop="ipaddress">
            <el-input v-model="formModel.ipaddress" placeholder="请输入接入 IP 地址" />
          </el-form-item>
          <el-form-item label="是否返回状态报告" prop="isreturnstatus">
            <el-radio-group v-model="formModel.isreturnstatus">
              <el-radio :value="1">返回</el-radio>
              <el-radio :value="0">不返回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="接收状态报告地址" prop="receivestatusurl">
            <el-input v-model="formModel.receivestatusurl" placeholder="请输入接收状态报告地址 (例: http://domain/callback)" />
          </el-form-item>
        </page-section>

        <!-- Section 3: Business Controls -->
        <page-section title="业务控制">
          <el-form-item label="业务优先级" prop="priority">
            <el-input-number v-model="formModel.priority" :min="1" placeholder="数字越大优先级越高" />
          </el-form-item>
          <el-form-item label="使用方式" prop="usertype">
            <el-radio-group v-model="formModel.usertype">
              <el-radio value="1">http</el-radio>
              <el-radio value="2">WEB</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="开通状态" prop="state">
            <el-radio-group v-model="formModel.state">
              <el-radio value="0">未开通</el-radio>
              <el-radio value="1">已开通</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="策略链" prop="clientFilters">
            <el-input v-model="formModel.clientFilters" placeholder="策略过滤链，多个逗号分隔 (例: black,dirtyword,route)" />
          </el-form-item>
          <el-form-item label="余额 (厘)" prop="money">
            <el-input v-model="formModel.money" placeholder="请输入余额" />
          </el-form-item>
        </page-section>
      </el-form>
    </form-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getClientBusinessList, getClientBusinessInfo, saveClientBusiness, updateClientBusiness, deleteClientBusinesses } from '@/api/client'

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
  { prop: 'usercode', label: '接入账号' },
  { prop: 'mobile', label: '手机号' },
  { prop: 'ipaddress', label: '接入IP' },
  { prop: 'isreturnstatus', label: '状态报告', width: 90, slot: 'returnSlot', align: 'center' },
  { prop: 'receivestatusurl', label: '回执地址', showOverflowTooltip: true },
  { prop: 'priority', label: '优先级', width: 80, align: 'center' },
  { prop: 'usertype', label: '使用方式', width: 90, slot: 'useTypeSlot', align: 'center' },
  { prop: 'state', label: '状态', width: 90, slot: 'stateSlot', align: 'center' },
  { prop: 'money', label: '余额(厘)', width: 100, align: 'right' },
  { label: '操作', slot: 'action', width: 120, fixed: 'right', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  isreturnstatus: 1,
  usertype: '1',
  state: '1',
  priority: 1
})

const formRules = {
  corpname: [{ required: true, message: '公司名称不能为空', trigger: 'blur' }],
  usercode: [{ required: true, message: '接入账号不能为空', trigger: 'blur' }],
  pwd: [{ required: true, message: '接入密码不能为空', trigger: 'blur' }],
  mobile: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
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
  formModel.value = {
    isreturnstatus: 1,
    usertype: '1',
    state: '1',
    priority: 1
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getClientBusinessInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data || res.message || {}
      // 字段转换格式以匹配单选框字符匹配
      if (formModel.value.usertype !== undefined) {
        formModel.value.usertype = String(formModel.value.usertype)
      }
      if (formModel.value.state !== undefined) {
        formModel.value.state = String(formModel.value.state)
      }
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取业务接入信息失败')
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
      confirmText: '确定删除该配置吗？',
      onClick: async () => {
        try {
          const res: any = await deleteClientBusinesses([row.id])
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
        const res: any = isEdit ? await updateClientBusiness(formModel.value) : await saveClientBusiness(formModel.value)
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
      const res: any = await deleteClientBusinesses(selectedIds.value)
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
.clientbusiness-management {
  /* Use layout spacing */
}
</style>
