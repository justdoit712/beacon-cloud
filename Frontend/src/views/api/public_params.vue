<template>
  <div class="public-params">
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
      <!-- Custom render for Must -->
      <template #mustSlot="scope">
        <el-tag :type="scope.row.isMust == 1 ? 'danger' : 'info'">
          {{ scope.row.isMust == 1 ? '必须' : '可选' }}
        </el-tag>
      </template>

      <!-- Custom render for State -->
      <template #stateSlot="scope">
        <el-tag :type="scope.row.enableState == 1 ? 'success' : 'danger'">
          {{ scope.row.enableState == 1 ? '启用' : '已停用' }}
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
        <el-form-item label="参数名称" prop="paramName">
          <el-input v-model="formModel.paramName" placeholder="请输入参数键名称" />
        </el-form-item>
        <el-form-item label="参数类型" prop="paramType">
          <el-input v-model="formModel.paramType" placeholder="字段数据类型 (例如: String, Integer)" />
        </el-form-item>
        <!-- Notice the 'descripton' spelling! -->
        <el-form-item label="介绍描述" prop="descripton">
          <el-input v-model="formModel.descripton" placeholder="请输入公共参数用途介绍" />
        </el-form-item>
        <el-form-item label="是否必须" prop="isMust">
          <el-radio-group v-model="formModel.isMust">
            <el-radio :value="1">必须</el-radio>
            <el-radio :value="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="enableState">
          <el-radio-group v-model="formModel.enableState">
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
const FAMILY = 'public-params'

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '参数名', prop: 'search', type: 'input', placeholder: '参数名搜索' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'paramName', label: '参数名称' },
  { prop: 'paramType', label: '参数类型', width: 120 },
  { prop: 'descripton', label: '描述说明', showOverflowTooltip: true },
  { prop: 'isMust', label: '是否必须', width: 100, slot: 'mustSlot', align: 'center' },
  { prop: 'enableState', label: '状态', width: 100, slot: 'stateSlot', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  isMust: 1,
  enableState: 1
})

const formRules = {
  paramName: [{ required: true, message: '参数名不能为空', trigger: 'blur' }],
  paramType: [{ required: true, message: '参数类型不能为空', trigger: 'blur' }],
  descripton: [{ required: true, message: '描述介绍不能为空', trigger: 'blur' }]
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
    isMust: 1,
    enableState: 1
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
    ElMessage.error(error.message || '获取参数详情失败')
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
.public-params {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
