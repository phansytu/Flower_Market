import api from './axios'

export const admin = {
  // GET /api/admin/dashboard
  getDashboard: () => api.get('/admin/dashboard').then(r => r.data),

  // GET /api/admin/users?page=0&size=20
  getUsers: (params) => api.get('/admin/users', { params }).then(r => r.data),

  // GET /api/admin/users/:id
  getUser: (id) => api.get(`/admin/users/${id}`).then(r => r.data),

  // PATCH /api/admin/users/:id/role
  changeRole: (id, role) =>
    api.patch(`/admin/users/${id}/role`, { role }).then(r => r.data),

  // PATCH /api/admin/users/:id/toggle-enabled
  toggleUser: (id) =>
    api.patch(`/admin/users/${id}/toggle-enabled`).then(r => r.data),

  // POST /api/admin/users/:id/ban
  banUser: (id, data) =>
    api.post(`/admin/users/${id}/ban`, data).then(r => r.data),

  // DELETE /api/admin/users/:id/ban
  unbanUser: (id) =>
    api.delete(`/admin/users/${id}/ban`).then(r => r.data),

  // GET /api/admin/listings?page=0&size=20&status=PENDING
  getListings: (params) =>
    api.get('/admin/listings', { params }).then(r => r.data),

  // PATCH /api/admin/listings/:id/status
  updateListingStatus: (id, status) =>
    api.patch(`/admin/listings/${id}/status`, { status }).then(r => r.data),

  // GET /api/admin/orders?page=0&size=20
  getOrders: (params) => api.get('/admin/orders', { params }).then(r => r.data),

  // GET /api/admin/payments?page=0&size=20
  getPayments: (params) =>
    api.get('/admin/payments', { params }).then(r => r.data),

  // GET /api/admin/audit-logs?page=0&size=20
  getAuditLogs: (params) =>
    api.get('/admin/audit-logs', { params }).then(r => r.data),

  // GET /api/admin/configs
  getConfigs: () => api.get('/admin/configs').then(r => r.data),

  // PUT /api/admin/configs/:key
  upsertConfig: (key, data) =>
    api.put(`/admin/configs/${key}`, data).then(r => r.data),
}
