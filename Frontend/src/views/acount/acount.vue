<template>
  <div class="acount-management">
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
      :request-api="getAcountList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom render for Paid Value -->
      <template #moneySlot="scope">
        <span>{{ (scope.row.paidvalue / 1000.0).toFixed(2) }} 元</span>
      </template>

      <!-- Custom render for Payment Type -->
      <template #paymentSlot="scope">
        <el-tag v-if="scope.row.paymentid == '1'" type="success">微信</el-tag>
        <el-tag v-else-if="scope.row.paymentid == '2'" type="primary">支付宝</el-tag>
        <span v-else>-</span>
      </template>

      <!-- Custom render for Date Times -->
      <template #timeSlot="scope">
        <span>{{ formatDateTime(scope.row.createtime) }}</span>
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
        <el-form-item label="所属客户" prop="clientid">
          <el-select
            v-model="formModel.clientid"
            placeholder="请选择所属客户"
            style="width: 100%"
          >
            <el-option
              v-for="site in clientOptions"
              :key="site.id"
              :label="site.corpname"
              :value="site.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="到账金额 (元)" prop="paidvalueYuan">
          <el-input-number
            v-model="formModel.paidvalueYuan"
            :min="0.01"
            :precision="2"
            :step="1"
            style="width: 100%"
            placeholder="请输入到账金额"
          />
        </el-form-item>
        <el-form-item label="支付方式" prop="paymentid">
          <el-radio-group v-model="formModel.paymentid">
            <el-radio value="1">微信</el-radio>
            <el-radio value="2">支付宝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="支付流水号" prop="paymentorder">
          <el-input v-model="formModel.paymentorder" placeholder="第三方支付订单号" />
        </el-form-item>
        <el-form-item label="备注说明" prop="paymentinfo">
          <el-input v-model="formModel.paymentinfo" type="textarea" :rows="2" placeholder="请输入充值账单备注信息" />
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
import { getAcountList, getAcountInfo, saveAcount, updateAcount, deleteAcountBatch } from '@/api/acount'
import { getClientBusinessAll } from '@/api/client'

const tableRef = ref()
const formRef = ref<FormInstance>()
const clientOptions = ref<any[]>([])

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '搜索订单', prop: 'search', type: 'input', placeholder: '订单号/商户名/支付渠道搜索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'orderid', label: '到账订单号', width: 140 },
  { prop: 'corpname', label: '客户公司' },
  { prop: 'paidvalue', label: '到账金额', width: 120, slot: 'moneySlot', align: 'right' },
  { prop: 'createtime', label: '到账时间', width: 160, slot: 'timeSlot', align: 'center' },
  { prop: 'paymentid', label: '支付方式', width: 90, slot: 'paymentSlot', align: 'center' },
  { prop: 'paymentorder', label: '渠道流水号', showOverflowTooltip: true }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  paymentid: '2',
  paidvalueYuan: 10
})

const formRules = {
  clientid: [{ required: true, message: '请选择所属客户', trigger: 'change' }],
  paidvalueYuan: [{ required: true, message: '到账金额不能为空', trigger: 'blur' }]
}

async function loadClients() {
  try {
    const res: any = await getClientBusinessAll()
    if (res && res.code === 0) {
      clientOptions.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
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

function formatDateTime(time: number) {
  if (!time) return '-'
  const date = new Date(time)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {
    paymentid: '2',
    paidvalueYuan: 10
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getAcountInfo(id)
    if (res && res.code === 0) {
      const data = res.data || {}
      // 厘换算为元展示在表单中
      data.paidvalueYuan = data.paidvalue ? (data.paidvalue / 1000.0) : 0
      formModel.value = data
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取到账订单失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 元转换为厘提交给后台
        const payload = {
          ...formModel.value,
          paidvalue: Math.round(formModel.value.paidvalueYuan * 1000)
        }
        const isEdit = payload.id !== undefined
        const res: any = isEdit ? await updateAcount(payload) : await saveAcount(payload)
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
  ElMessageBox.confirm('您确定要删除所选到账单据吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res: any = await deleteAcountBatch(selectedIds.value)
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

onMounted(() => {
  loadClients()
})
</script>

<style scoped>
.acount-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
