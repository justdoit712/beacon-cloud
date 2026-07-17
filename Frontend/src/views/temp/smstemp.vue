<template>
  <div class="smstemp-management">
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
      :request-api="getTemplateList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom render for Template Type -->
      <template #typeSlot="scope">
        <el-tag v-if="scope.row.templateType === 0" type="primary">验证码类</el-tag>
        <el-tag v-else-if="scope.row.templateType === 1" type="success">通知类</el-tag>
        <el-tag v-else-if="scope.row.templateType === 2" type="warning">营销类</el-tag>
        <span v-else>-</span>
      </template>

      <!-- Custom render for Audit State -->
      <template #stateSlot="scope">
        <el-tag v-if="scope.row.templateState === 0" type="info">审核中</el-tag>
        <el-tag v-else-if="scope.row.templateState === 1" type="danger">审核不通过</el-tag>
        <el-tag v-else-if="scope.row.templateState === 2" type="success">审核通过</el-tag>
        <span v-else>-</span>
      </template>

      <!-- Custom render for Use Scene -->
      <template #useSlot="scope">
        <span v-if="scope.row.useId === 0">网站</span>
        <span v-else-if="scope.row.useId === 1">APP</span>
        <span v-else-if="scope.row.useId === 2">微信</span>
        <span v-else>-</span>
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
        <el-form-item label="签名 ID" prop="signId">
          <el-input-number v-model="formModel.signId" :min="1" style="width: 100%" placeholder="对应 client_sign.id" />
        </el-form-item>
        <el-form-item label="模板内容" prop="templateText">
          <el-input v-model="formModel.templateText" type="textarea" :rows="3" placeholder="请输入模板内容" />
        </el-form-item>
        <el-form-item label="模板类型" prop="templateType">
          <el-radio-group v-model="formModel.templateType">
            <el-radio :value="0">验证码类</el-radio>
            <el-radio :value="1">通知类</el-radio>
            <el-radio :value="2">营销类</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核状态" prop="templateState">
          <el-radio-group v-model="formModel.templateState">
            <el-radio :value="0">审核中</el-radio>
            <el-radio :value="1">审核不通过</el-radio>
            <el-radio :value="2">审核通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="使用场景" prop="useId">
          <el-radio-group v-model="formModel.useId">
            <el-radio :value="0">网站</el-radio>
            <el-radio :value="1">APP</el-radio>
            <el-radio :value="2">微信</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="使用地址" prop="useWeb">
          <el-input v-model="formModel.useWeb" placeholder="网站地址、APP或微信使用场景说明" />
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
import { getTemplateList, getTemplateInfo, saveTemplate, updateTemplate, deleteTemplates } from '@/api/smstemp'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '模板内容', prop: 'search', type: 'input', placeholder: '请输入模板内容搜索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'signId', label: '签名ID', width: 90, sortable: true, align: 'center' },
  { prop: 'templateText', label: '模板内容', showOverflowTooltip: true },
  { prop: 'templateType', label: '模板类型', width: 100, slot: 'typeSlot', align: 'center' },
  { prop: 'templateState', label: '审核状态', width: 110, slot: 'stateSlot', align: 'center' },
  { prop: 'useId', label: '使用场景', width: 100, slot: 'useSlot', align: 'center' },
  { prop: 'useWeb', label: '使用地址', showOverflowTooltip: true },
  { prop: 'updated', label: '更新时间', width: 160, align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  templateType: 0,
  templateState: 0,
  useId: 0
})

const formRules = {
  signId: [{ required: true, message: '签名ID不能为空', trigger: 'blur' }],
  templateText: [{ required: true, message: '模板内容不能为空', trigger: 'blur' }],
  useWeb: [{ required: true, message: '使用地址不能为空', trigger: 'blur' }]
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
    templateType: 0,
    templateState: 0,
    useId: 0
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getTemplateInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data || {}
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取模板详情失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateTemplate(formModel.value) : await saveTemplate(formModel.value)
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
      const res: any = await deleteTemplates(selectedIds.value)
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
.smstemp-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
