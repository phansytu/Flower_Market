import api from './axios'

export const chat = {
  // GET /api/chat/conversations
  getConversations: () => api.get('/chat/conversations').then(r => r.data),

  // GET /api/chat/conversations/:id/messages?page=0&size=30
  getMessages: (convId, params) =>
    api.get(`/chat/conversations/${convId}/messages`, { params }).then(r => r.data),

  // POST /api/chat/send  (REST fallback nếu WS chưa kết nối)
  sendMessage: (data) => api.post('/chat/send', data).then(r => r.data),
}
