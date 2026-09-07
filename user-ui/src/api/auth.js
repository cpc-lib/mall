import request from '@/utils/request'
export default {
  register: (data) => request.post('/api/auth/register', data),
  login: (data) => request.post('/api/auth/login', data),
  logout: (refreshToken) => request.post('/api/auth/logout', { refreshToken }),
  changePassword: (data) => request.post('/api/auth/password', data),
  adminResetPassword: (data) => request.post('/api/auth/admin/reset-password', data),
  adminUsers: (params) => request.get('/api/admin/users', { params }),
  adminUserDetail: (id) => request.get(`/api/admin/users/${id}`),
  setUserStatus: (id, userStatus) => request.put(`/api/admin/users/${id}/status`, { userStatus }),
  passwordResetRequests: () => request.get('/api/admin/password-reset-requests'),
  handlePasswordResetRequest: (id, adminRemark) => request.post(`/api/admin/password-reset-requests/${id}/handle`, { adminRemark }),
  rejectPasswordResetRequest: (id, adminRemark) => request.post(`/api/admin/password-reset-requests/${id}/reject`, { adminRemark }),
  submitPasswordResetRequest: (data) => request.post('/api/auth/password-reset-request', data)
}
