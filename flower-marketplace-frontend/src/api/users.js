import api from './axios'

export const users = {
  // GET /api/users/me
  getMe: () => api.get('/users/me').then(r => r.data),

  // PUT /api/users/me
  updateProfile: (data) => api.put('/users/me', data).then(r => r.data),

  // GET /api/users/:id
  getById: (id) => api.get(`/users/${id}`).then(r => r.data),

  // POST /api/users/me/addresses
  addAddress: (data) => api.post('/users/me/addresses', data).then(r => r.data),

  // DELETE /api/users/me/addresses/:id
  deleteAddress: (id) => api.delete(`/users/me/addresses/${id}`).then(r => r.data),
}
