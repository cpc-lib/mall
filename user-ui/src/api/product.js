import request from '@/utils/request'

export default {
  page: (params) => request.get('/api/product/page', { params })
}
