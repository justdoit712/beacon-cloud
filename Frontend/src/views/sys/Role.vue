<template>
  <div class="role-management">
    <page-header title="角色管理">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增角色</el-button>
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
      :request-api="getRoleList"
      :init-param="searchParam"
      @selection-change="handleSelectionChange"
    >
      <!-- Custom Status Render -->
      <template #statusSlot="scope">
        <status-tag :status="scope.row.status === 1 ? 'success' : 'warning'" :text="scope.row.status === 1 ? '有效' : '无效'" />
      </template>

      <!-- Operations -->
      <template #action="{ row }">
        <action-buttons :actions="getRowActions(row)" />
      </template>
    </pro-table>

    <!-- Add/Edit Dialog -->
    <form-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      size="small"
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
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="formModel.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formModel.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formModel.status">
            <el-radio :value="1">有效</el-radio>
            <el-radio :value="0">无效</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </form-dialog>

    <!-- Permission Assignment Tree Dialog -->
    <form-dialog
      v-model="permDialogVisible"
      title="分配菜单权限"
      size="small"
      :loading="assigning"
      @confirm="handleAssignSubmit"
      @cancel="permDialogVisible = false"
    >
      <div v-loading="treeLoading" class="tree-container">
        <el-scrollbar max-height="400px">
          <el-tree
            ref="treeRef"
            :data="menuTreeData"
            :props="treeProps"
            node-key="id"
            show-checkbox
            default-expand-all
            check-strictly
          />
        </el-scrollbar>
      </div>
    </form-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { Plus, Edit, Delete, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type ElTree } from 'element-plus'
import { getRoleList, getRoleInfo, saveRole, updateRole, deleteRoles, getMenuTree, getRoleMenuIds, assignRoleMenus } from '@/api/role'

const tableRef = ref()
const formRef = ref<FormInstance>()
const treeRef = ref<InstanceType<typeof ElTree>>()

const searchParam = ref({
  name: '',
  status: ''
})

const searchConfig = [
  { label: '角色名称', prop: 'name', type: 'input', placeholder: '请输入角色名称' },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    placeholder: '请选择状态',
    options: [
      { label: '有效', value: '1' },
      { label: '无效', value: '0' }
    ]
  }
] as any[]

const columns = [
  { type: 'selection', width: 60 },
  { prop: 'id', label: '编号', width: 80, sortable: true },
  { prop: 'name', label: '角色名称' },
  { prop: 'remark', label: '备注' },
  { prop: 'status', label: '状态', slot: 'statusSlot', align: 'center' },
  { prop: 'createTime', label: '创建时间' },
  { label: '操作', width: 220, align: 'center', slot: 'action', fixed: 'right' }
] as any[]

const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const saving = ref(false)

const formModel = ref<any>({
  status: 1
})

const formRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

// Permission tree states
const permDialogVisible = ref(false)
const treeLoading = ref(false)
const assigning = ref(false)
const currentRoleId = ref<number>()
const menuTreeData = ref<any[]>([])
const treeProps = {
  label: 'name',
  children: 'children'
}

function handleSearch() {
  tableRef.value?.reload()
}

function handleReset() {
  searchParam.value.name = ''
  searchParam.value.status = ''
  tableRef.value?.reload()
}

function handleSelectionChange(selection: any[]) {
  selectedIds.value = selection.map(item => item.id)
}

function handleAdd() {
  dialogTitle.value = '新增'
  formModel.value = {
    status: 1
  }
  dialogVisible.value = true
}

async function handleEdit() {
  if (selectedIds.value.length !== 1) return
  dialogTitle.value = '修改'
  const id = selectedIds.value[0]
  try {
    const res: any = await getRoleInfo(id)
    if (res && res.code === 0) {
      formModel.value = res.data.role || res.data
      dialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取角色信息失败')
  }
}

function handleEditRow(row: any) {
  dialogTitle.value = '修改'
  formModel.value = { ...row }
  dialogVisible.value = true
}

function getRowActions(row: any) {
  return [
    { text: '分配权限', onClick: () => handleAssignPermission(row) },
    { text: '编辑', onClick: () => handleEditRow(row) },
    { 
      text: '删除', 
      danger: true, 
      confirmText: '确定删除该角色吗？',
      onClick: async () => {
        try {
          const res: any = await deleteRoles([row.id])
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
        const res: any = isEdit ? await updateRole(formModel.value) : await saveRole(formModel.value)
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
      const res: any = await deleteRoles(selectedIds.value)
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

// Convert flat tree array into recursive children structure
function buildTree(list: any[]): any[] {
  const map: any = {}
  const roots: any[] = []
  
  list.forEach(item => {
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

async function handleAssignPermission(row: any) {
  currentRoleId.value = row.id
  permDialogVisible.value = true
  treeLoading.value = true
  try {
    // 1. 获取菜单列表并转换为树结构
    const treeRes: any = await getMenuTree()
    if (treeRes && treeRes.code === 0) {
      menuTreeData.value = buildTree(treeRes.data || [])
    }
    
    // 2. 获取该角色当前已分配的菜单 ID
    const menuIdsRes: any = await getRoleMenuIds(row.id)
    if (menuIdsRes && menuIdsRes.code === 0) {
      nextTick(() => {
        treeRef.value?.setCheckedKeys(menuIdsRes.data || [])
      })
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取菜单权限失败')
  } finally {
    treeLoading.value = false
  }
}

async function handleAssignSubmit() {
  if (!currentRoleId.value) return
  assigning.value = true
  try {
    // 收集勾选的菜单节点 (包含完全勾选和半选节点)
    const checkedKeys = treeRef.value?.getCheckedKeys() as number[]
    const halfCheckedKeys = treeRef.value?.getHalfCheckedKeys() as number[]
    const allKeys = [...(checkedKeys || []), ...(halfCheckedKeys || [])]
    
    const res: any = await assignRoleMenus(currentRoleId.value, allKeys)
    if (res && res.code === 0) {
      ElMessage.success('分配权限成功')
      permDialogVisible.value = false
    } else {
      ElMessage.error(res.msg || '分配权限失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '网络请求错误')
  } finally {
    assigning.value = false
  }
}
</script>

<style scoped>
.role-management {
  /* Use layout spacing */
}
.tree-container {
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 12px;
  background-color: var(--panel-muted-bg);
}
</style>
