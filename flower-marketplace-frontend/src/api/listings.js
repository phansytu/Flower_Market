import api from './axios'

// GET /api/listings/public?page=0&size=20&category=...&sort=createdAt,desc
export const getListings = (params) =>
  api.get('/listings/public', { params }).then(r => r.data)

// GET /api/listings/search?keyword=roses&...
export const searchListings = (params) =>
  api.get('/listings/search', { params }).then(r => r.data)

// GET /api/listings/public/:id
export const getListingById = (id) =>
  api.get(`/listings/public/${id}`).then(r => r.data)

// GET /api/listings/my   (cần auth)
export const getMyListings = (params) =>
  api.get('/listings/my', { params }).then(r => r.data)

// POST /api/listings  (multipart)
export const createListing = (formData) =>
  api.post('/listings', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then(r => r.data)

// PUT /api/listings/:id
export const updateListing = (id, formData) =>
  api.put(`/listings/${id}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then(r => r.data)

// DELETE /api/listings/:id
export const deleteListing = (id) =>
  api.delete(`/listings/${id}`).then(r => r.data)

// PATCH /api/listings/:id/status
export const updateListingStatus = (id, status) =>
  api.patch(`/listings/${id}/status`, { status }).then(r => r.data)
