import request from '@/utils/request'

export default {
  listEnabledApps() {
    return request({
      url: '/api/payment-app/list',
      method: 'get'
    })
  },

  listAllApps() {
    return request({
      url: '/api/payment-app/list-all',
      method: 'get'
    })
  },

  createApp(data) {
    return request({
      url: '/api/payment-app',
      method: 'post',
      data
    })
  },

  updateApp(id, data) {
    return request({
      url: `/api/payment-app/${id}`,
      method: 'put',
      data
    })
  },

  updateAppStatus(id, status) {
    return request({
      url: `/api/payment-app/${id}/status`,
      method: 'patch',
      data: { status }
    })
  },

  deleteApp(id) {
    return request({
      url: `/api/payment-app/${id}`,
      method: 'delete'
    })
  },

  listEnabledChannels() {
    return request({
      url: '/api/payment-channel/list',
      method: 'get'
    })
  },

  listAllChannels() {
    return request({
      url: '/api/payment-channel/list-all',
      method: 'get'
    })
  },

  createChannel(data) {
    return request({
      url: '/api/payment-channel',
      method: 'post',
      data
    })
  },

  updateChannel(id, data) {
    return request({
      url: `/api/payment-channel/${id}`,
      method: 'put',
      data
    })
  },

  updateChannelStatus(id, status) {
    return request({
      url: `/api/payment-channel/${id}/status`,
      method: 'patch',
      data: { status }
    })
  },

  deleteChannel(id) {
    return request({
      url: `/api/payment-channel/${id}`,
      method: 'delete'
    })
  },

  reload() {
    return request({
      url: '/api/payment-config/reload',
      method: 'post'
    })
  }
}
