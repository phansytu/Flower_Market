import api from './axios'

export const notifications = {
  // GET /api/notifications?page=0&size=20
  getAll: (params) => api.get('/notifications', { params }).then(r => r.data),

  // GET /api/notifications/unread/count
  getUnreadCount: () =>
    api.get('/notifications/unread/count').then(r => r.data),

  // PATCH /api/notifications/read-all
  markAllRead: () => api.patch('/notifications/read-all').then(r => r.data),

  // PATCH /api/notifications/:id/read
  markOneRead: (id) =>
    api.patch(`/notifications/${id}/read`).then(r => r.data),
}
