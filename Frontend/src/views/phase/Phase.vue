<template>
  <div class="phase-management">
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
      :request-api="getPhaseList"
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
        label-width="100px"
      >
        <el-form-item label="号段" prop="phase">
          <el-input v-model="formModel.phase" placeholder="请输入号段 (例如: 139)" />
        </el-form-item>
        <el-form-item label="省份" prop="provId">
          <el-select
            v-model="formModel.provId"
            placeholder="请选择省份"
            style="width: 100%"
            @change="handleProvinceChange"
          >
            <el-option
              v-for="item in provinces"
              :key="item.id"
              :label="item.areaname"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="城市" prop="cityId">
          <el-select
            v-model="formModel.cityId"
            placeholder="请选择城市"
            style="width: 100%"
            :disabled="!formModel.provId"
          >
            <el-option
              v-for="item in cities"
              :key="item.id"
              :label="item.areaname"
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
import { getPhaseList, getPhaseInfo, savePhase, updatePhase, deletePhases, getProvinces, getCities } from '@/api/phase'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '关键字', prop: 'search', type: 'input', placeholder: '请输入搜索关键字' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'phase', label: '号段' },
  { prop: 'provName', label: '省份' },
  { prop: 'cityName', label: '城市' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const provinces = ref<any[]>([])
const cities = ref<any[]>([])

const formModel = ref<any>({
  provId: '',
  cityId: ''
})

const formRules = {
  phase: [
    { required: true, message: '请输入号段', trigger: 'blur' },
    { pattern: /^\d{3,7}$/, message: '请输入3-7位数字号段前缀', trigger: 'blur' }
  ],
  provId: [{ required: true, message: '请选择省份', trigger: 'change' }],
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }]
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

async function loadProvinces() {
  try {
    const res: any = await getProvinces()
    if (res && res.code === 0) {
      provinces.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

async function handleProvinceChange(provId: string | number) {
  formModel.value.cityId = ''
  cities.value = []
  if (!provId) return
  try {
    const res: any = await getCities(provId)
    if (res && res.code === 0) {
      cities.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

async function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {
    provId: '',
    cityId: ''
  }
  cities.value = []
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getPhaseInfo(id)
    if (res && res.code === 0) {
      const data = res.data || {}
      formModel.value = {
        id: data.id,
        phase: data.phase,
        provId: data.provId,
        cityId: data.cityId
      }
      // 级联加载对应城市列表
      if (data.provId) {
        const cityRes: any = await getCities(data.provId)
        if (cityRes && cityRes.code === 0) {
          cities.value = cityRes.data || []
        }
      }
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取号段详情失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updatePhase(formModel.value) : await savePhase(formModel.value)
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
      const res: any = await deletePhases(selectedIds.value)
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
  loadProvinces()
})
</script>

<style scoped>
.phase-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
</style>
