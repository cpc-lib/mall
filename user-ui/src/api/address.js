import request from '@/utils/request'

export default {
  list: () => request.get('/api/user/address/list'),
  getDefault: () => request.get('/api/user/address/default'),
  create: (data) => request.post('/api/user/address', data),
  update: (id, data) => request.put(`/api/user/address/${id}`, data),
  remove: (id) => request.delete(`/api/user/address/${id}`),
  setDefault: (id) => request.put(`/api/user/address/${id}/default`)
}
