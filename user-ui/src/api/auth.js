import request from '@/utils/request'
export default {
  register: (data) => request.post('/api/auth/register', data),
  login: (data) => request.post('/api/auth/login', data),
  logout: (refreshToken) => request.post('/api/auth/logout', { refreshToken }),
  changePassword: (data) => request.post('/api/auth/password', data),
  submitPasswordResetRequest: (data) => request.post('/api/auth/password-reset-request', data)
}
