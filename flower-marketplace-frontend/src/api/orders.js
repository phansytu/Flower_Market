// ──────────────────────────────────────────────────────────────────────────────
// orders.js
// ──────────────────────────────────────────────────────────────────────────────
import api from './axios'

export const orders = {
  // POST /api/orders
  create: (data) => api.post('/orders', data).then(r => r.data),

  // GET /api/orders/my?page=0&size=10
  getMy: (params) => api.get('/orders/my', { params }).then(r => r.data),

  // GET /api/orders/:id
  getById: (id) => api.get(`/orders/${id}`).then(r => r.data),

  // PATCH /api/orders/:id/cancel
  cancel: (id) => api.patch(`/orders/${id}/cancel`).then(r => r.data),

  // PATCH /api/orders/:id/status  (seller/admin)
  updateStatus: (id, status) =>
    api.patch(`/orders/${id}/status`, { status }).then(r => r.data),
}
