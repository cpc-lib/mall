import request from '@/utils/request'

export default {
  page: (params) => request.get('/api/product/page', { params }),
  spuPage: (params) => request.get('/api/product/spu/page', { params }),
  spuDetail: (spuId) => request.get(`/api/product/spu/${spuId}`)
}
