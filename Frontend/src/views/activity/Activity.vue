<template>
  <div class="activity-management">
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
      :request-api="getActivityList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom Render for Begin Time -->
      <template #beginTimeSlot="scope">
        <span>{{ formatDateTime(scope.row.beginTime) }}</span>
      </template>

      <!-- Custom Render for End Time -->
      <template #endTimeSlot="scope">
        <span>{{ formatDateTime(scope.row.endTime) }}</span>
      </template>

      <!-- Custom Render for Link -->
      <template #linkSlot="scope">
        <el-link v-if="scope.row.link" :href="scope.row.link" target="_blank" type="primary">
          {{ scope.row.link }}
        </el-link>
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
        label-width="120px"
      >
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="formModel.title" placeholder="如：汽车行业发展" />
        </el-form-item>
        
        <el-form-item label="活动描述" prop="description">
          <el-input v-model="formModel.description" type="textarea" placeholder="如：资讯来源" />
        </el-form-item>

        <el-form-item label="活动创建者" prop="author">
          <el-input v-model="formModel.author" placeholder="请输入活动创建者" />
        </el-form-item>

        <el-form-item label="SEO 关键字" prop="seoKeywords">
          <el-input v-model="formModel.seoKeywords" placeholder="请输入 SEO 关键字，用逗号分隔" />
        </el-form-item>

        <el-form-item label="活动封面">
          <el-upload
            action="http://localhost:8080/ytupload"
            accept="image/jpeg,image/gif,image/png"
            :on-success="handleUploadSuccess"
            name="mypic"
            :show-file-list="false"
            class="avatar-uploader"
          >
            <img v-if="formModel.coverPic" :src="formModel.coverPic" class="avatar" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">请上传图片格式文件，上传成功后会自动回填封面。</div>
        </el-form-item>

        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="x"
            @change="handleTimeRangeChange"
          />
        </el-form-item>

        <el-form-item label="活动链接" prop="link">
          <el-input v-model="formModel.link" placeholder="请输入活动链接" />
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
import { getActivityList, getActivityInfo, saveActivity, updateActivity, deleteActivities } from '@/api/activity'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '关键字', prop: 'search', type: 'input', placeholder: '请输入活动标题/创建者' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: 'ID', width: 80, sortable: true },
  { prop: 'title', label: '标题', showOverflowTooltip: true },
  { prop: 'author', label: '活动创建者', width: 140 },
  { prop: 'beginTime', label: '开始时间', width: 160, slot: 'beginTimeSlot' },
  { prop: 'endTime', label: '结束时间', width: 160, slot: 'endTimeSlot' },
  { prop: 'link', label: '活动链接', slot: 'linkSlot', showOverflowTooltip: true }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({})
const timeRange = ref<[number, number] | null>(null)

const formRules = {
  title: [
    { required: true, message: '请输入活动标题', trigger: 'blur' }
  ],
  author: [
    { required: true, message: '请输入活动创建者', trigger: 'blur' }
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

function formatDateTime(time: number | string) {
  if (!time) return '-'
  const date = new Date(Number(time))
  if (isNaN(date.getTime())) return String(time)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function handleUploadSuccess(res: any) {
  if (res && res.code === 0) {
    formModel.value.coverPic = res.file
    ElMessage.success('封面图片上传成功')
  } else {
    ElMessage.error(res?.msg || '上传失败')
  }
}

function handleTimeRangeChange(val: any) {
  if (val && val.length === 2) {
    formModel.value.beginTime = val[0]
    formModel.value.endTime = val[1]
  } else {
    formModel.value.beginTime = null
    formModel.value.endTime = null
  }
}

function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {}
  timeRange.value = null
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getActivityInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.activity || res.data
      if (formModel.value.beginTime && formModel.value.endTime) {
        timeRange.value = [Number(formModel.value.beginTime), Number(formModel.value.endTime)]
      } else {
        timeRange.value = null
      }
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取活动详情失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateActivity(formModel.value) : await saveActivity(formModel.value)
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
      const res: any = await deleteActivities(selectedIds.value)
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
.activity-management {
  padding: 10px 0;
}
.table-actions {
  margin-bottom: 16px;
}
.avatar-uploader {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 120px;
  height: 120px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.avatar-uploader:hover {
  border-color: var(--el-color-primary);
}
.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 100%;
  height: 100%;
  line-height: 120px;
  text-align: center;
}
.avatar {
  width: 120px;
  height: 120px;
  display: block;
  object-fit: cover;
}
.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
