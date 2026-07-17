<template>
  <div class="searchparams-management">
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
      <!-- Custom render for Range extreme -->
      <template #extremeSlot="scope">
        <span v-if="scope.row.tOrder === 0">0 - range起点</span>
        <span v-else-if="scope.row.tOrder === 1">1 - range终点</span>
        <span v-else>-</span>
      </template>

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
        <el-form-item label="请求参数名" prop="name">
          <el-input v-model="formModel.name" placeholder="请输入外部 API 请求参数名" />
        </el-form-item>
        <!-- Notice the 'cloum' spelling! -->
        <el-form-item label="对应搜索参数列" prop="cloum">
          <el-input v-model="formModel.cloum" placeholder="ES 内部检索参数列字段" />
        </el-form-item>
        <el-form-item label="搜索匹配类型" prop="type">
          <el-input v-model="formModel.type" placeholder="如 match, term, range" />
        </el-form-item>
        <el-form-item label="参数起点/终点" prop="tOrder">
          <el-radio-group v-model="formModel.tOrder">
            <el-radio :value="0">0 - range起点</el-radio>
            <el-radio :value="1">1 - range终点</el-radio>
          </el-radio-group>
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
const FAMILY = 'search-params'

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '参数名', prop: 'search', type: 'input', placeholder: '请输入请求参数名查询' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'name', label: '请求参数名' },
  { prop: 'cloum', label: '搜索字段映射' },
  { prop: 'type', label: '搜索匹配类型', width: 120, align: 'center' },
  { prop: 'tOrder', label: '极值范围限制', width: 140, slot: 'extremeSlot' },
  { prop: 'state', label: '状态', width: 100, slot: 'stateSlot', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  state: 1,
  tOrder: 0
})

const formRules = {
  name: [{ required: true, message: '请求参数名不能为空', trigger: 'blur' }],
  cloum: [{ required: true, message: '搜索列名参数不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '搜索匹配类型不能为空', trigger: 'blur' }]
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
    state: 1,
    tOrder: 0
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
.searchparams-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
