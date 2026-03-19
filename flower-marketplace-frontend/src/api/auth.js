import api from './axios'

// POST /api/auth/register
export const register = (data) =>
  api.post('/auth/register', data).then(r => r.data)

// POST /api/auth/login
export const login = (email, password) =>
  api.post('/auth/login', { email, password }).then(r => r.data)

// POST /api/auth/refresh
export const refreshToken = (token) =>
  api.post('/auth/refresh', { refreshToken: token }).then(r => r.data)

// POST /api/auth/logout
export const logout = () =>
  api.post('/auth/logout').then(r => r.data)
