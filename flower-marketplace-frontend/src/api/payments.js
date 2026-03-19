import api from './axios'

export const payments = {
  // POST /api/payments
  process: (data) => api.post('/payments', data).then(r => r.data),

  // GET /api/payments/my
  getMy: (params) => api.get('/payments/my', { params }).then(r => r.data),

  // GET /api/payments/order/:orderId
  getByOrder: (orderId) =>
    api.get(`/payments/order/${orderId}`).then(r => r.data),
}
