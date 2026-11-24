import request from '@/utils/request'

export default {
  listEnabledApps() {
    return request({
      url: '/api/payment-app/list',
      method: 'get'
    })
  }
}
