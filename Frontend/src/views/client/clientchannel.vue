<template>
  <div class="clientchannel-management">
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
      :request-api="getClientChannelList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    />

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
        <el-form-item label="所属客户" prop="clientid">
          <el-select
            v-model="formModel.clientid"
            placeholder="请选择所属客户"
            style="width: 100%"
          >
            <el-option
              v-for="item in clientOptions"
              :key="item.id"
              :label="item.corpname"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属通道" prop="channelid">
          <el-select
            v-model="formModel.channelid"
            placeholder="请选择所属通道"
            style="width: 100%"
          >
            <el-option
              v-for="item in channelOptions"
              :key="item.id"
              :label="item.channelname"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="扩展号" prop="extendnumber">
          <el-input v-model="formModel.extendnumber" type="number" placeholder="请输入扩展号" />
        </el-form-item>
        <el-form-item label="单条价格 (厘)" prop="price">
          <el-input-number v-model="formModel.price" :min="0" style="width: 100%" placeholder="价格(单位里)" />
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
import { getClientChannelList, getClientChannelInfo, saveClientChannel, updateClientChannel, deleteClientChannels, getClientBusinessAll, getAllChannels } from '@/api/client'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '关键字', prop: 'search', type: 'input', placeholder: '请输入关键字检索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'corpname', label: '客户名称' },
  { prop: 'extendnumber', label: '扩展号' },
  { prop: 'price', label: '每条价格(厘)', width: 150 },
  { prop: 'channelname', label: '通道名称' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const clientOptions = ref<any[]>([])
const channelOptions = ref<any[]>([])

const formModel = ref<any>({
  clientid: '',
  channelid: '',
  extendnumber: '',
  price: 0
})

const formRules = {
  clientid: [{ required: true, message: '请选择所属客户', trigger: 'change' }],
  channelid: [{ required: true, message: '请选择所属通道', trigger: 'change' }],
  extendnumber: [{ required: true, message: '扩展号不能为空', trigger: 'blur' }],
  price: [{ required: true, message: '价格不能为空', trigger: 'blur' }]
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

async function loadDropdownOptions() {
  try {
    const [clientRes, channelRes]: any = await Promise.all([
      getClientBusinessAll(),
      getAllChannels()
    ])
    if (clientRes && clientRes.code === 0) {
      clientOptions.value = clientRes.data || []
    }
    if (channelRes && channelRes.code === 0) {
      channelOptions.value = channelRes.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

async function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {
    clientid: '',
    channelid: '',
    extendnumber: '',
    price: 0
  }
  await loadDropdownOptions()
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    await loadDropdownOptions()
    const res: any = await getClientChannelInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data || {}
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取通道绑定详情失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateClientChannel(formModel.value) : await saveClientChannel(formModel.value)
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
      const res: any = await deleteClientChannels(selectedIds.value)
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
.clientchannel-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
