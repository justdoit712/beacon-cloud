import request from '@/utils/request'

// 获取短信记录列表
export function getSmsRecordList(params: any) {
  const { limit, offset, ...rest } = params
  return request.get('/sys/search/list', {
    params: {
      size: limit,
      from: offset,
      ...rest
    }
  })
}
