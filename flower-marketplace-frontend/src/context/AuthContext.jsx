import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/auth'
import { users } from '../api/users'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser]       = useState(null)   // UserDto từ backend
  const [token, setToken]     = useState(() => localStorage.getItem('accessToken'))
  const [loading, setLoading] = useState(true)

  // Khi app load: nếu có token → lấy profile
  useEffect(() => {
    if (token) {
      users.getMe()
        .then(res => setUser(res.data))
        .catch(() => { localStorage.clear(); setToken(null) })
        .finally(() => setLoading(false))
    } else {
      setLoading(false)
    }
  }, [token])

  const login = useCallback(async (email, password) => {
    const res = await apiLogin(email, password)
    const { accessToken, refreshToken, ...userData } = res.data
    localStorage.setItem('accessToken',  accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    setToken(accessToken)
    setUser(userData)
    return userData
  }, [])

  const register = useCallback(async (data) => {
    const res = await apiRegister(data)
    const { accessToken, refreshToken, ...userData } = res.data
    localStorage.setItem('accessToken',  accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    setToken(accessToken)
    setUser(userData)
    return userData
  }, [])

  const logout = useCallback(async () => {
    try { await apiLogout() } catch (_) {}
    localStorage.clear()
    setToken(null)
    setUser(null)
  }, [])

  const isAdmin  = user?.role === 'ROLE_ADMIN'
  const isSeller = user?.role === 'ROLE_SELLER' || isAdmin
  const isAuth   = !!token

  return (
    <AuthContext.Provider value={{ user, token, loading, isAuth, isAdmin, isSeller, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}
