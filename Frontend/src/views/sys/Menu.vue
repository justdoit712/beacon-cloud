<template>
  <div class="menu-management">
    <page-header title="菜单管理">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增菜单</el-button>
      </template>
    </page-header>

    <!-- Search Form -->
    <pro-search
      v-model="searchParam"
      :search-config="searchConfig"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- Action Bar -->
    <div class="table-actions flex space-x-2 mb-4">
      <el-button type="warning" plain :icon="Edit" :disabled="selectedIds.length !== 1" @click="handleEdit">修改</el-button>
      <el-button type="danger" plain :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">删除</el-button>
    </div>

    <!-- Data Table -->
    <pro-table
      ref="tableRef"
      :columns="columns"
      :request-api="getMenuList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom Icon Render -->
      <template #iconSlot="scope">
        <el-icon v-if="scope.row.icon"><component :is="getIconComponent(scope.row.icon)" /></el-icon>
        <span v-else>-</span>
      </template>

      <!-- Custom Type Render -->
      <template #typeSlot="scope">
        <status-tag v-if="scope.row.type === 0" status="primary" text="目录" />
        <status-tag v-else-if="scope.row.type === 1" status="success" text="菜单" />
        <status-tag v-else-if="scope.row.type === 2" status="info" text="按钮" />
      </template>

      <!-- Custom Actions -->
      <template #action="{ row }">
        <action-buttons :actions="getRowActions(row)" />
      </template>
    </pro-table>

    <!-- Add/Edit Dialog -->
    <form-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      size="medium"
      :loading="saving"
      @confirm="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="formModel.type">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="formModel.name" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="formModel.parentId"
            :data="menuOptions"
            check-strictly
            placeholder="请选择上级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="formModel.type === 1" label="菜单URL" prop="url">
          <el-input v-model="formModel.url" placeholder="请输入菜单URL (例如: sys/user.html)" />
        </el-form-item>
        <el-form-item v-if="formModel.type === 1 || formModel.type === 2" label="授权标识" prop="perms">
          <el-input v-model="formModel.perms" placeholder="多个逗号分隔 (例如: sys:user:list,sys:user:save)" />
        </el-form-item>
        <el-form-item v-if="formModel.type !== 2" label="图标" prop="icon">
          <el-input v-model="formModel.icon" placeholder="请输入图标样式类 (例如: fa fa-cog)" />
        </el-form-item>
        <el-form-item v-if="formModel.type !== 2" label="排序" prop="orderNum">
          <el-input-number v-model="formModel.orderNum" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
    </form-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import { getMenuList, getMenuInfo, getMenuSelect, saveMenu, updateMenu, deleteMenus } from '@/api/menu'

const tableRef = ref()
const formRef = ref<FormInstance>()

const searchParam = ref({
  search: ''
})

const searchConfig = [
  { label: '关键字', prop: 'search', type: 'input', placeholder: '菜单名称' }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'name', label: '菜单名称' },
  { prop: 'parentName', label: '上级菜单' },
  { prop: 'icon', label: '图标', width: 80, align: 'center', slot: 'iconSlot' },
  { prop: 'url', label: '菜单URL' },
  { prop: 'perms', label: '授权标识' },
  { prop: 'type', label: '类型', width: 90, slot: 'typeSlot', align: 'center' },
  { prop: 'orderNum', label: '排序', width: 80, align: 'center' },
  { label: '操作', slot: 'action', width: 120, fixed: 'right', align: 'center' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)
const menuOptions = ref<any[]>([])

const formModel = ref<any>({
  type: 0,
  parentId: 0,
  orderNum: 0
})

const formRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }]
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

function buildTree(list: any[]): any[] {
  const map: any = {}
  const roots: any[] = []
  
  list.forEach(item => {
    item.value = item.id
    item.label = item.name
    item.children = []
    map[item.id] = item
  })
  
  list.forEach(item => {
    if (item.parentId === -1 || item.parentId === '-1' || item.id === 0) {
      if (item.id === 0) {
        roots.push(item)
      }
    } else {
      const parent = map[item.parentId]
      if (parent) {
        parent.children.push(item)
      } else {
        roots.push(item)
      }
    }
  })
  return roots
}

async function loadMenuOptions() {
  try {
    const res: any = await getMenuSelect()
    if (res && res.code === 0) {
      menuOptions.value = buildTree(res.data || [])
    }
  } catch (error) {
    console.error(error)
  }
}

async function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {
    type: 0,
    parentId: 0,
    orderNum: 0
  }
  await loadMenuOptions()
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    await loadMenuOptions()
    const res: any = await getMenuInfo(id)
    if (res && res.code === 0) {
      // 兼容后端返回的对象层级
      formModel.value = res.data.menu || res.data
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取菜单信息失败')
  }
}

async function handleEditRow(row: any) {
  dialogTitle.value = '修改'
  try {
    await loadMenuOptions()
    const res: any = await getMenuInfo(row.id)
    if (res && res.code === 0) {
      formModel.value = res.data.menu || res.data
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取菜单信息失败')
  }
}

function getRowActions(row: any) {
  return [
    { text: '编辑', onClick: () => handleEditRow(row) },
    { 
      text: '删除', 
      danger: true, 
      confirmText: '确定删除该菜单吗？',
      onClick: async () => {
        try {
          const res: any = await deleteMenus([row.id])
          if (res && res.code === 0) {
            ElMessage.success('删除成功')
            tableRef.value?.reload()
          } else {
            ElMessage.error(res.msg || '删除失败')
          }
        } catch (error: any) {
          ElMessage.error(error.message || '网络错误')
        }
      }
    }
  ]
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const isEdit = formModel.value.id !== undefined
        const res: any = isEdit ? await updateMenu(formModel.value) : await saveMenu(formModel.value)
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
      const res: any = await deleteMenus(selectedIds.value)
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

function getIconComponent(iconStr: string | null) {
  if (!iconStr) return null
  // 转换 fa fa-cog -> Setting
  let name = iconStr.replace('fa fa-', '')
  name = name.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join('')
  
  if (name === 'Cog' || name === 'Gear') name = 'Setting'
  if (name === 'UserSecret') name = 'User'
  if (name === 'ThList') name = 'List'
  if (name === 'Bug') name = 'Warning'
  if (name === 'Tasks') name = 'Checked'
  if (name === 'SunO') name = 'Sunny'
  
  return (Icons as any)[name] || Icons.Menu
}
</script>

<style scoped>
.menu-management {
  /* Use layout spacing */
}
</style>
