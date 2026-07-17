<template>
  <div class="apigatewayfilter-management">
    <!-- Read-Only Notice -->
    <el-alert
      title="配置读取说明"
      type="info"
      description="API 网关过滤器来自 Nacos 配置中心的实时动态载入，当前页面仅支持只读查看，如需修改请前往配置中心操作。"
      show-icon
      :closable="false"
      style="margin-bottom: 16px;"
    />

    <!-- Data Table -->
    <pro-table
      ref="tableRef"
      :columns="columns"
      :request-api="getGatewayFilterList"
    >
      <!-- Custom render for Read State -->
      <template #stateSlot="scope">
        <el-tag :type="scope.row.readState === 1 ? 'success' : 'danger'">
          {{ scope.row.readStateText }}
        </el-tag>
      </template>
    </pro-table>
  </div>
</template>

<script setup lang="ts">
import { getGatewayFilterList } from '@/api/filter'

const columns = [
  { prop: 'id', label: '序号', width: 60, type: 'index' },
  { prop: 'serviceName', label: '服务名称' },
  { prop: 'dataId', label: 'Data ID' },
  { prop: 'group', label: 'Group', width: 140 },
  { prop: 'filters', label: '过滤器列表', showOverflowTooltip: true },
  { prop: 'readState', label: '读取状态', width: 110, slot: 'stateSlot', align: 'center' },
  { prop: 'message', label: '说明信息', showOverflowTooltip: true }
] as any[]
</script>

<style scoped>
.apigatewayfilter-management {
  padding: 10px 0;
}
</style>
