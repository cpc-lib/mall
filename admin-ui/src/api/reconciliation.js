import request from '@/utils/request'

/**
 * 上传微信交易账单 XLSX 文件并自动对账（multipart/form-data）。
 * 同文件重复上传幂等返回原批次；同一账单日期重复上传会被后端拒绝。
 * @param {FormData} formData 包含 file / billDate / billType(可选，默认 tradebill)
 */
export function uploadBill(formData) {
  return request({
    url: '/api/reconciliation/bill/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 导入批次列表，billDate 可选过滤
 */
export function listImports(params) {
  return request({
    url: '/api/reconciliation/imports',
    method: 'get',
    params
  })
}

/**
 * 导入批次详情
 */
export function getImport(importNo) {
  return request({
    url: `/api/reconciliation/imports/${importNo}`,
    method: 'get'
  })
}

/**
 * 账账核对汇总：渠道账 vs 平台账的笔数、金额、净额与平账状态
 */
export function getReconcileSummary(importNo) {
  return request({
    url: `/api/reconciliation/imports/${importNo}/summary`,
    method: 'get'
  })
}

/**
 * 批次账单流水，recordType 可选 PAY/REFUND
 */
export function listRecords(importNo, params) {
  return request({
    url: `/api/reconciliation/imports/${importNo}/records`,
    method: 'get',
    params
  })
}

/**
 * 批次对账差异单，bizType/discrepancyType/status 可选过滤
 */
export function listDiscrepancies(importNo, params) {
  return request({
    url: `/api/reconciliation/imports/${importNo}/discrepancies`,
    method: 'get',
    params
  })
}

/**
 * 单条差异数据下钻：渠道原始账、平台账本候选记录与字段核验结果
 */
export function getDiscrepancyDrilldown(id) {
  return request({
    url: `/api/reconciliation/discrepancies/${id}/drilldown`,
    method: 'get'
  })
}

/**
 * 对已导入批次重新触发对账；已对账批次幂等返回现有结果
 */
export function reconcileImport(importNo) {
  return request({
    url: `/api/reconciliation/imports/${importNo}/reconcile`,
    method: 'post'
  })
}

/**
 * 标记差异单已处理
 */
export function resolveDiscrepancy(id, resolveRemark) {
  return request({
    url: `/api/reconciliation/discrepancies/${id}/resolve`,
    method: 'post',
    data: { resolveRemark }
  })
}
