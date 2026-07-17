import request from '@/utils/request'

// 发送短信
export function sendSms(data: { clientId: number | string; mobile: string; content: string; state: number }) {
  return request.post('/sys/sms/save', data)
}
