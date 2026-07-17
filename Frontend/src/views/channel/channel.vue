<template>
  <div class="channel-management">
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
      :request-api="getChannelList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom render for Channel Type -->
      <template #typeSlot="scope">
        <el-tag v-if="scope.row.channeltype === 1" type="primary">移动</el-tag>
        <el-tag v-else-if="scope.row.channeltype === 2" type="success">联通</el-tag>
        <el-tag v-else-if="scope.row.channeltype === 3" type="warning">电信</el-tag>
        <span v-else>-</span>
      </template>

      <!-- Custom render for Protocol Type -->
      <template #protocolSlot="scope">
        <el-tag v-if="scope.row.protocaltype === 1" type="info">CMPP</el-tag>
        <el-tag v-else-if="scope.row.protocaltype === 2" type="info">SGIP</el-tag>
        <el-tag v-else-if="scope.row.protocaltype === 3" type="info">SMGP</el-tag>
        <el-tag v-else-if="scope.row.protocaltype === 4" type="primary">HTTP</el-tag>
        <span v-else>-</span>
      </template>

      <!-- Custom render for Availability -->
      <template #availableSlot="scope">
        <el-tag :type="scope.row.isavailable === 1 ? 'success' : 'danger'">
          {{ scope.row.isavailable === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </pro-table>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="通道名称" prop="channelname">
              <el-input v-model="formModel.channelname" placeholder="请输入通道名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通道地区" prop="channelarea">
              <el-input v-model="formModel.channelarea" placeholder="请输入通道地区 (例: 浙江)" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="通道类型" prop="channeltype">
              <el-select v-model="formModel.channeltype" placeholder="请选择通道类型" style="width: 100%">
                <el-option :value="1" label="移动" />
                <el-option :value="2" label="联通" />
                <el-option :value="3" label="电信" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地区编码" prop="channelareacode">
              <el-input v-model="formModel.channelareacode" placeholder="地区数字编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="通道价格(厘)" prop="channelprice">
              <el-input-number v-model="formModel.channelprice" :min="0" style="width: 100%" placeholder="资费单价(厘)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议类型" prop="protocaltype">
              <el-select v-model="formModel.protocaltype" placeholder="请选择协议类型" style="width: 100%">
                <el-option :value="1" label="CMPP" />
                <el-option :value="2" label="SGIP" />
                <el-option :value="3" label="SMGP" />
                <el-option :value="4" label="HTTP" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="网关 IP" prop="channelip">
              <el-input v-model="formModel.channelip" placeholder="请输入网关 IP" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网关端口" prop="channelport">
              <el-input-number v-model="formModel.channelport" :min="1" style="width: 100%" placeholder="网关通信端口" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="接入账号" prop="channelusername">
              <el-input v-model="formModel.channelusername" placeholder="请输入接入账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接入密码" prop="channelpassword">
              <el-input v-model="formModel.channelpassword" type="password" show-password placeholder="请输入接入密码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="SP 接入号" prop="spnumber">
              <el-input v-model="formModel.spnumber" placeholder="请输入接入号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否可用" prop="isavailable">
              <el-radio-group v-model="formModel.isavailable">
                <el-radio :value="1">可用</el-radio>
                <el-radio :value="0">不可用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
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
import { getChannelList, getChannelInfo, saveChannel, updateChannel, deleteChannels } from '@/api/channel'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '通道名称', prop: 'search', type: 'input', placeholder: '请输入通道名称进行检索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'channelname', label: '通道名称' },
  { prop: 'channeltype', label: '运营商', width: 90, slot: 'typeSlot', align: 'center' },
  { prop: 'channelarea', label: '通道地区', width: 100 },
  { prop: 'channelprice', label: '资费(厘)', width: 100, align: 'right' },
  { prop: 'channelip', label: '网关IP', width: 140 },
  { prop: 'channelport', label: '端口', width: 80, align: 'center' },
  { prop: 'spnumber', label: 'SP接入号' },
  { prop: 'protocaltype', label: '协议类型', width: 100, slot: 'protocolSlot', align: 'center' },
  { prop: 'isavailable', label: '状态', width: 90, slot: 'availableSlot', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  isavailable: 1,
  channelprice: 0
})

const formRules = {
  channelname: [{ required: true, message: '通道名称不能为空', trigger: 'blur' }],
  channeltype: [{ required: true, message: '请选择运营商类型', trigger: 'change' }],
  channelarea: [{ required: true, message: '通道地区不能为空', trigger: 'blur' }],
  channelprice: [{ required: true, message: '通道价格价格不能为空', trigger: 'blur' }],
  protocaltype: [{ required: true, message: '请选择协议类型', trigger: 'change' }]
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
    isavailable: 1,
    channelprice: 0
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getChannelInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data || {}
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取通道详情失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateChannel(formModel.value) : await saveChannel(formModel.value)
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
      const res: any = await deleteChannels(selectedIds.value)
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
.channel-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
