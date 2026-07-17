<template>
  <div class="smssend-container">
    <!-- Send Workbench Card -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span class="title">发送工作台</span>
          <span class="subtitle">先选择发送客户与短信类型，再粘贴目标手机号与内容，提交后查看下方结果摘要。</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="smsForm"
        :rules="formRules"
        label-width="120px"
        style="max-width: 800px; margin: 20px 0;"
      >
        <el-form-item label="所属客户" prop="clientId">
          <el-select
            v-model="smsForm.clientId"
            placeholder="请选择所属客户"
            style="width: 100%"
          >
            <el-option
              v-for="site in clientOptions"
              :key="site.id"
              :label="site.corpname"
              :value="site.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="短信类型" prop="state">
          <el-select v-model="smsForm.state" placeholder="请选择短信类型" style="width: 100%">
            <el-option :value="0" label="验证码" />
            <el-option :value="1" label="通知" />
            <el-option :value="2" label="营销" />
          </el-select>
        </el-form-item>

        <el-form-item label="手机号" prop="mobile">
          <el-input
            v-model="smsForm.mobile"
            type="textarea"
            :rows="8"
            placeholder="每行输入一个手机号，支持逗号、分号或空格分隔"
          />
          <div class="help-text">
            当前识别到 <strong class="highlight">{{ mobileCount }}</strong> 个目标手机号，系统会自动去重。
          </div>
        </el-form-item>

        <el-form-item label="短信内容" prop="content">
          <el-input
            v-model="smsForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入短信内容"
          />
          <div class="help-text">
            内容长度 <strong class="highlight">{{ contentLength }}</strong> 字。
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="sending" @click="handleSend">
            {{ sending ? '发送中' : '发送短信' }}
          </el-button>
          <el-button @click="resetForm">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Feedback Summary Card -->
    <el-card class="box-card feedback-card">
      <template #header>
        <div class="card-header">
          <span class="title">发送结果反馈</span>
          <span class="subtitle">展示最近一次批量发送的反馈结果。</span>
        </div>
      </template>

      <div class="summary-strip">
        <div class="summary-item">
          <span class="summary-label">目标数量</span>
          <strong class="summary-value">{{ resultSummary.total }}</strong>
        </div>
        <div class="summary-item success">
          <span class="summary-label">成功数量</span>
          <strong class="summary-value">{{ resultSummary.success }}</strong>
        </div>
        <div class="summary-item failed">
          <span class="summary-label">失败数量</span>
          <strong class="summary-value">{{ resultSummary.failed }}</strong>
        </div>
      </div>

      <div class="feedback-banner" :class="{ 'is-empty': !resultSummary.message }">
        <p class="feedback-text">{{ resultSummary.message || '短信发送后，将在此处渲染接口日志反馈。' }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getClientBusinessAll } from '@/api/client'
import { sendSms } from '@/api/sms'

const formRef = ref<FormInstance>()
const clientOptions = ref<any[]>([])
const sending = ref(false)

const smsForm = ref({
  clientId: '',
  state: 0,
  mobile: '',
  content: ''
})

const resultSummary = ref({
  total: 0,
  success: 0,
  failed: 0,
  message: ''
})

const formRules = {
  clientId: [{ required: true, message: '请选择所属客户', trigger: 'change' }],
  state: [{ required: true, message: '请选择短信类型', trigger: 'change' }],
  mobile: [{ required: true, message: '请输入目标手机号', trigger: 'blur' }],
  content: [{ required: true, message: '请输入短信内容', trigger: 'blur' }]
}

// 统计识别的手机号码个数（去重及非空过滤）
const mobileCount = computed(() => {
  const text = smsForm.value.mobile.trim()
  if (!text) return 0
  const arr = text.split(/[\n,;\s]+/)
  const unique = new Set(arr.filter(val => val.trim().length > 0))
  return unique.size
})

// 短信字数统计
const contentLength = computed(() => {
  return smsForm.value.content.length
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

function handleSend() {
  formRef.value?.validate(async (valid) => {
    if (valid) {
      sending.value = true
      resultSummary.value = { total: 0, success: 0, failed: 0, message: '' }
      try {
        const res: any = await sendSms(smsForm.value)
        if (res && res.code === 0) {
          ElMessage.success('发送请求处理完成')
          const summary = res.data || {}
          resultSummary.value = {
            total: summary.total || 0,
            success: summary.success || 0,
            failed: summary.failed || 0,
            message: summary.message || res.msg || '投递处理成功'
          }
        } else {
          ElMessage.error(res.msg || '短信发送失败')
          resultSummary.value.message = res.msg || '短信投递异常'
        }
      } catch (error: any) {
        ElMessage.error(error.message || '网络投递请求异常')
        resultSummary.value.message = error.message || '接口交互异常'
      } finally {
        sending.value = false
      }
    }
  })
}

function resetForm() {
  smsForm.value = {
    clientId: '',
    state: 0,
    mobile: '',
    content: ''
  }
  resultSummary.value = {
    total: 0,
    success: 0,
    failed: 0,
    message: ''
  }
}

onMounted(() => {
  loadClients()
})
</script>

<style scoped>
.smssend-container {
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
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
.help-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
.highlight {
  color: var(--el-color-primary);
  font-weight: bold;
  font-size: 14px;
}

/* Feedback Summary styling */
.summary-strip {
  display: flex;
  gap: 30px;
  margin: 15px 0;
}
.summary-item {
  display: flex;
  flex-direction: column;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 15px 30px;
  min-width: 140px;
}
.summary-item.success {
  background-color: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-8);
}
.summary-item.failed {
  background-color: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-8);
}
.summary-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.summary-value {
  font-size: 26px;
  font-weight: bold;
  margin-top: 6px;
}
.summary-item.success .summary-value {
  color: var(--el-color-success);
}
.summary-item.failed .summary-value {
  color: var(--el-color-danger);
}
.feedback-banner {
  background-color: var(--el-fill-color-darker);
  border-radius: 6px;
  padding: 15px;
  margin-top: 15px;
}
.feedback-banner.is-empty {
  background-color: var(--el-fill-color-light);
}
.feedback-text {
  font-family: monospace;
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--el-text-color-primary);
  margin: 0;
}
.feedback-banner.is-empty .feedback-text {
  color: var(--el-text-color-placeholder);
}
</style>
