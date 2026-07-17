<template>
  <div class="pro-table">
    <!-- Table -->
    <el-table
      v-loading="loading"
      :data="tableData"
      border
      stripe
      v-bind="$attrs"
    >
      <template v-for="col in columns" :key="col.prop || col.type">
        <!-- Selection or Index -->
        <el-table-column
          v-if="col.type === 'selection' || col.type === 'index'"
          :type="col.type"
          :width="col.width || 60"
          :align="col.align || 'center'"
          :label="col.label"
        />

        <!-- Custom Slot Column -->
        <el-table-column
          v-else-if="col.slot"
          v-bind="col"
        >
          <template #default="scope">
            <slot :name="col.slot" v-bind="scope" />
          </template>
        </el-table-column>

        <!-- Standard Column -->
        <el-table-column
          v-else
          v-bind="col"
        />
      </template>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

interface ColumnConfig {
  type?: 'selection' | 'index'
  prop?: string
  label?: string
  width?: string | number
  align?: 'left' | 'center' | 'right'
  slot?: string
  [key: string]: any
}

const props = withDefaults(
  defineProps<{
    columns: ColumnConfig[]
    requestApi: (params: any) => Promise<any>
    initParam?: any
  }>(),
  {
    initParam: () => ({})
  }
)

const tableData = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function getTableData() {
  if (!props.requestApi) return
  loading.value = true
  try {
    // 转换为 limit 和 offset 契约
    const offset = (currentPage.value - 1) * pageSize.value
    const params = {
      limit: pageSize.value,
      offset,
      ...props.initParam
    }
    const res = await props.requestApi(params)
    // 兼容后端返回的分页数据格式，比如 { code: 0, total: 100, rows: [] } 或者 { data: { total: 100, list: [] } }
    if (res) {
      tableData.value = res.rows || res.data?.rows || res.data || []
      total.value = res.total || res.data?.total || 0
    }
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  currentPage.value = 1
  getTableData()
}

function handleCurrentChange() {
  getTableData()
}

function reload() {
  currentPage.value = 1
  getTableData()
}

// 监听参数变化重新加载
watch(() => props.initParam, () => {
  currentPage.value = 1
  getTableData()
}, { deep: true })

onMounted(() => {
  getTableData()
})

// 暴露方法给父组件使用
defineExpose({
  reload,
  getTableData
})
</script>

<style scoped>
.pro-table {
  background-color: #fff;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
