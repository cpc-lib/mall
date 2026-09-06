import request from '../utils/request'

export default {
  // 上传微信交易账单文件并自动对账（multipart/form-data），formData 含 file/billDate/billType
  uploadBill(formData) {
    return request({
      url: '/api/reconciliation/bill/upload',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  // 导入批次列表，params.billDate 可选
  listImports: params => request.get('/api/reconciliation/imports', { params }),
  getImport: importNo => request.get(`/api/reconciliation/imports/${importNo}`),
  // 批次账单流水，params.recordType 可选 PAY/REFUND
  listRecords: (importNo, params) => request.get(`/api/reconciliation/imports/${importNo}/records`, { params }),
  // 批次对账差异，params.bizType/discrepancyType/status 可选
  listDiscrepancies: (importNo, params) => request.get(`/api/reconciliation/imports/${importNo}/discrepancies`, { params }),
  // 重新触发对账；已对账批次幂等返回现有结果
  reconcile: importNo => request.post(`/api/reconciliation/imports/${importNo}/reconcile`),
  // 标记差异已处理，data: { resolveRemark }
  resolveDiscrepancy: (id, data) => request.post(`/api/reconciliation/discrepancies/${id}/resolve`, data)
}
