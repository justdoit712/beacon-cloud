<template>
  <div class="smssearch-management">
    <!-- Custom Search Form -->
    <el-card class="box-card search-card" style="margin-bottom: 20px;">
      <el-form :inline="true" :model="searchParam" class="demo-form-inline">
        <el-form-item label="所属客户">
          <el-select
            v-model="searchParam.clientID"
            placeholder="全部客户"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="site in clientOptions"
              :key="site.id"
              :label="site.corpname"
              :value="site.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="searchParam.mobile" placeholder="手机号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="内容关键字">
          <el-input v-model="searchParam.content" placeholder="短信内容" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="rawStartTime"
            type="datetime"
            placeholder="开始时间"
            value-format="x"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="rawStopTime"
            type="datetime"
            placeholder="结束时间"
            value-format="x"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Data Table -->
    <pro-table
      ref="tableRef"
      :columns="columns"
      :request-api="getSmsRecordList"
      :init-param="mappedParams"
    >
      <!-- Custom render for Report State -->
      <template #stateSlot="scope">
        <el-tag v-if="scope.row.reportState === 0" type="info">等待</el-tag>
        <el-tag v-else-if="scope.row.reportState === 1" type="success">成功</el-tag>
        <el-tag v-else-if="scope.row.reportState === 2" type="danger">失败</el-tag>
        <span v-else>-</span>
      </template>

      <!-- Custom render for Operator -->
      <template #operatorSlot="scope">
        <el-tag v-if="scope.row.operatorId === 1" type="primary">移动</el-tag>
        <el-tag v-else-if="scope.row.operatorId === 2" type="success">联通</el-tag>
        <el-tag v-else-if="scope.row.operatorId === 3" type="warning">电信</el-tag>
        <el-tag v-else type="info">未知</el-tag>
      </template>
    </pro-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getSmsRecordList } from '@/api/search'
import { getClientBusinessAll } from '@/api/client'

const tableRef = ref()
const clientOptions = ref<any[]>([])

const searchParam = ref<any>({
  clientID: '',
  mobile: '',
  content: ''
})

const rawStartTime = ref<string>('')
const rawStopTime = ref<string>('')

const columns = [
  { prop: 'id', label: '序号', width: 60, type: 'index' },
  { prop: 'corpname', label: '客户名称', width: 140 },
  { prop: 'sendTimeStr', label: '发送时间', width: 160, align: 'center' },
  { prop: 'reportState', label: '状态', width: 90, slot: 'stateSlot', align: 'center' },
  { prop: 'operatorId', label: '运营商', width: 90, slot: 'operatorSlot', align: 'center' },
  { prop: 'errorMsg', label: '错误原因', width: 120, showOverflowTooltip: true },
  { prop: 'srcNumber', label: '发送号', width: 120 },
  { prop: 'mobile', label: '手机号', width: 120, align: 'center' },
  { prop: 'text', label: '短信内容', showOverflowTooltip: true }
] as any[]

// 映射日期选择器的毫秒级时间戳
const mappedParams = computed(() => {
  return {
    clientID: searchParam.value.clientID || '',
    mobile: searchParam.value.mobile || '',
    content: searchParam.value.content || '',
    starttime: rawStartTime.value ? Number(rawStartTime.value) : null,
    stoptime: rawStopTime.value ? Number(rawStopTime.value) : null
  }
})

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
  searchParam.value = {
    clientID: '',
    mobile: '',
    content: ''
  }
  rawStartTime.value = ''
  rawStopTime.value = ''
  tableRef.value?.reload()
}

onMounted(() => {
  loadClients()
})
</script>

<style scoped>
.smssearch-management {
  padding: 10px 0;
}
.search-card :deep(.el-card__body) {
  padding-bottom: 2px;
}
</style>
