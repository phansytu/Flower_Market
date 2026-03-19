import api from './axios'

export const reviews = {
  // GET /api/reviews/listing/:id?page=0&size=10&sort=newest
  getByListing: (listingId, params) =>
    api.get(`/reviews/listing/${listingId}`, { params }).then(r => r.data),

  // GET /api/reviews/listing/:id/stats
  getListingStats: (listingId) =>
    api.get(`/reviews/listing/${listingId}/stats`).then(r => r.data),

  // GET /api/reviews/seller/:id/stats
  getSellerStats: (sellerId) =>
    api.get(`/reviews/seller/${sellerId}/stats`).then(r => r.data),

  // POST /api/reviews
  create: (data) => api.post('/reviews', data).then(r => r.data),

  // POST /api/reviews/:id/reply
  reply: (id, reply) => api.post(`/reviews/${id}/reply`, { reply }).then(r => r.data),
}
