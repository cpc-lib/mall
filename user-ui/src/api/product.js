import request from '@/utils/request'

export default {
  list() {
    return request({
      url: '/api/product/list',
      method: 'get'
    })
  },
  page: (params) => request.get('/api/product/page', { params })
}
