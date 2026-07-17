<template>
  <div class="userpay-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span class="title">客户充值</span>
          <span class="subtitle">为指定客户充值账户余额，充值成功后余额实时生效。</span>
        </div>
      </template>

      <el-form :model="payForm" label-width="120px" style="max-width: 600px; margin: 20px 0;">
        <!-- Section 1: Query Client -->
        <el-form-item label="客户 ID" required>
          <div class="query-input-group">
            <el-input
              v-model="payForm.clientId"
              placeholder="请输入客户业务 ID"
              @keyup.enter="lookupClient"
            />
            <el-button
              type="primary"
              :loading="looking"
              @click="lookupClient"
            >
              查询
            </el-button>
          </div>
        </el-form-item>

        <!-- Client Info Details -->
        <el-form-item label="客户公司名称">
          <el-input :value="corpname" placeholder="请输入客户ID并点击查询" readonly disabled />
        </el-form-item>

        <el-form-item label="当前账户余额">
          <div class="balance-display">
            <el-tag v-if="clientVerified" type="success" size="large" effect="dark">
              {{ currentMoneyDisplay }}
            </el-tag>
            <span v-else class="placeholder-text">请先完成客户信息查询</span>
          </div>
        </el-form-item>

        <!-- Section 2: Recharge Amount -->
        <el-form-item label="充值金额 (厘)" required>
          <el-input-number
            v-model="payForm.amount"
            :min="1"
            style="width: 100%"
            placeholder="1元 = 1000厘，请输入正整数"
            :disabled="!clientVerified"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="success"
            :loading="submitting"
            :disabled="!clientVerified"
            @click="submitPay"
          >
            确认充值
          </el-button>
          <el-button @click="goBack">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClientBusinessInfo, payClient } from '@/api/client'

const route = useRoute()
const router = useRouter()

const payForm = ref({
  clientId: '',
  amount: 0
})

const corpname = ref('')
const currentMoney = ref<number | null>(null)
const clientVerified = ref(false)
const looking = ref(false)
const submitting = ref(false)

const currentMoneyDisplay = computed(() => {
  if (!clientVerified.value) {
    return '请先查询客户'
  }
  if (currentMoney.value === null || currentMoney.value === undefined) {
    return '加载中...'
  }
  return `${currentMoney.value / 1000.0} 元（${currentMoney.value} 厘）`
})

async function lookupClient() {
  const id = String(payForm.value.clientId).trim()
  if (!id) {
    ElMessage.warning('请输入客户业务 ID')
    return
  }
  if (!/^\d+$/.test(id)) {
    ElMessage.warning('客户 ID 必须为数字')
    return
  }

  looking.value = true
  clientVerified.value = false
  corpname.value = ''
  currentMoney.value = null

  try {
    const res: any = await getClientBusinessInfo(id)
    if (res && res.code === 0 && res.data) {
      const info = res.data.clientbusiness || res.data
      if (!info.corpname && !info.id) {
        ElMessage.error('该客户 ID 不存在，请核实后重试')
        return
      }
      corpname.value = info.corpname || ''
      currentMoney.value = info.money != null ? Number(info.money) : 0
      clientVerified.value = true
    } else {
      ElMessage.error((res.msg || '客户不存在') + '，请核实后重试')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '查询失败，请检查网络后重试')
  } finally {
    looking.value = false
  }
}

function submitPay() {
  if (!clientVerified.value) {
    ElMessage.warning('请先查询并确认客户信息')
    return
  }
  const val = payForm.value.amount
  if (!val || val <= 0) {
    ElMessage.warning('充值金额必须大于 0')
    return
  }

  ElMessageBox.confirm(
    `确认为【${corpname.value}】充值 ${val} 厘（${val / 1000.0} 元）？`,
    '充值确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }
  ).then(async () => {
    submitting.value = true
    try {
      const res: any = await payClient({
        jine: val,
        clientId: payForm.value.clientId
      })
      if (res && res.code === 0) {
        const data = res.data || {}
        const newBalance = data.balance != null ? Number(data.balance) : ((currentMoney.value || 0) + val)
        currentMoney.value = newBalance
        payForm.value.amount = 0
        ElMessageBox.alert(`充值成功！当前最新余额为：${newBalance / 1000.0} 元`, '充值结果', {
          confirmButtonText: '确定'
        })
      } else {
        ElMessage.error(res.msg || '充值失败')
      }
    } catch (error: any) {
      ElMessage.error(error.message || '充值失败，网络请求异常')
    } finally {
      submitting.value = false
    }
  }).catch(() => {})
}

function goBack() {
  router.push('/client/clientbusiness')
}

onMounted(() => {
  const qId = route.query.clientId
  if (qId) {
    payForm.value.clientId = String(qId)
    lookupClient()
  }
})
</script>

<style scoped>
.userpay-container {
  padding: 10px 0;
}
.card-header {
  display: flex;
  flex-direction: column;
}
.card-header .title {
  font-size: 18px;
  font-weight: 600;
}
.card-header .subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
.query-input-group {
  display: flex;
  width: 100%;
  gap: 12px;
}
.balance-display {
  display: flex;
  align-items: center;
}
.placeholder-text {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
