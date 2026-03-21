/**
 * FlowerMarket.jsx  — Single-file React App (tích hợp Spring Boot)
 *
 * Mỗi page giao tiếp trực tiếp với backend qua hooks/useApi.js:
 *   HomePage        → GET /api/listings/public  (featured + nearby)
 *   CategoryPage    → GET /api/listings/public  (filter + search)
 *   ProductDetail   → GET /api/listings/public/:id, GET /api/reviews/listing/:id/stats
 *   CreateListing   → POST /api/listings  (multipart)
 *   ChatPage        → GET /api/chat/conversations + WS STOMP /app/chat.send
 *   OrderPage       → POST /api/orders, POST /api/payments
 *   UserProfile     → GET /api/users/me, GET /api/listings/my, GET /api/orders/my
 *   SellerDashboard → GET /api/listings/my, GET /api/orders (seller view)
 *   AdminDashboard  → GET /api/admin/dashboard, /users, /listings, /orders
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { QueryClient, QueryClientProvider, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import toast, { Toaster } from 'react-hot-toast'
import axios from 'axios'
import { formatDistanceToNow } from 'date-fns'
import { vi } from 'date-fns/locale'

// ─── DESIGN TOKENS ──────────────────────────────────────────────────────────
const C = {
  bg: '#FDFAF6', dark: '#2D2A26', accent: '#C8553D', green: '#5B8A5F',
  muted: '#9B9188', border: '#D9CFC4', surface: '#EDE8E1', surfaceDk: '#D4CCC2',
  tag: '#F4EDE4', highlight: '#FFF3EC', amber: '#8B5E3C',
}

// ─── AXIOS INSTANCE ─────────────────────────────────────────────────────────
const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use(cfg => {
  const t = localStorage.getItem('accessToken')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

let refreshing = false, failQ = []
const processQ = (err, tok) => { failQ.forEach(p => err ? p.reject(err) : p.resolve(tok)); failQ = [] }

api.interceptors.response.use(r => r, async err => {
  const orig = err.config
  if (err.response?.status === 401 && !orig._retry) {
    if (refreshing) return new Promise((res, rej) => failQ.push({ resolve: res, reject: rej }))
      .then(tok => { orig.headers.Authorization = `Bearer ${tok}`; return api(orig) })
    orig._retry = true; refreshing = true
    const rt = localStorage.getItem('refreshToken')
    if (!rt) { localStorage.clear(); window.location.reload(); return Promise.reject(err) }
    try {
      const { data } = await axios.post('/api/auth/refresh', { refreshToken: rt })
      const at = data.data.accessToken
      localStorage.setItem('accessToken', at)
      processQ(null, at); orig.headers.Authorization = `Bearer ${at}`
      return api(orig)
    } catch (e) { processQ(e, null); localStorage.clear(); window.location.reload(); return Promise.reject(e) }
    finally { refreshing = false }
  }
  return Promise.reject(err)
})

// ─── API CALLS ───────────────────────────────────────────────────────────────
const Auth = {
  login:    (email, password) => api.post('/auth/login', { email, password }),
  register: (d) => api.post('/auth/register', d),
  logout:   () => api.post('/auth/logout'),
}
const Listings = {
  getPublic:  (p) => api.get('/listings/public', { params: p }),
  search:     (p) => api.get('/listings/search',  { params: p }),
  getById:    (id) => api.get(`/listings/public/${id}`),
  getMy:      (p)  => api.get('/listings/my', { params: p }),
  create:     (fd) => api.post('/listings', fd, { headers: { 'Content-Type': 'multipart/form-data' } }),
  update:     (id, fd) => api.put(`/listings/${id}`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }),
  remove:     (id) => api.delete(`/listings/${id}`),
}
const Orders = {
  create:  (d) => api.post('/orders', d),
  getMy:   (p) => api.get('/orders/my', { params: p }),
  getById: (id) => api.get(`/orders/${id}`),
  cancel:  (id) => api.patch(`/orders/${id}/cancel`),
  updateStatus: (id, status) => api.patch(`/orders/${id}/status`, { status }),
}
const Payments = {
  process:     (d)  => api.post('/payments', d),
  getByOrder:  (id) => api.get(`/payments/order/${id}`),
}
const Reviews = {
  getByListing: (id, p) => api.get(`/reviews/listing/${id}`, { params: p }),
  getStats:     (id)    => api.get(`/reviews/listing/${id}/stats`),
  create:       (d)     => api.post('/reviews', d),
  reply:        (id, r) => api.post(`/reviews/${id}/reply`, { reply: r }),
}
const Users = {
  getMe:    () => api.get('/users/me'),
  update:   (d) => api.put('/users/me', d),
  getById:  (id) => api.get(`/users/${id}`),
  addAddr:  (d) => api.post('/users/me/addresses', d),
}
const Chat = {
  getConversations: () => api.get('/chat/conversations'),
  getMessages: (cid, p) => api.get(`/chat/conversations/${cid}/messages`, { params: p }),
}
const Notif = {
  getAll:         (p) => api.get('/notifications', { params: p }),
  getUnreadCount: () => api.get('/notifications/unread/count'),
  markAllRead:    () => api.patch('/notifications/read-all'),
}
const Admin = {
  getDashboard:   () => api.get('/admin/dashboard'),
  getUsers:       (p) => api.get('/admin/users', { params: p }),
  toggleUser:     (id) => api.patch(`/admin/users/${id}/toggle-enabled`),
  banUser:        (id, d) => api.post(`/admin/users/${id}/ban`, d),
  getListings:    (p) => api.get('/admin/listings', { params: p }),
  updateListing:  (id, s) => api.patch(`/admin/listings/${id}/status`, { status: s }),
  getOrders:      (p) => api.get('/admin/orders', { params: p }),
}

// ─── REACT QUERY CLIENT ──────────────────────────────────────────────────────
const qc = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30000 } }
})

// ─── AUTH STATE (simple module-level, no context needed in single-file) ───────
let _authUser = null, _authListeners = []
const authStore = {
  get: () => _authUser,
  set: (u) => { _authUser = u; _authListeners.forEach(f => f(u)) },
  subscribe: (f) => { _authListeners.push(f); return () => { _authListeners = _authListeners.filter(x => x !== f) } },
}

// Init from localStorage
;(async () => {
  const t = localStorage.getItem('accessToken')
  if (t) {
    try { const r = await api.get('/users/me'); authStore.set(r.data.data) } catch (_) {}
  }
})()

function useAuthUser() {
  const [user, setUser] = useState(authStore.get)
  useEffect(() => authStore.subscribe(setUser), [])
  return user
}

// ─── CSS ─────────────────────────────────────────────────────────────────────
const CSS = `
  @import url('https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;600;700;900&family=DM+Sans:wght@300;400;500;600&display=swap');
  *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
  body{font-family:'DM Sans',sans-serif;background:${C.bg};color:${C.dark};min-height:100vh}
  h1,h2,h3,h4{font-family:'Playfair Display',serif}
  .serif{font-family:'Playfair Display',serif}
  ::-webkit-scrollbar{width:5px;height:5px}
  ::-webkit-scrollbar-track{background:${C.surface}}
  ::-webkit-scrollbar-thumb{background:${C.surfaceDk};border-radius:3px}

  /* Tabs */
  .tab-nav{background:${C.dark};display:flex;overflow-x:auto;border-bottom:2px solid ${C.accent};position:sticky;top:0;z-index:200}
  .tab-nav::-webkit-scrollbar{height:0}
  .tab-btn{padding:11px 17px;cursor:pointer;white-space:nowrap;font-size:12px;font-weight:500;color:#777;border-bottom:3px solid transparent;transition:all .15s;background:transparent;border-top:none;border-left:none;border-right:none;font-family:'DM Sans',sans-serif}
  .tab-btn:hover{color:#bbb}
  .tab-btn.active{color:${C.accent};font-weight:700;border-bottom-color:${C.accent};background:rgba(200,85,61,.08)}

  /* Nav */
  .navbar{background:${C.dark};padding:12px 28px;display:flex;align-items:center;gap:14px}
  .nav-logo{font-family:'Playfair Display',serif;color:#fff;font-size:18px;font-weight:700;white-space:nowrap;cursor:pointer}
  .nav-search{flex:1;height:36px;background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.15);border-radius:6px;display:flex;align-items:center;padding:0 12px;gap:8px;cursor:pointer;transition:background .15s;max-width:460px}
  .nav-search:hover{background:rgba(255,255,255,.15)}
  .nav-search input{background:transparent;border:none;outline:none;flex:1;color:#ddd;font-size:12px;font-family:'DM Sans',sans-serif}
  .nav-search input::placeholder{color:#777}

  /* Buttons */
  .btn{display:inline-flex;align-items:center;gap:6px;padding:8px 18px;border-radius:6px;font-size:13px;font-weight:600;cursor:pointer;transition:all .15s;border:none;font-family:'DM Sans',sans-serif;white-space:nowrap}
  .btn:disabled{opacity:.5;cursor:not-allowed}
  .btn-primary{background:${C.accent};color:#fff}
  .btn-primary:hover:not(:disabled){background:#b4472f;transform:translateY(-1px);box-shadow:0 4px 12px rgba(200,85,61,.3)}
  .btn-secondary{background:${C.surface};color:${C.dark};border:1.5px solid ${C.border}}
  .btn-secondary:hover:not(:disabled){background:${C.surfaceDk}}
  .btn-ghost{background:transparent;color:${C.dark};border:1.5px solid ${C.border}}
  .btn-ghost:hover:not(:disabled){background:${C.surface}}
  .btn-sm{padding:5px 12px;font-size:11px}
  .btn-green{background:${C.green};color:#fff}
  .btn-green:hover:not(:disabled){background:#4a7a4e}
  .btn-danger{background:#fee2e2;color:#dc2626;border:1px solid #fca5a5}

  /* Cards */
  .card{background:#fff;border:1.5px solid ${C.border};border-radius:10px;overflow:hidden;transition:all .2s}
  .product-card{background:#fff;border:1.5px solid ${C.border};border-radius:10px;overflow:hidden;transition:all .2s;cursor:pointer;flex-shrink:0}
  .product-card:hover{transform:translateY(-4px);box-shadow:0 10px 28px rgba(45,42,38,.12)}
  .product-card .pimg{background:linear-gradient(135deg,${C.surfaceDk},${C.surface});display:flex;align-items:center;justify-content:center;font-size:42px;position:relative}
  .product-card .heart{position:absolute;top:10px;right:10px;background:rgba(255,255,255,.9);border-radius:50%;width:28px;height:28px;display:flex;align-items:center;justify-content:center;font-size:14px;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,.1);transition:transform .15s}
  .product-card .heart:hover{transform:scale(1.2)}
  .product-card .fbadge{position:absolute;top:10px;left:10px;background:${C.green};color:#fff;font-size:9px;font-weight:700;padding:3px 7px;border-radius:10px;text-transform:uppercase}

  /* Input */
  .input{width:100%;padding:9px 13px;border:1.5px solid ${C.border};border-radius:6px;font-size:13px;font-family:'DM Sans',sans-serif;color:${C.dark};background:#fff;outline:none;transition:border-color .15s}
  .input:focus{border-color:${C.accent};box-shadow:0 0 0 3px rgba(200,85,61,.08)}
  .input::placeholder{color:${C.muted}}
  textarea.input{resize:vertical;min-height:90px;padding-top:10px}
  select.input{appearance:none;cursor:pointer}
  .label{font-size:12px;font-weight:600;color:${C.dark};margin-bottom:6px;display:block}

  /* Chips */
  .chip{display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:500;background:${C.tag};color:${C.amber};border:1px solid ${C.border}}
  .chip-green{background:#e8f4e8;color:${C.green};border-color:#c3ddc4}
  .chip-red{background:#fde8e4;color:${C.accent};border-color:#f0c4bc}

  /* Status badges */
  .b-active{background:#d1e7dd;color:#155724;border:1px solid #198754;border-radius:4px;padding:2px 8px;font-size:10px;font-weight:600}
  .b-pending{background:#fff3cd;color:#856404;border:1px solid #ffc107;border-radius:4px;padding:2px 8px;font-size:10px;font-weight:600}
  .b-shipped{background:#cfe2ff;color:#084298;border:1px solid #0d6efd;border-radius:4px;padding:2px 8px;font-size:10px;font-weight:600}
  .b-banned{background:#f8d7da;color:#721c24;border:1px solid #dc3545;border-radius:4px;padding:2px 8px;font-size:10px;font-weight:600}
  .b-cancelled{background:#f0f0f0;color:${C.muted};border:1px solid ${C.border};border-radius:4px;padding:2px 8px;font-size:10px;font-weight:600}

  /* Table */
  .table{width:100%;border-collapse:collapse;font-size:13px}
  .table th{background:${C.dark};color:#fff;padding:9px 14px;text-align:left;font-weight:600;font-size:11px;letter-spacing:.03em}
  .table td{padding:10px 14px;border-bottom:1px solid ${C.border}}
  .table tr:hover td{background:${C.highlight}}
  .table tr:last-child td{border-bottom:none}

  /* Sidebar nav */
  .sidebar-item{padding:7px 12px;border-radius:6px;cursor:pointer;font-size:13px;font-weight:500;display:flex;align-items:center;gap:8px;transition:all .12s;border-left:3px solid transparent;color:${C.muted}}
  .sidebar-item:hover{background:${C.surface};color:${C.dark}}
  .sidebar-item.active{background:${C.highlight};color:${C.accent};font-weight:600;border-left-color:${C.accent}}
  .admin-si{padding:7px 10px;border-radius:5px;cursor:pointer;font-size:12px;color:#999;transition:all .12s}
  .admin-si:hover{background:rgba(255,255,255,.06);color:#ddd}
  .admin-si.active{background:${C.accent};color:#fff;font-weight:600}

  /* Chat */
  .bubble-l{background:${C.surface};border-radius:4px 12px 12px 12px;padding:10px 13px;max-width:75%;font-size:13px;line-height:1.4}
  .bubble-r{background:${C.accent};color:#fff;border-radius:12px 4px 12px 12px;padding:10px 13px;max-width:75%;font-size:13px;margin-left:auto;line-height:1.4}

  /* KPI */
  .kpi{background:#fff;border:1.5px solid ${C.border};border-radius:10px;padding:18px;position:relative;overflow:hidden}

  /* Hero */
  .hero{background:linear-gradient(135deg,${C.surfaceDk} 0%,${C.surface} 50%,${C.highlight} 100%);border-radius:14px;padding:52px 40px;position:relative;overflow:hidden}
  .hero::after{content:'🌸';position:absolute;right:-20px;top:-20px;font-size:160px;opacity:.07;transform:rotate(-15deg)}

  /* Category pill */
  .cat-pill{display:flex;flex-direction:column;align-items:center;gap:5px;padding:12px 14px;background:#fff;border:1.5px solid ${C.border};border-radius:10px;cursor:pointer;transition:all .15s;min-width:74px}
  .cat-pill:hover{border-color:${C.accent};background:${C.highlight};transform:translateY(-2px)}

  /* Filter */
  .filter-box{background:#fff;border:1.5px solid ${C.border};border-radius:10px;padding:18px}
  .filter-sec{margin-bottom:18px;padding-bottom:18px;border-bottom:1px solid ${C.border}}
  .filter-sec:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0}
  .filter-opt{display:flex;align-items:center;gap:8px;padding:4px 0;cursor:pointer;font-size:12px;color:${C.muted}}
  .filter-opt:hover{color:${C.dark}}

  /* Delivery / Payment option */
  .dopt{border:1.5px solid ${C.border};border-radius:8px;padding:12px;text-align:center;cursor:pointer;transition:all .15s}
  .dopt.active{border-color:${C.accent};background:${C.highlight}}
  .popt{border:1.5px solid ${C.border};border-radius:8px;padding:11px 14px;cursor:pointer;transition:all .15s;display:flex;align-items:center;gap:10px}
  .popt.active{border-color:${C.accent};background:${C.highlight}}

  /* Profile tab */
  .ptab{padding:10px 22px;cursor:pointer;font-size:13px;font-weight:600;border-bottom:2.5px solid transparent;color:${C.muted};transition:all .15s}
  .ptab.active{color:${C.accent};border-bottom-color:${C.accent}}

  /* Bar chart */
  .bar{border-radius:4px 4px 0 0;background:${C.accent};transition:height .3s ease;min-width:18px}

  /* Misc */
  .page{padding:28px 32px;max-width:1200px;margin:0 auto}
  .divider{height:1px;background:${C.border};margin:14px 0}
  .stars{color:#f59e0b}
  .notif-dot{width:18px;height:18px;border-radius:50%;background:${C.accent};color:#fff;font-size:9px;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0}
  .upload-box{border:2px dashed ${C.accent};border-radius:8px;background:${C.highlight};width:100px;height:100px;display:flex;flex-direction:column;align-items:center;justify-content:center;cursor:pointer;transition:all .15s;flex-shrink:0}
  .upload-box:hover{background:#ffe8de}
  .img-slot{background:linear-gradient(135deg,${C.surfaceDk},${C.surface});border-radius:8px;width:100px;height:100px;display:flex;align-items:center;justify-content:center;font-size:30px;flex-shrink:0;position:relative}
  .img-slot .rm{position:absolute;top:-7px;right:-7px;background:${C.accent};color:#fff;width:18px;height:18px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:9px;cursor:pointer}
  @keyframes fadeUp{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}
  .fade-up{animation:fadeUp .3s ease forwards}
  @keyframes spin{to{transform:rotate(360deg)}}
  .spinner{width:28px;height:28px;border:3px solid ${C.border};border-top-color:${C.accent};border-radius:50%;animation:spin .7s linear infinite}
`

// ─── SHARED COMPONENTS ───────────────────────────────────────────────────────

const Spinner = ({ size = 28 }) => (
  <div style={{ display:'flex', justifyContent:'center', padding:32 }}>
    <div className="spinner" style={{ width:size, height:size }} />
  </div>
)

const Empty = ({ icon='📭', text='Không có dữ liệu' }) => (
  <div style={{ textAlign:'center', padding:'48px 0', color:C.muted }}>
    <div style={{ fontSize:48, marginBottom:12 }}>{icon}</div>
    <div style={{ fontSize:15 }}>{text}</div>
  </div>
)

const ErrBox = ({ msg }) => (
  <div style={{ background:'#fff5f5', border:'1px solid #fca5a5', borderRadius:8, padding:14, color:'#dc2626', fontSize:13 }}>
    ⚠ {msg || 'Lỗi kết nối server. Hãy chạy backend trước.'}
  </div>
)

const statusBadge = (status) => {
  const map = {
    ACTIVE:'b-active', CONFIRMED:'b-active', DELIVERED:'b-active',
    PENDING:'b-pending', PROCESSING:'b-pending',
    SHIPPED:'b-shipped',
    BANNED:'b-banned', CANCELLED:'b-cancelled', REFUNDED:'b-cancelled',
    SOLD:'b-cancelled',
  }
  const labels = {
    ACTIVE:'Đang bán', CONFIRMED:'Đã xác nhận', DELIVERED:'Đã giao',
    PENDING:'Chờ xử lý', PROCESSING:'Đang xử lý',
    SHIPPED:'Đang giao', BANNED:'Bị cấm', CANCELLED:'Đã huỷ',
    REFUNDED:'Hoàn tiền', SOLD:'Đã bán',
  }
  return <span className={map[status] || 'b-cancelled'}>{labels[status] || status}</span>
}

const NavBar = ({ onNav, onSearch }) => {
  const user = useAuthUser()
  const [q, setQ] = useState('')
  const { data: countData } = useQuery({
    queryKey: ['notifCount'],
    queryFn: () => Notif.getUnreadCount().then(r => r.data.data),
    enabled: !!user,
    refetchInterval: 20000,
  })

  return (
    <div className="navbar">
      <div className="nav-logo" onClick={() => onNav('Homepage')}>🌸 FlowerMarket</div>
      <div className="nav-search">
        <span style={{ color:'#888' }}>🔍</span>
        <input
          placeholder="Tìm kiếm hoa, người bán..."
          value={q}
          onChange={e => setQ(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && onNav('Category Listing', { keyword: q })}
        />
      </div>
      <button className="btn btn-sm btn-secondary" style={{ color:'#ccc', border:'1px solid #444', background:'transparent' }}
        onClick={() => onNav('Category Listing')}>Danh mục ▾</button>
      <button className="btn btn-primary btn-sm" onClick={() => onNav('Create Listing')}>+ Bán hoa</button>
      <button className="btn btn-sm" style={{ color:'#ccc', border:'1px solid #444', background:'transparent', position:'relative' }}
        onClick={() => onNav('Chat')}>
        💬 Chat
      </button>
      {user ? (
        <button className="btn btn-sm" style={{ color:'#ccc', border:'1px solid #444', background:'transparent' }}
          onClick={() => onNav('User Profile')}>
          👤 {user.fullName?.split(' ').pop() || 'Tôi'}
          {countData > 0 && <span style={{ background:C.accent, color:'#fff', borderRadius:'50%', fontSize:9, padding:'1px 5px', marginLeft:4 }}>{countData}</span>}
        </button>
      ) : (
        <button className="btn btn-sm" style={{ color:'#ccc', border:'1px solid #444', background:'transparent' }}
          onClick={() => onNav('Login')}>👤 Đăng nhập</button>
      )}
    </div>
  )
}

const ProductCard = ({ listing, onClick }) => {
  const emojis = { ROSE:'🌹', TULIP:'🌷', SUNFLOWER:'🌻', ORCHID:'🪷', BOUQUET:'💐', DAISY:'🌼', DEFAULT:'🌸' }
  const emoji = emojis[listing?.category] || emojis.DEFAULT
  const imgUrl = listing?.imageUrls?.[0]
  return (
    <div className="product-card" style={{ minWidth:165, flex:'0 0 auto' }} onClick={onClick}>
      <div className="pimg" style={{ height:150 }}>
        {imgUrl
          ? <img src={imgUrl} alt={listing?.title} style={{ width:'100%', height:'100%', objectFit:'cover' }} />
          : <span>{emoji}</span>
        }
        <div className="heart">❤</div>
        {listing?.condition === 'FRESH' && <div className="fbadge">Tươi</div>}
      </div>
      <div style={{ padding:'10px 11px 12px' }}>
        <div style={{ fontSize:12, fontWeight:600, color:C.dark, marginBottom:4, lineHeight:1.3,
          overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
          {listing?.title || 'Bó Hoa Đẹp'}
        </div>
        <div className="serif" style={{ fontSize:14, fontWeight:700, color:C.accent }}>
          ₫ {listing?.price?.toLocaleString() || '—'}
        </div>
        <div style={{ display:'flex', gap:8, marginTop:5 }}>
          <span style={{ fontSize:10, color:C.muted }}>📍 {listing?.location || '—'}</span>
          <span style={{ fontSize:10, color:C.muted }}>⭐ {listing?.averageRating?.toFixed(1) || '—'}</span>
        </div>
      </div>
    </div>
  )
}

// ─── LOGIN / REGISTER ────────────────────────────────────────────────────────

const LoginPage = ({ onNav }) => {
  const [tab, setTab] = useState('login')
  const [form, setForm] = useState({ email:'', password:'', fullName:'', phone:'' })
  const [loading, setLoading] = useState(false)

  const set = k => e => setForm(p => ({ ...p, [k]: e.target.value }))

  const handleLogin = async () => {
    setLoading(true)
    try {
      const r = await Auth.login(form.email, form.password)
      const { accessToken, refreshToken, ...u } = r.data.data
      localStorage.setItem('accessToken',  accessToken)
      localStorage.setItem('refreshToken', refreshToken)
      authStore.set(u)
      toast.success(`Chào mừng, ${u.fullName}!`)
      onNav('Homepage')
    } catch (e) {
      toast.error(e.response?.data?.message || 'Sai email hoặc mật khẩu')
    } finally { setLoading(false) }
  }

  const handleRegister = async () => {
  setLoading(true)

  try {
    // ✅ validate cơ bản
    if (!form.fullName || !form.email || !form.password) {
      toast.error("Vui lòng nhập đầy đủ thông tin")
      return
    }

    // 🔥 tách fullName an toàn
    const parts = form.fullName.trim().split(/\s+/)
    const firstName = parts[0]
    const lastName = parts.length > 1 ? parts.slice(1).join(" ") : "User"

    const payload = {
      email: form.email,
      password: form.password,
      firstName,
      lastName,
      phoneNumber: form.phone,
      role: 'ROLE_BUYER'
    }

    console.log("SEND:", payload) // debug

    // ✅ gọi API
    const res = await Auth.register(payload)

    console.log("RES:", res) // debug

    // ⚠️ backend của bạn có thể KHÔNG trả token
    if (res.data) {
      const { accessToken, refreshToken, ...u } = res.data

      if (accessToken && refreshToken) {
        localStorage.setItem('accessToken', accessToken)
        localStorage.setItem('refreshToken', refreshToken)
      }

      authStore.set(u)
      toast.success(`Chào mừng, ${u.fullName || firstName}!`)
    } else {
      // 👉 trường hợp chỉ register thành công
      toast.success("Đăng ký thành công!")
    }

    onNav('Homepage')

  } catch (e) {
    console.log("ERROR FULL:", e)

    const msg =
      e.response?.data?.message ||
      e.response?.data?.error ||
      "Lỗi server"

    toast.error(msg)
  } finally {
    setLoading(false)
  }
}

  return (
    <div className="fade-up" style={{ minHeight:'100vh', display:'flex', alignItems:'center', justifyContent:'center', background:C.bg }}>
      <div style={{ width:420, background:'#fff', borderRadius:16, border:`1.5px solid ${C.border}`, overflow:'hidden', boxShadow:'0 8px 40px rgba(45,42,38,.1)' }}>
        <div style={{ background:C.dark, padding:'24px 28px' }}>
          <h1 style={{ color:'#fff', fontSize:22, marginBottom:4 }}>🌸 FlowerMarket</h1>
          <p style={{ color:'#888', fontSize:13 }}>Nền tảng mua bán hoa tươi</p>
        </div>
        <div style={{ padding:'24px 28px' }}>
          <div style={{ display:'flex', borderBottom:`2px solid ${C.border}`, marginBottom:20 }}>
            {['login','register'].map(t => (
              <div key={t} className={`ptab${tab===t?' active':''}`}
                onClick={() => setTab(t)} style={{ flex:1, textAlign:'center' }}>
                {t === 'login' ? 'Đăng nhập' : 'Đăng ký'}
              </div>
            ))}
          </div>

          {tab === 'login' ? (
            <div style={{ display:'flex', flexDirection:'column', gap:14 }}>
              <div><label className="label">Email</label>
                <input className="input" type="email" placeholder="your@email.com" value={form.email} onChange={set('email')} /></div>
              <div><label className="label">Mật khẩu</label>
                <input className="input" type="password" placeholder="••••••••" value={form.password} onChange={set('password')}
                  onKeyDown={e => e.key === 'Enter' && handleLogin()} /></div>
              <button className="btn btn-primary" style={{ justifyContent:'center', padding:12 }}
                onClick={handleLogin} disabled={loading}>
                {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
              </button>
              <div style={{ textAlign:'center', fontSize:12, color:C.muted }}>
                Demo: <strong>admin@flower.vn</strong> / <strong>password123</strong>
              </div>
            </div>
          ) : (
            <div style={{ display:'flex', flexDirection:'column', gap:14 }}>
              <div><label className="label">Họ tên</label>
                <input className="input" placeholder="Nguyễn Thị Lan" value={form.fullName} onChange={set('fullName')} /></div>
              <div><label className="label">Email</label>
                <input className="input" type="email" placeholder="your@email.com" value={form.email} onChange={set('email')} /></div>
              <div><label className="label">Số điện thoại</label>
                <input className="input" placeholder="0912345678" value={form.phone} onChange={set('phone')} /></div>
              <div><label className="label">Mật khẩu</label>
                <input className="input" type="password" placeholder="••••••••" value={form.password} onChange={set('password')} /></div>
              <button className="btn btn-primary" style={{ justifyContent:'center', padding:12 }}
                onClick={handleRegister} disabled={loading}>
                {loading ? 'Đang đăng ký...' : 'Tạo tài khoản'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── HOMEPAGE ────────────────────────────────────────────────────────────────

const HomePage = ({ onNav }) => {
  const { data: featuredData, isLoading: fl, error: fe } = useQuery({
    queryKey: ['listings', 'featured'],
    queryFn: () => Listings.getPublic({ page:0, size:5, sort:'createdAt,desc' }).then(r => r.data.data),
  })
  const { data: nearbyData, isLoading: nl } = useQuery({
    queryKey: ['listings', 'nearby'],
    queryFn: () => Listings.getPublic({ page:0, size:4 }).then(r => r.data.data),
  })

  const cats = [
    { emoji:'🌹', name:'Hoa Hồng', val:'ROSE' }, { emoji:'🌻', name:'Hướng Dương', val:'SUNFLOWER' },
    { emoji:'🌷', name:'Tulip', val:'TULIP' }, { emoji:'💐', name:'Bó Hoa', val:'BOUQUET' },
    { emoji:'🌷', name:'Phong Lan', val:'ORCHID' }, { emoji:'🌼', name:'Cúc', val:'DAISY' },
    { emoji:'🌺', name:'Hibiscus', val:'HIBISCUS' }, { emoji:'➕', name:'Khác', val:'' },
  ]

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        {/* Hero */}
        <div className="hero" style={{ marginBottom:32 }}>
          <h1 style={{ fontSize:36, color:C.dark, marginBottom:8, lineHeight:1.2 }}>🌸 Hoa Tươi Giao Tận Nơi</h1>
          <p style={{ fontSize:15, color:C.muted, marginBottom:22, maxWidth:420 }}>
            Tìm những bó hoa đẹp nhất từ người bán địa phương gần bạn
          </p>
          <div style={{ display:'flex', gap:12 }}>
            <button className="btn btn-primary" onClick={() => onNav('Category Listing')}>🔍 Khám phá ngay</button>
            <button className="btn btn-secondary" onClick={() => onNav('Create Listing')}>+ Đăng tin bán hoa</button>
          </div>
          <div style={{ marginTop:28, display:'flex', gap:28 }}>
            {[['🌸','Hoa tươi chất lượng'],['🚚','Giao hàng nhanh chóng'],['⭐','Người bán uy tín']].map(([i,l]) => (
              <div key={l} style={{ fontSize:13, color:C.muted, display:'flex', gap:6, alignItems:'center' }}>
                <span style={{ fontSize:20 }}>{i}</span>{l}
              </div>
            ))}
          </div>
        </div>

        {/* Categories */}
        <div style={{ marginBottom:32 }}>
          <h2 style={{ fontSize:20, marginBottom:16 }}>Danh mục hoa</h2>
          <div style={{ display:'flex', gap:10, flexWrap:'wrap' }}>
            {cats.map(c => (
              <div key={c.val} className="cat-pill" onClick={() => onNav('Category Listing', { category: c.val })}>
                <span style={{ fontSize:24 }}>{c.emoji}</span>
                <div style={{ fontSize:10, fontWeight:600, color:C.muted }}>{c.name}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Featured */}
        <div style={{ marginBottom:32 }}>
          <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
            <h2 style={{ fontSize:20 }}>⭐ Tin nổi bật</h2>
            <button className="btn btn-ghost btn-sm" onClick={() => onNav('Category Listing')}>Xem tất cả →</button>
          </div>
          {fl && <Spinner />}
          {fe && <ErrBox />}
          <div style={{ display:'flex', gap:14, flexWrap:'wrap' }}>
            {(featuredData?.content || []).map(l => (
              <ProductCard key={l.id} listing={l} onClick={() => onNav('Product Detail', { id: l.id })} />
            ))}
            {!fl && !fe && !(featuredData?.content?.length) && (
              <Empty icon="🌸" text="Chưa có tin đăng nào. Hãy là người đầu tiên!" />
            )}
          </div>
        </div>

        {/* Nearby */}
        <div style={{ display:'grid', gridTemplateColumns:'1fr 280px', gap:28 }}>
          <div>
            <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
              <h2 style={{ fontSize:20 }}>📍 Hoa gần bạn</h2>
              <button className="btn btn-ghost btn-sm">Xem bản đồ →</button>
            </div>
            {nl && <Spinner />}
            <div style={{ display:'flex', gap:14, flexWrap:'wrap' }}>
              {(nearbyData?.content || []).map(l => (
                <ProductCard key={l.id} listing={l} onClick={() => onNav('Product Detail', { id: l.id })} />
              ))}
            </div>
          </div>
          <div>
            <h2 style={{ fontSize:20, marginBottom:14 }}>🏪 Người bán nổi bật</h2>
            <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
              {['Lan\'s Flower Shop','Rose Garden HN','Tulip World'].map((name,i) => (
                <div key={name} style={{ background:'#fff', border:`1.5px solid ${C.border}`, borderRadius:10, padding:'12px 14px', display:'flex', gap:12, alignItems:'center' }}>
                  <div style={{ width:44, height:44, borderRadius:'50%', background:C.surfaceDk, display:'flex', alignItems:'center', justifyContent:'center', fontSize:22 }}>👩</div>
                  <div style={{ flex:1 }}>
                    <div style={{ fontWeight:700, fontSize:13 }}>{name}</div>
                    <div style={{ fontSize:11, color:C.muted, marginTop:2 }}>⭐ {(4.7+i*0.1).toFixed(1)} · {(200+i*34)} lượt bán</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── CATEGORY PAGE ───────────────────────────────────────────────────────────

const CategoryPage = ({ onNav, params: initParams = {} }) => {
  const [filters, setFilters] = useState({
    keyword: initParams.keyword || '',
    category: initParams.category || '',
    minPrice: '', maxPrice: '',
    location: '', condition: '',
    page: 0, size: 12, sort: 'createdAt,desc',
  })
  const [sort, setSort] = useState('Mới nhất')

  const queryParams = { ...filters }
  if (!queryParams.keyword) delete queryParams.keyword
  if (!queryParams.category) delete queryParams.category

  const { data, isLoading, error } = useQuery({
    queryKey: ['listings', 'browse', queryParams],
    queryFn: () => (filters.keyword
      ? Listings.search(queryParams)
      : Listings.getPublic(queryParams)
    ).then(r => r.data.data),
    staleTime: 20000,
  })

  const setFilter = (k, v) => setFilters(p => ({ ...p, [k]: v, page: 0 }))
  const content = data?.content || []
  const totalPages = data?.totalPages || 1

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:20 }}>
          <h2 style={{ fontSize:22 }}>
            {filters.keyword ? `Kết quả: "${filters.keyword}"` : 'Tất cả tin đăng'}
            <span style={{ color:C.muted, fontSize:14, fontFamily:'inherit', fontWeight:400 }}>
              {' '}({data?.totalElements || 0} kết quả)
            </span>
          </h2>
          <div style={{ display:'flex', gap:8 }}>
            {['Mới nhất','Giá tăng','Giá giảm','Đánh giá'].map((s,i) => {
              const sorts = ['createdAt,desc','price,asc','price,desc','averageRating,desc']
              return (
                <button key={s} className="btn btn-sm" style={{ background:sort===s?C.accent:C.surface, color:sort===s?'#fff':C.dark, border:`1.5px solid ${sort===s?C.accent:C.border}` }}
                  onClick={() => { setSort(s); setFilter('sort', sorts[i]) }}>{s}</button>
              )
            })}
          </div>
        </div>

        <div style={{ display:'grid', gridTemplateColumns:'220px 1fr', gap:24 }}>
          {/* Filter sidebar */}
          <div className="filter-box" style={{ height:'fit-content', position:'sticky', top:80 }}>
            <div style={{ fontWeight:700, fontSize:14, marginBottom:14, display:'flex', justifyContent:'space-between' }}>
              Bộ lọc
              <span style={{ fontSize:11, color:C.accent, cursor:'pointer' }}
                onClick={() => setFilters(p => ({ ...p, category:'', condition:'', minPrice:'', maxPrice:'', location:'' }))}>
                Xóa tất cả
              </span>
            </div>
            {/* Category */}
            <div className="filter-sec">
              <div style={{ fontSize:11, fontWeight:700, color:C.accent, marginBottom:8 }}>Danh mục</div>
              {[['','Tất cả'],['ROSE','Hoa Hồng'],['TULIP','Tulip'],['ORCHID','Phong Lan'],['SUNFLOWER','Hướng Dương'],['BOUQUET','Bó Hoa']].map(([v,l]) => (
                <label key={v} className="filter-opt">
                  <input type="radio" name="cat" checked={filters.category===v} onChange={() => setFilter('category', v)} style={{ accentColor:C.accent }} />
                  {l}
                </label>
              ))}
            </div>
            {/* Price */}
            <div className="filter-sec">
              <div style={{ fontSize:11, fontWeight:700, color:C.accent, marginBottom:8 }}>Khoảng giá (₫)</div>
              <div style={{ display:'flex', gap:8 }}>
                <input className="input" placeholder="Từ" value={filters.minPrice} onChange={e => setFilter('minPrice', e.target.value)} style={{ fontSize:11 }} />
                <input className="input" placeholder="Đến" value={filters.maxPrice} onChange={e => setFilter('maxPrice', e.target.value)} style={{ fontSize:11 }} />
              </div>
            </div>
            {/* Condition */}
            <div className="filter-sec">
              <div style={{ fontSize:11, fontWeight:700, color:C.accent, marginBottom:8 }}>Tình trạng</div>
              {[['','Tất cả'],['FRESH','Hoa tươi'],['DRIED','Hoa khô'],['ARTIFICIAL','Nhân tạo']].map(([v,l]) => (
                <label key={v} className="filter-opt">
                  <input type="radio" name="cond" checked={filters.condition===v} onChange={() => setFilter('condition', v)} style={{ accentColor:C.accent }} />
                  {l}
                </label>
              ))}
            </div>
            <button className="btn btn-primary" style={{ width:'100%', justifyContent:'center' }}>Áp dụng</button>
          </div>

          {/* Grid */}
          <div>
            {isLoading && <Spinner />}
            {error && <ErrBox />}
            {!isLoading && !error && (
              <>
                <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:14, marginBottom:24 }}>
                  {content.map(l => (
                    <ProductCard key={l.id} listing={l} onClick={() => onNav('Product Detail', { id: l.id })} />
                  ))}
                </div>
                {content.length === 0 && <Empty icon="🌸" text="Không tìm thấy kết quả phù hợp" />}
                {/* Pagination */}
                <div style={{ display:'flex', gap:6, justifyContent:'center' }}>
                  {Array.from({ length: Math.min(totalPages, 8) }, (_, i) => (
                    <div key={i} onClick={() => setFilter('page', i)}
                      style={{ width:32, height:32, borderRadius:6, display:'flex', alignItems:'center', justifyContent:'center', fontSize:12, fontWeight:600, cursor:'pointer', background:filters.page===i?C.accent:'#fff', color:filters.page===i?'#fff':C.dark, border:`1.5px solid ${filters.page===i?C.accent:C.border}` }}>
                      {i+1}
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── PRODUCT DETAIL ──────────────────────────────────────────────────────────

const ProductDetailPage = ({ onNav, params = {} }) => {
  const [selImg, setSelImg] = useState(0)
  const [reviewForm, setReviewForm] = useState({ rating: 5, comment: '' })
  const [showReviewForm, setShowReviewForm] = useState(false)
  const user = useAuthUser()

  const { data: listingData, isLoading, error } = useQuery({
    queryKey: ['listing', params.id],
    queryFn: () => Listings.getById(params.id).then(r => r.data.data),
    enabled: !!params.id,
  })
  const { data: statsData } = useQuery({
    queryKey: ['ratingStats', params.id],
    queryFn: () => Reviews.getStats(params.id).then(r => r.data.data),
    enabled: !!params.id,
  })
  const { data: reviewsData } = useQuery({
    queryKey: ['reviews', params.id],
    queryFn: () => Reviews.getByListing(params.id, { page:0, size:5 }).then(r => r.data.data),
    enabled: !!params.id,
  })

  const queryClient = useQueryClient()
  const createReview = useMutation({
    mutationFn: (d) => Reviews.create(d),
    onSuccess: () => {
      toast.success('Đã gửi đánh giá!')
      queryClient.invalidateQueries({ queryKey: ['reviews', params.id] })
      queryClient.invalidateQueries({ queryKey: ['ratingStats', params.id] })
      setShowReviewForm(false)
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Lỗi gửi đánh giá'),
  })

  if (!params.id) return <div className="page"><ErrBox msg="Không có ID sản phẩm" /></div>
  if (isLoading) return <div><NavBar onNav={onNav} /><div className="page"><Spinner /></div></div>
  if (error) return <div><NavBar onNav={onNav} /><div className="page"><ErrBox /></div></div>

  const l = listingData
  const imgs = l?.imageUrls?.length ? l.imageUrls : [null]
  const emojis = { ROSE:'🌹', TULIP:'🌷', SUNFLOWER:'🌻', ORCHID:'🪷', BOUQUET:'💐', DAISY:'🌼' }
  const emoji = emojis[l?.category] || '🌸'

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        <div style={{ fontSize:12, color:C.muted, marginBottom:16 }}>
          <span style={{ cursor:'pointer' }} onClick={() => onNav('Homepage')}>Trang chủ</span> ›{' '}
          <span style={{ cursor:'pointer' }} onClick={() => onNav('Category Listing')}>Hoa</span> ›{' '}
          <span style={{ color:C.dark }}>{l?.title}</span>
        </div>

        <div style={{ display:'grid', gridTemplateColumns:'360px 1fr', gap:32 }}>
          {/* Gallery */}
          <div>
            <div style={{ background:`linear-gradient(135deg,${C.surfaceDk},${C.surface})`, borderRadius:12, height:300, display:'flex', alignItems:'center', justifyContent:'center', marginBottom:12, border:`1.5px solid ${C.border}`, overflow:'hidden' }}>
              {imgs[selImg]
                ? <img src={imgs[selImg]} alt={l?.title} style={{ width:'100%', height:'100%', objectFit:'cover' }} />
                : <span style={{ fontSize:80 }}>{emoji}</span>
              }
            </div>
            <div style={{ display:'flex', gap:8, marginBottom:14, flexWrap:'wrap' }}>
              {imgs.map((img, i) => (
                <div key={i} onClick={() => setSelImg(i)} style={{ width:62, height:62, background:C.surface, borderRadius:8, display:'flex', alignItems:'center', justifyContent:'center', fontSize:24, cursor:'pointer', border:`2px solid ${selImg===i?C.accent:C.border}`, overflow:'hidden' }}>
                  {img ? <img src={img} alt="" style={{ width:'100%', height:'100%', objectFit:'cover' }} /> : emoji}
                </div>
              ))}
            </div>
            <div style={{ display:'flex', gap:8 }}>
              <button className="btn btn-secondary btn-sm">❤ Lưu</button>
              <button className="btn btn-secondary btn-sm">↗ Chia sẻ</button>
              <button className="btn btn-secondary btn-sm">🚨 Báo cáo</button>
            </div>
          </div>

          {/* Details */}
          <div>
            <h1 style={{ fontSize:26, marginBottom:10, lineHeight:1.2 }}>{l?.title}</h1>
            <div style={{ display:'flex', gap:8, marginBottom:16, flexWrap:'wrap' }}>
              {l?.category && <span className="chip">{l.category}</span>}
              {l?.condition && <span className="chip chip-green">✓ {l.condition}</span>}
              {l?.freeDelivery && <span className="chip chip-green">✓ Giao miễn phí</span>}
            </div>

            <div className="serif" style={{ fontSize:30, fontWeight:700, color:C.accent, marginBottom:16 }}>
              ₫ {l?.price?.toLocaleString()}
            </div>

            {/* Description */}
            <div className="card" style={{ padding:16, marginBottom:12, borderRadius:10 }}>
              <div style={{ fontWeight:700, marginBottom:10 }}>Mô tả sản phẩm</div>
              <div style={{ fontSize:13, color:C.muted, lineHeight:1.6 }}>{l?.description || 'Không có mô tả.'}</div>
            </div>

            {/* Details */}
            <div className="card" style={{ padding:16, marginBottom:12, borderRadius:10 }}>
              <div style={{ fontWeight:700, marginBottom:10 }}>📦 Chi tiết tin đăng</div>
              <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:8 }}>
                {[['Danh mục', l?.category],['Tình trạng', l?.condition],['Địa điểm', l?.location],
                  ['Tồn kho', l?.stockQuantity],['Đánh giá', statsData ? `${statsData.averageRating?.toFixed(1)} ⭐ (${statsData.totalReviews} đánh giá)` : '—']
                ].map(([k,v]) => (
                  <div key={k} style={{ fontSize:12 }}>
                    <span style={{ color:C.muted }}>{k}: </span>
                    <span style={{ fontWeight:600 }}>{v || '—'}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Seller info */}
            {l?.seller && (
              <div className="card" style={{ padding:16, marginBottom:18, borderRadius:10 }}>
                <div style={{ fontWeight:700, marginBottom:12 }}>🏪 Người bán</div>
                <div style={{ display:'flex', gap:14, alignItems:'center' }}>
                  <div style={{ width:52, height:52, borderRadius:'50%', background:C.surfaceDk, display:'flex', alignItems:'center', justifyContent:'center', fontSize:26, flexShrink:0 }}>
                    {l.seller.avatarUrl ? <img src={l.seller.avatarUrl} alt="" style={{ width:'100%', height:'100%', borderRadius:'50%', objectFit:'cover' }} /> : '👩'}
                  </div>
                  <div style={{ flex:1 }}>
                    <div style={{ fontWeight:700, fontSize:15 }}>{l.seller.fullName}</div>
                    <div style={{ fontSize:12, color:C.muted, marginTop:2 }}>📍 {l.seller.city || '—'}</div>
                  </div>
                </div>
              </div>
            )}

            <div style={{ display:'flex', gap:12 }}>
              <button className="btn btn-secondary" onClick={() => onNav('Chat')}>💬 Nhắn tin</button>
              <button className="btn btn-primary" style={{ flex:1, justifyContent:'center' }}
                onClick={() => user ? onNav('Order', { listingId: l?.id }) : onNav('Login')}>
                🛒 Mua ngay
              </button>
            </div>
          </div>
        </div>

        {/* Reviews */}
        <div style={{ marginTop:36 }}>
          <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
            <h2 style={{ fontSize:20 }}>⭐ Đánh giá ({statsData?.totalReviews || 0})</h2>
            {user && !showReviewForm && (
              <button className="btn btn-secondary btn-sm" onClick={() => setShowReviewForm(true)}>
                + Viết đánh giá
              </button>
            )}
          </div>

          {/* Write review form */}
          {showReviewForm && (
            <div className="card" style={{ padding:20, borderRadius:10, marginBottom:20 }}>
              <h3 style={{ marginBottom:14 }}>Đánh giá của bạn</h3>
              <div style={{ marginBottom:12 }}>
                <label className="label">Số sao</label>
                <div style={{ display:'flex', gap:8 }}>
                  {[1,2,3,4,5].map(s => (
                    <span key={s} onClick={() => setReviewForm(p => ({ ...p, rating:s }))}
                      style={{ fontSize:24, cursor:'pointer', color: s <= reviewForm.rating ? '#f59e0b' : C.border }}>
                      ★
                    </span>
                  ))}
                </div>
              </div>
              <div style={{ marginBottom:14 }}>
                <label className="label">Nhận xét</label>
                <textarea className="input" placeholder="Chia sẻ trải nghiệm của bạn..."
                  value={reviewForm.comment} onChange={e => setReviewForm(p => ({ ...p, comment: e.target.value }))} />
              </div>
              <div style={{ display:'flex', gap:10 }}>
                <button className="btn btn-primary" disabled={createReview.isPending}
                  onClick={() => createReview.mutate({ listingId: params.id, rating: reviewForm.rating, comment: reviewForm.comment })}>
                  {createReview.isPending ? 'Đang gửi...' : 'Gửi đánh giá'}
                </button>
                <button className="btn btn-ghost" onClick={() => setShowReviewForm(false)}>Huỷ</button>
              </div>
            </div>
          )}

          <div style={{ display:'flex', gap:16, alignItems:'flex-start' }}>
            {/* Stats */}
            {statsData && (
              <div style={{ background:'#fff', border:`1.5px solid ${C.border}`, borderRadius:10, padding:20, textAlign:'center', minWidth:160 }}>
                <div className="serif" style={{ fontSize:44, color:C.accent, fontWeight:900 }}>
                  {statsData.averageRating?.toFixed(1) || '—'}
                </div>
                <div className="stars">{'⭐'.repeat(Math.round(statsData.averageRating || 0))}</div>
                <div style={{ fontSize:11, color:C.muted, marginTop:4 }}>{statsData.totalReviews} đánh giá</div>
              </div>
            )}
            {/* Review cards */}
            <div style={{ display:'flex', gap:12, flexWrap:'wrap', flex:1 }}>
              {(reviewsData?.content || []).map(rv => (
                <div key={rv.id} style={{ background:'#fff', border:`1.5px solid ${C.border}`, borderRadius:10, padding:16, flex:'1 0 200px', maxWidth:280 }}>
                  <div style={{ display:'flex', gap:8, alignItems:'center', marginBottom:10 }}>
                    <div style={{ width:32, height:32, borderRadius:'50%', background:C.surface, display:'flex', alignItems:'center', justifyContent:'center' }}>👤</div>
                    <div>
                      <div style={{ fontWeight:600, fontSize:13 }}>{rv.reviewerName || 'Ẩn danh'}</div>
                      <div className="stars" style={{ fontSize:11 }}>{'⭐'.repeat(rv.rating)}</div>
                    </div>
                  </div>
                  <div style={{ fontSize:12, color:C.muted, lineHeight:1.5 }}>{rv.comment}</div>
                  <div style={{ fontSize:10, color:C.border, marginTop:8 }}>
                    {rv.createdAt ? formatDistanceToNow(new Date(rv.createdAt), { addSuffix:true, locale:vi }) : ''}
                  </div>
                </div>
              ))}
              {!reviewsData?.content?.length && <Empty icon="⭐" text="Chưa có đánh giá nào" />}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── CREATE LISTING ──────────────────────────────────────────────────────────

const CreateListingPage = ({ onNav }) => {
  const user = useAuthUser()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    title:'', description:'', category:'', condition:'FRESH',
    price:'', stockQuantity:'', location:'', tags:'',
  })
  const [images, setImages] = useState([])
  const [loading, setLoading] = useState(false)

  const set = k => e => setForm(p => ({ ...p, [k]: e.target.value }))

  const handleSubmit = async () => {
  if (!user) { toast.error('Bạn cần đăng nhập để đăng tin'); onNav('Login'); return }
  if (!form.title || !form.price || !form.category) { toast.error('Vui lòng điền đầy đủ thông tin bắt buộc'); return }

  setLoading(true)
  try {
    const fd = new FormData()

    // ✅ gửi JSON đúng format backend yêu cầu
    fd.append('data', new Blob(
      [JSON.stringify({
        ...form,
        price: Number(form.price),
        stockQuantity: Number(form.stockQuantity || 1)
      })],
      { type: 'application/json' }
    ))

    // images
    images.forEach(f => fd.append('images', f))

    await Listings.create(fd)

    toast.success('Đăng tin thành công! 🌸')
    queryClient.invalidateQueries({ queryKey: ['listings'] })
    queryClient.invalidateQueries({ queryKey: ['myListings'] })
    onNav('User Profile')

  } catch (e) {
    toast.error(e.response?.data?.message || 'Đăng tin thất bại. Hãy kiểm tra backend.')
  } finally {
    setLoading(false)
  }
 }

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        <h1 style={{ fontSize:26, marginBottom:24 }}>📝 Đăng tin bán hoa</h1>
        <div style={{ display:'grid', gridTemplateColumns:'1fr 320px', gap:28 }}>
          {/* Form */}
          <div style={{ display:'flex', flexDirection:'column', gap:20 }}>
            {!user && (
              <div style={{ background:'#fff3cd', border:'1px solid #ffc107', borderRadius:8, padding:14, fontSize:13 }}>
                ⚠ Bạn cần <span style={{ color:C.accent, cursor:'pointer', fontWeight:700 }} onClick={() => onNav('Login')}>đăng nhập</span> để đăng tin.
              </div>
            )}

            {/* Image upload */}
            <div>
              <label className="label">Ảnh sản phẩm * <span style={{ color:C.muted, fontWeight:400 }}>(tối đa 10 ảnh, mỗi ảnh 5MB)</span></label>
              <div style={{ display:'flex', gap:10, flexWrap:'wrap' }}>
                <label className="upload-box" htmlFor="img-upload">
                  <span style={{ fontSize:24, color:C.accent }}>+</span>
                  <span style={{ fontSize:10, color:C.muted, marginTop:4 }}>Thêm ảnh</span>
                </label>
                <input id="img-upload" type="file" accept="image/*" multiple style={{ display:'none' }}
                  onChange={e => setImages(prev => [...prev, ...Array.from(e.target.files)].slice(0,10))} />
                {images.map((f,i) => (
                  <div key={i} className="img-slot">
                    <img src={URL.createObjectURL(f)} alt="" style={{ width:'100%', height:'100%', objectFit:'cover', borderRadius:8 }} />
                    <div className="rm" onClick={() => setImages(p => p.filter((_,j) => j!==i))}>×</div>
                  </div>
                ))}
              </div>
              <div style={{ fontSize:11, color:C.muted, marginTop:6 }}>Ảnh đầu tiên sẽ làm ảnh bìa</div>
            </div>

            <div><label className="label">Tiêu đề *</label>
              <input className="input" placeholder="VD: 20 Bông Hồng Đỏ Tươi — Bó Cao Cấp" value={form.title} onChange={set('title')} /></div>

            <div><label className="label">Mô tả *</label>
              <textarea className="input" placeholder="Mô tả chi tiết về hoa: loại, độ tươi, số lượng, đóng gói..." value={form.description} onChange={set('description')} /></div>

            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:14 }}>
              <div><label className="label">Danh mục *</label>
                <select className="input" value={form.category} onChange={set('category')}>
                  <option value="">Chọn danh mục ▾</option>
                  {[['ROSE','Hoa Hồng'],['TULIP','Tulip'],['ORCHID','Phong Lan'],['SUNFLOWER','Hướng Dương'],['BOUQUET','Bó Hoa'],['DAISY','Cúc']].map(([v,l]) => (
                    <option key={v} value={v}>{l}</option>
                  ))}
                </select>
              </div>
              <div><label className="label">Tình trạng</label>
                <select className="input" value={form.condition} onChange={set('condition')}>
                  {[['FRESH','Hoa tươi'],['DRIED','Hoa khô'],['ARTIFICIAL','Nhân tạo']].map(([v,l]) => (
                    <option key={v} value={v}>{l}</option>
                  ))}
                </select>
              </div>
            </div>

            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:14 }}>
              <div><label className="label">Giá (₫) *</label>
                <input className="input" type="number" placeholder="220000" value={form.price} onChange={set('price')} /></div>
              <div><label className="label">Số lượng tồn kho</label>
                <input className="input" type="number" placeholder="50" value={form.stockQuantity} onChange={set('stockQuantity')} /></div>
            </div>

            <div><label className="label">Địa điểm *</label>
              <input className="input" placeholder="📍 VD: Hoàn Kiếm, Hà Nội" value={form.location} onChange={set('location')} /></div>

            <div><label className="label">Từ khoá</label>
              <input className="input" placeholder="VD: hoa hồng đỏ, valentine, đám cưới" value={form.tags} onChange={set('tags')} /></div>

            <div style={{ display:'flex', gap:12, paddingTop:8 }}>
              <button className="btn btn-secondary" onClick={() => onNav('Homepage')}>Huỷ</button>
              <button className="btn btn-primary" onClick={handleSubmit} disabled={loading || !user}>
                {loading ? 'Đang đăng tin...' : '📢 Đăng tin'}
              </button>
            </div>
          </div>

          {/* Preview */}
          <div>
            <div style={{ position:'sticky', top:80 }}>
              <label className="label">XEM TRƯỚC</label>
              <div style={{ background:'#fff', border:`1.5px solid ${C.border}`, borderRadius:12, overflow:'hidden' }}>
                <div style={{ background:`linear-gradient(135deg,${C.surfaceDk},${C.surface})`, height:160, display:'flex', alignItems:'center', justifyContent:'center', fontSize:images.length?0:60 }}>
                  {images.length ? <img src={URL.createObjectURL(images[0])} alt="" style={{ width:'100%', height:'100%', objectFit:'cover' }} /> : '🌹'}
                </div>
                <div style={{ padding:14 }}>
                  <div style={{ fontWeight:700, fontSize:13, marginBottom:4 }}>{form.title || 'Tiêu đề...'}</div>
                  <div className="serif" style={{ fontSize:18, color:C.accent, fontWeight:700, marginBottom:8 }}>
                    {form.price ? `₫ ${parseInt(form.price).toLocaleString()}` : '₫ —'}
                  </div>
                  {form.category && <span className="chip">{form.category}</span>}
                  {form.location && <div style={{ fontSize:11, color:C.muted, marginTop:6 }}>📍 {form.location}</div>}
                </div>
              </div>
              <div style={{ background:C.highlight, border:`1.5px solid ${C.border}`, borderRadius:10, padding:14, marginTop:14 }}>
                <div style={{ fontWeight:700, fontSize:13, marginBottom:8 }}>💡 Mẹo đăng tin</div>
                <div style={{ fontSize:12, color:C.muted, lineHeight:1.6 }}>
                  • Ảnh rõ nét tăng 3× lượt xem<br/>
                  • Mô tả chi tiết giúp tăng tỉ lệ chuyển đổi<br/>
                  • Giá hợp lý giúp bán hàng nhanh hơn
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── CHAT PAGE ───────────────────────────────────────────────────────────────

const ChatPage = ({ onNav }) => {
  const user = useAuthUser()
  const [activeConv, setActiveConv] = useState(null)
  const [input, setInput] = useState('')
  const [localMessages, setLocalMessages] = useState([])
  const messagesEndRef = useRef(null)

  const { data: convData, isLoading: convLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => Chat.getConversations().then(r => r.data.data),
    enabled: !!user,
    refetchInterval: 8000,
  })

  const { data: msgData } = useQuery({
    queryKey: ['messages', activeConv?.id],
    queryFn: () => Chat.getMessages(activeConv.id, { page:0, size:30 }).then(r => r.data.data),
    enabled: !!activeConv?.id,
    onSuccess: (d) => setLocalMessages(d?.content?.reverse() || []),
  })

  const { connected, sendMessage } = (() => {
    const clientRef = useRef(null)
    const [connected, setConnected] = useState(false)
    useEffect(() => {
      if (!user) return
      const token = localStorage.getItem('accessToken')
      if (!token) return
      try {
        const client = new Client({
          webSocketFactory: () => new SockJS('/api/ws'),
          connectHeaders: { Authorization: `Bearer ${token}` },
          reconnectDelay: 5000,
          onConnect: () => {
            setConnected(true)
            client.subscribe('/user/queue/messages', frame => {
              try {
                const msg = JSON.parse(frame.body)
                setLocalMessages(prev => [...prev, msg])
                setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior:'smooth' }), 50)
              } catch (_) {}
            })
          },
          onDisconnect: () => setConnected(false),
        })
        client.activate()
        clientRef.current = client
        return () => client.deactivate()
      } catch (_) {}
    }, [user])
    const sendMessage = useCallback((payload) => {
      if (!clientRef.current?.connected) return false
      clientRef.current.publish({ destination: '/app/chat.send', body: JSON.stringify(payload) })
      return true
    }, [])
    return { connected, sendMessage }
  })()

  const handleSend = () => {
    if (!input.trim() || !activeConv) return
    const ok = sendMessage({ conversationId: activeConv.id, content: input })
    if (!ok) {
      // Fallback REST
      api.post('/chat/send', { conversationId: activeConv.id, content: input })
        .then(r => setLocalMessages(prev => [...prev, r.data.data]))
        .catch(() => toast.error('Gửi tin nhắn thất bại'))
    } else {
      setLocalMessages(prev => [...prev, { id: Date.now(), content: input, senderId: user?.id, createdAt: new Date().toISOString() }])
    }
    setInput('')
    setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior:'smooth' }), 50)
  }

  useEffect(() => {
    if (msgData?.content) setLocalMessages(msgData.content.reverse())
  }, [msgData])

  if (!user) return (
    <div><NavBar onNav={onNav} />
      <div className="page"><Empty icon="💬" text="Vui lòng đăng nhập để xem tin nhắn" />
        <div style={{ textAlign:'center', marginTop:16 }}><button className="btn btn-primary" onClick={() => onNav('Login')}>Đăng nhập</button></div>
      </div>
    </div>
  )

  const convs = convData || []

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        <div style={{ display:'flex', border:`1.5px solid ${C.border}`, borderRadius:12, overflow:'hidden', height:560 }}>
          {/* Conversation list */}
          <div style={{ width:280, borderRight:`1.5px solid ${C.border}`, background:C.surface, display:'flex', flexDirection:'column' }}>
            <div style={{ padding:'12px 14px', borderBottom:`1px solid ${C.border}` }}>
              <input className="input" placeholder="🔍 Tìm kiếm..." style={{ fontSize:12 }} />
            </div>
            <div style={{ flex:1, overflowY:'auto' }}>
              {convLoading && <Spinner size={20} />}
              {convs.length === 0 && !convLoading && <Empty icon="💬" text="Chưa có hội thoại nào" />}
              {convs.map(c => (
                <div key={c.id} onClick={() => setActiveConv(c)}
                  style={{ padding:'11px 14px', borderBottom:`1px solid ${C.border}`, cursor:'pointer', background: activeConv?.id===c.id ? C.highlight : 'transparent', borderLeft:`3px solid ${activeConv?.id===c.id?C.accent:'transparent'}` }}>
                  <div style={{ display:'flex', gap:10, alignItems:'center' }}>
                    <div style={{ width:36, height:36, borderRadius:'50%', background:C.surfaceDk, display:'flex', alignItems:'center', justifyContent:'center', fontSize:18, flexShrink:0 }}>
                      {c.otherUser?.avatarUrl ? <img src={c.otherUser.avatarUrl} alt="" style={{ width:'100%', height:'100%', borderRadius:'50%' }} /> : '🌹'}
                    </div>
                    <div style={{ flex:1, minWidth:0 }}>
                      <div style={{ display:'flex', justifyContent:'space-between' }}>
                        <span style={{ fontSize:13, fontWeight:600 }}>{c.otherUser?.fullName || c.otherUserName || '—'}</span>
                        <span style={{ fontSize:10, color:C.muted }}>
                          {c.lastMessageAt ? formatDistanceToNow(new Date(c.lastMessageAt), { addSuffix:false, locale:vi }) : ''}
                        </span>
                      </div>
                      <div style={{ display:'flex', justifyContent:'space-between', marginTop:2 }}>
                        <span style={{ fontSize:11, color:C.muted, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap', maxWidth:140 }}>
                          {c.lastMessage || 'Bắt đầu cuộc trò chuyện'}
                        </span>
                        {c.unreadCount > 0 && <div className="notif-dot">{c.unreadCount}</div>}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Chat window */}
          {activeConv ? (
            <div style={{ flex:1, display:'flex', flexDirection:'column', background:'#fff' }}>
              <div style={{ padding:'12px 18px', borderBottom:`1px solid ${C.border}`, background:C.surface, display:'flex', alignItems:'center', justifyContent:'space-between' }}>
                <div style={{ display:'flex', gap:12, alignItems:'center' }}>
                  <div style={{ width:36, height:36, borderRadius:'50%', background:C.surfaceDk, display:'flex', alignItems:'center', justifyContent:'center', fontSize:18 }}>🌹</div>
                  <div>
                    <div style={{ fontWeight:700, fontSize:14 }}>{activeConv.otherUser?.fullName || 'Chat'}</div>
                    <div style={{ fontSize:11, color: connected ? C.green : C.muted }}>
                      {connected ? '● Đang kết nối' : '○ Ngoại tuyến'}
                    </div>
                  </div>
                </div>
              </div>

              <div style={{ flex:1, padding:'18px 20px', overflowY:'auto', display:'flex', flexDirection:'column', gap:12 }}>
                {localMessages.map(m => (
                  <div key={m.id} style={{ display:'flex', justifyContent: m.senderId===user?.id ? 'flex-end':'flex-start', gap:10, alignItems:'flex-end' }}>
                    {m.senderId !== user?.id && (
                      <div style={{ width:26, height:26, borderRadius:'50%', background:C.surface, display:'flex', alignItems:'center', justifyContent:'center', fontSize:13, flexShrink:0 }}>🌹</div>
                    )}
                    <div className={m.senderId===user?.id ? 'bubble-r' : 'bubble-l'}>{m.content}</div>
                  </div>
                ))}
                {localMessages.length === 0 && <div style={{ textAlign:'center', color:C.muted, fontSize:13, marginTop:40 }}>Bắt đầu cuộc trò chuyện 💬</div>}
                <div ref={messagesEndRef} />
              </div>

              <div style={{ padding:'12px 16px', borderTop:`1px solid ${C.border}`, background:C.surface }}>
                <div style={{ display:'flex', gap:8, alignItems:'center' }}>
                  <button className="btn btn-secondary btn-sm" style={{ padding:'5px 8px' }}>📎</button>
                  <input className="input" placeholder="Nhập tin nhắn..." value={input}
                    onChange={e => setInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSend()} />
                  <button className="btn btn-primary btn-sm" onClick={handleSend}>Gửi ➤</button>
                </div>
              </div>
            </div>
          ) : (
            <div style={{ flex:1, display:'flex', alignItems:'center', justifyContent:'center', flexDirection:'column', gap:12, color:C.muted }}>
              <span style={{ fontSize:48 }}>💬</span>
              <span>Chọn một cuộc trò chuyện để bắt đầu</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── ORDER PAGE ──────────────────────────────────────────────────────────────

const OrderPage = ({ onNav, params = {} }) => {
  const user = useAuthUser()
  const queryClient = useQueryClient()
  const [delivery, setDelivery] = useState('TODAY')
  const [payment, setPayment] = useState('COD')
  const [selectedAddr, setSelectedAddr] = useState(0)
  const [promoCode, setPromoCode] = useState('')
  const [loading, setLoading] = useState(false)

  const { data: meData } = useQuery({
    queryKey: ['me'],
    queryFn: () => Users.getMe().then(r => r.data.data),
    enabled: !!user,
  })

  const { data: listingData } = useQuery({
    queryKey: ['listing', params.listingId],
    queryFn: () => Listings.getById(params.listingId).then(r => r.data.data),
    enabled: !!params.listingId,
  })

  const listing = listingData || { title:'Sản phẩm', price:220000, seller:{ fullName:'Người bán' } }
  const deliveryFee = delivery === 'TODAY' ? 30000 : delivery === 'TOMORROW' ? 20000 : 0
  const discount = promoCode === 'FLOWER10' ? Math.round(listing.price * 0.1) : 0
  const total = listing.price + deliveryFee - discount

  const handleOrder = async () => {
    if (!user) { toast.error('Cần đăng nhập'); onNav('Login'); return }
    setLoading(true)
    try {
      const addr = meData?.addresses?.[selectedAddr]
      const orderRes = await Orders.create({
        listingId: listing.id,
        quantity: 1,
        shippingAddressId: addr?.id,
        deliveryType: delivery,
        note: '',
      })
      const order = orderRes.data.data
      // Process payment
      await Payments.process({
        orderId: order.id,
        paymentMethod: payment,
        amount: total,
        idempotencyKey: `${order.id}-${Date.now()}`,
      })
      toast.success('Đặt hàng thành công! 🌸')
      queryClient.invalidateQueries({ queryKey: ['myOrders'] })
      onNav('User Profile')
    } catch (e) {
      toast.error(e.response?.data?.message || 'Đặt hàng thất bại. Kiểm tra backend.')
    } finally { setLoading(false) }
  }

  const addresses = meData?.addresses || [
    { id:1, fullName:'Nguyễn Văn A', addressLine:'12 Hoàn Kiếm, Hà Nội', isDefault:true },
  ]

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        <h1 style={{ fontSize:24, marginBottom:24 }}>🛒 Xác nhận đơn hàng</h1>
        {!user && (
          <div style={{ background:'#fff3cd', border:'1px solid #ffc107', borderRadius:8, padding:14, marginBottom:20, fontSize:13 }}>
            ⚠ Vui lòng <span style={{ color:C.accent, cursor:'pointer', fontWeight:700 }} onClick={() => onNav('Login')}>đăng nhập</span> để đặt hàng.
          </div>
        )}
        <div style={{ display:'grid', gridTemplateColumns:'1fr 340px', gap:28 }}>
          <div style={{ display:'flex', flexDirection:'column', gap:20 }}>
            {/* Product */}
            <div className="card" style={{ padding:0, borderRadius:12 }}>
              <div style={{ padding:'14px 18px', borderBottom:`1px solid ${C.border}`, background:C.surface, fontWeight:700 }}>🛒 Sản phẩm</div>
              <div style={{ padding:16, display:'flex', gap:14, alignItems:'center' }}>
                <div style={{ width:70, height:70, background:`linear-gradient(135deg,${C.surfaceDk},${C.surface})`, borderRadius:8, display:'flex', alignItems:'center', justifyContent:'center', fontSize:36, flexShrink:0 }}>
                  {listing.imageUrls?.[0] ? <img src={listing.imageUrls[0]} alt="" style={{ width:'100%', height:'100%', objectFit:'cover', borderRadius:8 }} /> : '🌹'}
                </div>
                <div style={{ flex:1 }}>
                  <div style={{ fontWeight:700, fontSize:14 }}>{listing.title}</div>
                  <div style={{ fontSize:12, color:C.muted, marginTop:3 }}>Người bán: {listing.seller?.fullName}</div>
                </div>
                <div className="serif" style={{ fontSize:18, color:C.accent, fontWeight:700 }}>₫ {listing.price?.toLocaleString()}</div>
              </div>
            </div>

            {/* Address */}
            <div>
              <div style={{ display:'flex', justifyContent:'space-between', marginBottom:12 }}>
                <h3>📍 Địa chỉ giao hàng</h3>
                <button className="btn btn-ghost btn-sm">+ Thêm mới</button>
              </div>
              {addresses.map((a, i) => (
                <div key={i} onClick={() => setSelectedAddr(i)}
                  style={{ border:`1.5px solid ${selectedAddr===i?C.accent:C.border}`, borderRadius:8, padding:12, marginBottom:8, background:selectedAddr===i?C.highlight:'#fff', cursor:'pointer' }}>
                  <div style={{ display:'flex', gap:10, alignItems:'center' }}>
                    <div style={{ width:14, height:14, borderRadius:'50%', border:`2px solid ${selectedAddr===i?C.accent:C.border}`, background:selectedAddr===i?C.accent:'transparent' }} />
                    <div style={{ flex:1 }}>
                      <span style={{ fontWeight:600, fontSize:13 }}>{a.fullName}</span>
                      {a.isDefault && <span style={{ fontSize:9, color:C.green, fontWeight:700, marginLeft:8, background:'#e8f4e8', padding:'1px 6px', borderRadius:8 }}>MẶC ĐỊNH</span>}
                      <div style={{ fontSize:12, color:C.muted, marginTop:2 }}>{a.addressLine}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Delivery */}
            <div>
              <h3 style={{ marginBottom:12 }}>🕐 Thời gian giao hàng</h3>
              <div style={{ display:'grid', gridTemplateColumns:'repeat(3,1fr)', gap:10 }}>
                {[['TODAY','Hôm nay','2–4 tiếng','+₫30k'],['TOMORROW','Ngày mai','9h–18h','+₫20k'],['SCHEDULE','Hẹn lịch','Chọn ngày','Miễn phí']].map(([v,l,s,p]) => (
                  <div key={v} className={`dopt${delivery===v?' active':''}`} onClick={() => setDelivery(v)}>
                    <div style={{ fontWeight:700, fontSize:14 }}>{l}</div>
                    <div style={{ fontSize:11, color:C.muted, marginTop:2 }}>{s}</div>
                    <div style={{ fontSize:11, color:C.green, fontWeight:600, marginTop:3 }}>{p}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Payment */}
            <div>
              <h3 style={{ marginBottom:12 }}>💳 Phương thức thanh toán</h3>
              <div style={{ display:'flex', flexDirection:'column', gap:8 }}>
                {[['COD','💵','Tiền mặt khi nhận hàng'],['MOMO','📱','Ví MoMo'],['ZALOPAY','💙','ZaloPay'],['BANK_TRANSFER','🏦','Chuyển khoản ngân hàng'],['CREDIT_CARD','💳','Visa / Mastercard']].map(([v,ico,l]) => (
                  <div key={v} className={`popt${payment===v?' active':''}`} onClick={() => setPayment(v)}>
                    <div style={{ width:14, height:14, borderRadius:'50%', border:`2px solid ${payment===v?C.accent:C.border}`, background:payment===v?C.accent:'transparent' }} />
                    <span style={{ fontSize:22 }}>{ico}</span>
                    <span style={{ fontSize:13, fontWeight:500 }}>{l}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Promo */}
            <div>
              <label className="label">🏷 Mã giảm giá</label>
              <div style={{ display:'flex', gap:8 }}>
                <input className="input" placeholder="Nhập mã... (thử FLOWER10)" value={promoCode} onChange={e => setPromoCode(e.target.value.toUpperCase())} style={{ flex:1 }} />
                <button className="btn btn-primary">Áp dụng</button>
              </div>
              {promoCode === 'FLOWER10' && <div style={{ fontSize:12, color:C.green, marginTop:6 }}>✓ Giảm 10%!</div>}
            </div>
          </div>

          {/* Summary */}
          <div style={{ position:'sticky', top:80, height:'fit-content' }}>
            <div className="card" style={{ padding:20, borderRadius:12 }}>
              <h3 style={{ marginBottom:16 }}>💰 Tóm tắt thanh toán</h3>
              {[['Sản phẩm', `₫ ${listing.price?.toLocaleString()}`],['Phí giao hàng', `₫ ${deliveryFee.toLocaleString()}`],discount>0?['Giảm giá', `- ₫ ${discount.toLocaleString()}`]:null].filter(Boolean).map(([k,v]) => (
                <div key={k} style={{ display:'flex', justifyContent:'space-between', marginBottom:8 }}>
                  <span style={{ fontSize:13, color:C.muted }}>{k}</span>
                  <span style={{ fontSize:13, fontWeight:600 }}>{v}</span>
                </div>
              ))}
              <div className="divider" />
              <div style={{ display:'flex', justifyContent:'space-between', marginBottom:20 }}>
                <span style={{ fontWeight:700, fontSize:14 }}>Tổng cộng</span>
                <span className="serif" style={{ fontSize:22, color:C.accent, fontWeight:700 }}>₫ {total.toLocaleString()}</span>
              </div>
              <button className="btn btn-primary" style={{ width:'100%', justifyContent:'center', padding:12 }}
                onClick={handleOrder} disabled={loading || !user}>
                {loading ? 'Đang xử lý...' : '✅ Xác nhận đặt hàng'}
              </button>
              <div style={{ fontSize:11, color:C.muted, marginTop:10, textAlign:'center', lineHeight:1.5 }}>
                Bằng cách đặt hàng, bạn đồng ý với Điều khoản của chúng tôi
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── USER PROFILE ────────────────────────────────────────────────────────────

const UserProfilePage = ({ onNav }) => {
  const user = useAuthUser()
  const [tab, setTab] = useState('listings')
  const queryClient = useQueryClient()

  const { data: meData, isLoading: meLoading } = useQuery({
    queryKey: ['me'],
    queryFn: () => Users.getMe().then(r => r.data.data),
    enabled: !!user,
  })
  const { data: listingsData, isLoading: listLoading } = useQuery({
    queryKey: ['myListings'],
    queryFn: () => Listings.getMy({ page:0, size:20 }).then(r => r.data.data),
    enabled: !!user && tab === 'listings',
  })
  const { data: ordersData, isLoading: ordLoading } = useQuery({
    queryKey: ['myOrders'],
    queryFn: () => Orders.getMy({ page:0, size:20 }).then(r => r.data.data),
    enabled: !!user && tab === 'orders',
  })

  const deleteMutation = useMutation({
    mutationFn: (id) => Listings.remove(id),
    onSuccess: () => { toast.success('Đã xoá tin đăng'); queryClient.invalidateQueries({ queryKey:['myListings'] }) },
    onError: (e) => toast.error(e.response?.data?.message || 'Lỗi xoá'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => Orders.cancel(id),
    onSuccess: () => { toast.success('Đã huỷ đơn hàng'); queryClient.invalidateQueries({ queryKey:['myOrders'] }) },
    onError: (e) => toast.error(e.response?.data?.message || 'Không thể huỷ'),
  })

  const handleLogout = async () => {
    try { await Auth.logout() } catch (_) {}
    localStorage.clear()
    authStore.set(null)
    toast.success('Đã đăng xuất')
    onNav('Homepage')
  }

  if (!user) return (
    <div><NavBar onNav={onNav} />
      <div className="page"><Empty icon="👤" text="Vui lòng đăng nhập để xem trang cá nhân" />
        <div style={{ textAlign:'center', marginTop:16 }}><button className="btn btn-primary" onClick={() => onNav('Login')}>Đăng nhập</button></div>
      </div>
    </div>
  )

  const me = meData
  const listings = listingsData?.content || []
  const orders = ordersData?.content || []
  const tabs = [['listings','Tin đăng'],['orders','Lịch sử đơn'],['saved','Đã lưu'],['reviews','Đánh giá']]

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        {/* Profile Header */}
        <div style={{ background:`linear-gradient(135deg,${C.surfaceDk},${C.surface})`, borderRadius:14, padding:'28px 28px', marginBottom:24, display:'flex', gap:20, alignItems:'center' }}>
          <div style={{ width:88, height:88, borderRadius:'50%', background:C.surfaceDk, display:'flex', alignItems:'center', justifyContent:'center', fontSize:44, border:`3px solid ${C.accent}`, flexShrink:0 }}>
            {me?.avatarUrl ? <img src={me.avatarUrl} alt="" style={{ width:'100%', height:'100%', borderRadius:'50%', objectFit:'cover' }} /> : '👤'}
          </div>
          <div style={{ flex:1 }}>
            {meLoading ? <Spinner size={20} /> : (
              <>
                <h2 style={{ fontSize:22, marginBottom:4 }}>{me?.fullName || user.fullName}</h2>
                <div style={{ fontSize:13, color:C.muted, marginBottom:10 }}>
                  📍 {me?.city || 'Việt Nam'} · {me?.email}
                </div>
                <div style={{ display:'flex', gap:10, flexWrap:'wrap' }}>
                  <span className="chip">Vai trò: {me?.role?.replace('ROLE_','') || '—'}</span>
                  {me?.phoneNumber && <span className="chip">📱 {me.phoneNumber}</span>}
                </div>
              </>
            )}
          </div>
          <div style={{ display:'flex', flexDirection:'column', gap:8 }}>
            {me?.role === 'ROLE_SELLER' || me?.role === 'ROLE_ADMIN' ? (
              <button className="btn btn-primary btn-sm" onClick={() => onNav('Seller Dashboard')}>📊 Dashboard</button>
            ) : null}
            {me?.role === 'ROLE_ADMIN' && (
              <button className="btn btn-secondary btn-sm" onClick={() => onNav('Admin Dashboard')}>🔧 Admin</button>
            )}
            <button className="btn btn-ghost btn-sm" style={{ color:'#dc2626' }} onClick={handleLogout}>Đăng xuất</button>
          </div>
        </div>

        {/* Tabs */}
        <div style={{ display:'flex', borderBottom:`2px solid ${C.border}`, marginBottom:24 }}>
          {tabs.map(([id,label]) => (
            <div key={id} className={`ptab${tab===id?' active':''}`} onClick={() => setTab(id)}>{label}</div>
          ))}
        </div>

        {/* My Listings */}
        {tab === 'listings' && (
          <div>
            <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
              <h3>TIN ĐĂNG CỦA TÔI ({listings.length})</h3>
              <button className="btn btn-primary btn-sm" onClick={() => onNav('Create Listing')}>+ Đăng tin mới</button>
            </div>
            {listLoading && <Spinner />}
            {!listLoading && listings.length === 0 && <Empty icon="📦" text="Bạn chưa đăng tin nào" />}
            <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:14 }}>
              {listings.map(l => (
                <div key={l.id} className="card" style={{ borderRadius:10 }}>
                  <div style={{ height:100, background:`linear-gradient(135deg,${C.surfaceDk},${C.surface})`, display:'flex', alignItems:'center', justifyContent:'center', fontSize:40 }}>
                    {l.imageUrls?.[0] ? <img src={l.imageUrls[0]} alt="" style={{ width:'100%', height:'100%', objectFit:'cover' }} /> : '🌹'}
                  </div>
                  <div style={{ padding:'10px 12px' }}>
                    <div style={{ fontSize:12, fontWeight:600, marginBottom:4, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{l.title}</div>
                    <div className="serif" style={{ fontSize:13, color:C.accent, fontWeight:700, marginBottom:8 }}>₫ {l.price?.toLocaleString()}</div>
                    <div style={{ display:'flex', gap:6, alignItems:'center', flexWrap:'wrap' }}>
                      {statusBadge(l.status)}
                      <button className="btn btn-ghost btn-sm" style={{ padding:'2px 7px', fontSize:11 }}
                        onClick={() => onNav('Product Detail', { id: l.id })}>👁</button>
                      <button className="btn btn-danger btn-sm" style={{ padding:'2px 7px', fontSize:11 }}
                        onClick={() => deleteMutation.mutate(l.id)}>🗑</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Orders */}
        {tab === 'orders' && (
          <div>
            <h3 style={{ marginBottom:16 }}>LỊCH SỬ ĐƠN HÀNG</h3>
            {ordLoading && <Spinner />}
            {!ordLoading && orders.length === 0 && <Empty icon="🛒" text="Chưa có đơn hàng nào" />}
            {orders.map(o => (
              <div key={o.id} className="card" style={{ padding:'14px 18px', marginBottom:10, borderRadius:10, display:'flex', alignItems:'center', gap:16 }}>
                <div style={{ width:56, height:56, background:`linear-gradient(135deg,${C.surfaceDk},${C.surface})`, borderRadius:8, display:'flex', alignItems:'center', justifyContent:'center', fontSize:26, flexShrink:0 }}>🌹</div>
                <div style={{ flex:1 }}>
                  <div style={{ fontWeight:700 }}>Đơn #{o.orderNumber || o.id}</div>
                  <div style={{ fontSize:12, color:C.muted, marginTop:2 }}>
                    {o.items?.map(i => i.listingTitle).join(', ')}
                  </div>
                  <div style={{ fontSize:11, color:C.muted }}>
                    {o.createdAt ? new Date(o.createdAt).toLocaleDateString('vi-VN') : ''}
                  </div>
                </div>
                <div>
                  <div className="serif" style={{ fontSize:16, color:C.accent, fontWeight:700, textAlign:'right', marginBottom:6 }}>
                    ₫ {o.totalAmount?.toLocaleString()}
                  </div>
                  <div style={{ display:'flex', gap:6, alignItems:'center' }}>
                    {statusBadge(o.status)}
                    {o.status === 'PENDING' && (
                      <button className="btn btn-danger btn-sm" onClick={() => cancelMutation.mutate(o.id)}>Huỷ</button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {(tab === 'saved' || tab === 'reviews') && (
          <Empty icon={tab==='saved'?'❤':'⭐'} text={tab==='saved'?'Chưa có tin đăng đã lưu':'Chưa có đánh giá nào'} />
        )}
      </div>
    </div>
  )
}

// ─── SELLER DASHBOARD ────────────────────────────────────────────────────────

const SellerDashboard = ({ onNav }) => {
  const user = useAuthUser()
  const [sideTab, setSideTab] = useState('📊 Tổng quan')
  const queryClient = useQueryClient()

  const { data: listingsData, isLoading: ll } = useQuery({
    queryKey: ['myListings', 'all'],
    queryFn: () => Listings.getMy({ page:0, size:100 }).then(r => r.data.data),
    enabled: !!user,
  })
  const { data: ordersData, isLoading: ol } = useQuery({
    queryKey: ['sellerOrders'],
    queryFn: () => Orders.getMy({ page:0, size:50 }).then(r => r.data.data),
    enabled: !!user,
  })

  const updateOrderStatus = useMutation({
    mutationFn: ({ id, status }) => Orders.updateStatus(id, status),
    onSuccess: () => { toast.success('Cập nhật trạng thái đơn'); queryClient.invalidateQueries({ queryKey:['sellerOrders'] }) },
  })

  const listings = listingsData?.content || []
  const orders   = ordersData?.content  || []
  const revenue  = orders.filter(o => o.status === 'DELIVERED').reduce((s, o) => s + (o.totalAmount||0), 0)
  const pendingOrders = orders.filter(o => o.status === 'PENDING').length

  const bars = [40,65,50,80,70,90,85]
  const days = ['T2','T3','T4','T5','T6','T7','CN']
  const sideItems = ['📊 Tổng quan','📦 Tin đăng','🛒 Đơn hàng','💬 Tin nhắn','⭐ Đánh giá','📈 Phân tích','💰 Doanh thu','⚙ Cài đặt']

  if (!user) return (
    <div><NavBar onNav={onNav} />
      <div className="page"><Empty icon="📊" text="Vui lòng đăng nhập" /></div>
    </div>
  )

  return (
    <div className="fade-up">
      <NavBar onNav={onNav} />
      <div className="page">
        <div style={{ display:'grid', gridTemplateColumns:'190px 1fr', gap:24 }}>
          {/* Sidebar */}
          <div style={{ background:'#fff', border:`1.5px solid ${C.border}`, borderRadius:12, padding:14, height:'fit-content', position:'sticky', top:80 }}>
            <div style={{ fontSize:12, fontWeight:700, color:C.accent, marginBottom:12 }}>SELLER HUB</div>
            {sideItems.map(item => (
              <div key={item} className={`sidebar-item${sideTab===item?' active':''}`} onClick={() => setSideTab(item)}>{item}</div>
            ))}
          </div>

          {/* Main */}
          <div style={{ display:'flex', flexDirection:'column', gap:24 }}>
            {/* KPIs */}
            <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:14 }}>
              {[
                { label:'Tổng doanh thu', val: ll || ol ? '...' : `₫ ${(revenue/1000000).toFixed(1)}M`, sub:'Từ đơn đã giao', icon:'💰' },
                { label:'Tin đang bán', val: ll ? '...' : listings.filter(l=>l.status==='ACTIVE').length, sub:`${listings.length} tin tổng`, icon:'📦' },
                { label:'Đơn hàng mới', val: ol ? '...' : pendingOrders, sub:'Cần xử lý', icon:'🛒' },
                { label:'Tổng đơn', val: ol ? '...' : orders.length, sub:'Tất cả trạng thái', icon:'📋' },
              ].map(s => (
                <div key={s.label} className="kpi" style={{ borderTop:`3px solid ${C.accent}` }}>
                  <div style={{ fontSize:26, marginBottom:6 }}>{s.icon}</div>
                  <div className="serif" style={{ fontSize:22, fontWeight:700, color:C.accent }}>{s.val}</div>
                  <div style={{ fontSize:12, fontWeight:600, marginTop:2 }}>{s.label}</div>
                  <div style={{ fontSize:11, color:C.green, marginTop:2 }}>{s.sub}</div>
                </div>
              ))}
            </div>

            {/* Chart */}
            <div className="card" style={{ padding:20, borderRadius:12 }}>
              <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
                <h3>📈 Doanh thu theo ngày (dữ liệu mẫu)</h3>
                <div style={{ display:'flex', gap:6 }}>
                  {['7n','30n','90n'].map((t,i) => (
                    <button key={t} className="btn btn-sm" style={{ background:i===0?C.accent:C.surface, color:i===0?'#fff':C.dark, border:`1.5px solid ${i===0?C.accent:C.border}` }}>{t}</button>
                  ))}
                </div>
              </div>
              <div style={{ display:'flex', gap:10, alignItems:'flex-end', height:120, padding:'0 4px' }}>
                {bars.map((h,i) => (
                  <div key={i} style={{ flex:1, display:'flex', flexDirection:'column', alignItems:'center', gap:4 }}>
                    <div className="bar" style={{ width:'100%', height:`${h}%`, opacity:0.65+i*0.05 }} />
                    <span style={{ fontSize:9, color:C.muted }}>{days[i]}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Orders table */}
            <div className="card" style={{ borderRadius:12, overflow:'hidden' }}>
              <div style={{ padding:'14px 18px', borderBottom:`1px solid ${C.border}`, display:'flex', justifyContent:'space-between' }}>
                <h3>🛒 Đơn hàng ({orders.length})</h3>
                <button className="btn btn-ghost btn-sm" onClick={() => setSideTab('🛒 Đơn hàng')}>Xem tất cả →</button>
              </div>
              {ol && <Spinner />}
              {!ol && orders.length === 0 && <Empty icon="🛒" text="Chưa có đơn hàng" />}
              {orders.length > 0 && (
                <table className="table">
                  <thead>
                    <tr>{['Mã đơn','Sản phẩm','Người mua','Giá trị','Trạng thái','Thao tác'].map(h => <th key={h}>{h}</th>)}</tr>
                  </thead>
                  <tbody>
                    {orders.slice(0,8).map(o => (
                      <tr key={o.id}>
                        <td style={{ color:C.accent, fontWeight:700 }}>#{o.orderNumber || o.id}</td>
                        <td>{o.items?.[0]?.listingTitle || '—'}</td>
                        <td style={{ color:C.muted }}>{o.buyerName || '—'}</td>
                        <td className="serif" style={{ fontWeight:700, color:C.accent }}>₫ {o.totalAmount?.toLocaleString()}</td>
                        <td>{statusBadge(o.status)}</td>
                        <td>
                          <div style={{ display:'flex', gap:4 }}>
                            {o.status === 'PENDING' && (
                              <button className="btn btn-green btn-sm" onClick={() => updateOrderStatus.mutate({ id:o.id, status:'CONFIRMED' })}>Xác nhận</button>
                            )}
                            {o.status === 'CONFIRMED' && (
                              <button className="btn btn-primary btn-sm" onClick={() => updateOrderStatus.mutate({ id:o.id, status:'SHIPPED' })}>Giao hàng</button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Listings table */}
            <div className="card" style={{ borderRadius:12, overflow:'hidden' }}>
              <div style={{ padding:'14px 18px', borderBottom:`1px solid ${C.border}`, display:'flex', justifyContent:'space-between' }}>
                <h3>📦 Tin đăng của tôi ({listings.length})</h3>
                <button className="btn btn-primary btn-sm" onClick={() => onNav('Create Listing')}>+ Đăng tin mới</button>
              </div>
              {ll && <Spinner />}
              {!ll && listings.length === 0 && <Empty icon="📦" text="Chưa có tin đăng" />}
              {listings.length > 0 && (
                <table className="table">
                  <thead>
                    <tr>{['Tiêu đề','Danh mục','Giá','Tồn kho','Trạng thái','Lượt xem'].map(h => <th key={h}>{h}</th>)}</tr>
                  </thead>
                  <tbody>
                    {listings.slice(0,10).map(l => (
                      <tr key={l.id} style={{ cursor:'pointer' }} onClick={() => onNav('Product Detail', { id:l.id })}>
                        <td style={{ fontWeight:600, maxWidth:200, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{l.title}</td>
                        <td><span className="chip">{l.category}</span></td>
                        <td className="serif" style={{ color:C.accent, fontWeight:700 }}>₫ {l.price?.toLocaleString()}</td>
                        <td>{l.stockQuantity}</td>
                        <td>{statusBadge(l.status)}</td>
                        <td style={{ color:C.muted }}>{l.viewCount || 0}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── ADMIN DASHBOARD ─────────────────────────────────────────────────────────

const AdminDashboard = ({ onNav }) => {
  const user = useAuthUser()
  const [sideTab, setSideTab] = useState('📊 Dashboard')
  const queryClient = useQueryClient()

  const { data: dashData, isLoading: dl } = useQuery({
    queryKey: ['adminDashboard'],
    queryFn: () => Admin.getDashboard().then(r => r.data.data),
    enabled: !!user,
    staleTime: 60000,
  })
  const { data: usersData, isLoading: ul } = useQuery({
    queryKey: ['adminUsers', { page:0, size:20 }],
    queryFn: () => Admin.getUsers({ page:0, size:20 }).then(r => r.data.data),
    enabled: !!user,
  })
  const { data: listingsData, isLoading: lll } = useQuery({
    queryKey: ['adminListings', { page:0, size:20 }],
    queryFn: () => Admin.getListings({ page:0, size:20 }).then(r => r.data.data),
    enabled: !!user,
  })

  const toggleUser = useMutation({
    mutationFn: (id) => Admin.toggleUser(id),
    onSuccess: () => { toast.success('Đã cập nhật trạng thái user'); queryClient.invalidateQueries({ queryKey:['adminUsers'] }) },
  })
  const updateListing = useMutation({
    mutationFn: ({ id, status }) => Admin.updateListing(id, status),
    onSuccess: () => { toast.success('Đã cập nhật tin đăng'); queryClient.invalidateQueries({ queryKey:['adminListings'] }) },
  })

  const sideItems = ['📊 Dashboard','👥 Người dùng','📦 Tin đăng','🛒 Đơn hàng','💬 Báo cáo','📈 Phân tích','💰 Doanh thu','⚙ Cài đặt']

  if (!user) return (
    <div style={{ minHeight:'100vh', display:'flex', alignItems:'center', justifyContent:'center', background:C.bg }}>
      <div style={{ textAlign:'center' }}>
        <div style={{ fontSize:48, marginBottom:12 }}>🔒</div>
        <div>Cần đăng nhập để truy cập Admin Panel</div>
        <button className="btn btn-primary" style={{ marginTop:16 }} onClick={() => onNav('Login')}>Đăng nhập</button>
      </div>
    </div>
  )

  const dash = dashData || {}
  const usersList     = usersData?.content    || []
  const listingsList  = listingsData?.content || []

  return (
    <div className="fade-up">
      {/* Admin navbar */}
      <div style={{ background:'#1a1714', padding:'10px 24px', display:'flex', alignItems:'center', gap:14 }}>
        <div className="nav-logo" onClick={() => onNav('Homepage')}>🌸 FlowerMarket</div>
        <span style={{ background:C.accent, color:'#fff', fontSize:10, fontWeight:700, padding:'3px 8px', borderRadius:4, letterSpacing:'0.06em' }}>ADMIN</span>
        <div style={{ flex:1 }} />
        {['👤 '+(user?.fullName||'Admin'),'⚙ Cài đặt'].map(i => (
          <span key={i} style={{ color:'#888', fontSize:12, cursor:'pointer', padding:'4px 10px', border:'1px solid #333', borderRadius:5 }}>{i}</span>
        ))}
        <button className="btn btn-sm" style={{ background:'transparent', color:'#888', border:'1px solid #333' }}
          onClick={async () => { try { await Auth.logout() } catch (_) {} localStorage.clear(); authStore.set(null); onNav('Homepage') }}>
          Đăng xuất
        </button>
      </div>

      <div style={{ padding:'24px 28px', maxWidth:1200, margin:'0 auto' }}>
        <div style={{ display:'grid', gridTemplateColumns:'200px 1fr', gap:24 }}>
          {/* Sidebar */}
          <div style={{ background:'#2D2A26', borderRadius:12, padding:14, height:'fit-content', position:'sticky', top:60 }}>
            <div style={{ fontSize:10, fontWeight:700, color:'#555', marginBottom:12, letterSpacing:'0.08em' }}>NAVIGATION</div>
            {sideItems.map(item => (
              <div key={item} className={`admin-si${sideTab===item?' active':''}`} onClick={() => setSideTab(item)}>{item}</div>
            ))}
          </div>

          {/* Main */}
          <div style={{ display:'flex', flexDirection:'column', gap:24 }}>
            {/* KPIs */}
            <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:14 }}>
              {[
                { label:'Tổng người dùng', val: dl?'...':(dash.totalUsers||0).toLocaleString(), sub:`+${dash.newUsersToday||0} hôm nay`, icon:'👥', color:C.accent },
                { label:'Tin đang đăng',   val: dl?'...':(dash.activeListings||0).toLocaleString(), sub:`${dash.pendingListings||0} chờ duyệt`, icon:'📦', color:C.green },
                { label:'Tổng doanh thu',  val: dl?'...':dash.totalRevenue ? `₫ ${(dash.totalRevenue/1000000).toFixed(1)}M` : '₫ —', sub:'+8% tháng này', icon:'💰', color:C.amber },
                { label:'Báo cáo vi phạm', val: dl?'...':(dash.flaggedReports||0), sub:`${dash.highPriorityReports||0} ưu tiên cao`, icon:'🚨', color:'#C8553D' },
              ].map(s => (
                <div key={s.label} className="kpi" style={{ borderTop:`3px solid ${s.color}` }}>
                  <div style={{ fontSize:26, marginBottom:6 }}>{s.icon}</div>
                  <div className="serif" style={{ fontSize:22, fontWeight:700, color:s.color }}>{s.val}</div>
                  <div style={{ fontSize:12, fontWeight:600, marginTop:2 }}>{s.label}</div>
                  <div style={{ fontSize:11, color:C.muted, marginTop:2 }}>{s.sub}</div>
                </div>
              ))}
            </div>

            {/* Users table */}
            <div className="card" style={{ borderRadius:12, overflow:'hidden' }}>
              <div style={{ padding:'14px 18px', borderBottom:`1px solid ${C.border}`, display:'flex', justifyContent:'space-between', alignItems:'center' }}>
                <h3>👥 Quản lý người dùng</h3>
                <div style={{ display:'flex', gap:8 }}>
                  <input className="input" placeholder="🔍 Tìm kiếm..." style={{ width:200, fontSize:12 }} />
                  <button className="btn btn-secondary btn-sm">Lọc ▾</button>
                </div>
              </div>
              {ul && <Spinner />}
              {!ul && usersList.length === 0 && <Empty icon="👥" text="Không có dữ liệu" />}
              {usersList.length > 0 && (
                <table className="table">
                  <thead>
                    <tr>{['Người dùng','Email','Vai trò','Trạng thái','Ngày tham gia','Thao tác'].map(h => <th key={h}>{h}</th>)}</tr>
                  </thead>
                  <tbody>
                    {usersList.map(u => (
                      <tr key={u.id}>
                        <td>
                          <div style={{ display:'flex', gap:8, alignItems:'center' }}>
                            <div style={{ width:28, height:28, borderRadius:'50%', background:C.surface, display:'flex', alignItems:'center', justifyContent:'center', fontSize:14 }}>👤</div>
                            <span style={{ fontWeight:600 }}>{u.fullName}</span>
                          </div>
                        </td>
                        <td style={{ color:C.muted, fontSize:12 }}>{u.email}</td>
                        <td><span className="chip">{u.role?.replace('ROLE_','')}</span></td>
                        <td>
                          <span className={u.enabled ? 'b-active' : 'b-banned'}>
                            {u.enabled ? 'Hoạt động' : 'Bị vô hiệu'}
                          </span>
                        </td>
                        <td style={{ color:C.muted, fontSize:12 }}>{u.createdAt ? new Date(u.createdAt).toLocaleDateString('vi-VN') : '—'}</td>
                        <td>
                          <div style={{ display:'flex', gap:4 }}>
                            <button className="btn btn-ghost btn-sm">Xem</button>
                            <button className="btn btn-danger btn-sm"
                              onClick={() => toggleUser.mutate(u.id)}>
                              {u.enabled ? 'Khoá' : 'Mở khoá'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Listing moderation */}
            <div className="card" style={{ padding:18, borderRadius:12 }}>
              <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
                <h3>📦 Kiểm duyệt tin đăng</h3>
              </div>
              <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:12, marginBottom:20 }}>
                {[['Chờ duyệt',dash.pendingListings||0,C.accent],['Đã duyệt',dash.activeListings||0,C.green],['Bị gắn cờ',dash.flaggedListings||0,'#b88a00'],['Đã xoá',dash.removedListings||0,'#999']].map(([s,n,c]) => (
                  <div key={s} style={{ background:'#fff', border:`1.5px solid ${C.border}`, borderRadius:10, padding:16, borderTop:`3px solid ${c}` }}>
                    <div className="serif" style={{ fontSize:28, fontWeight:700, color:c }}>{n}</div>
                    <div style={{ fontSize:12, color:C.muted, marginTop:4 }}>{s}</div>
                  </div>
                ))}
              </div>
              {lll && <Spinner />}
              {listingsList.length > 0 && (
                <table className="table">
                  <thead>
                    <tr>{['Tiêu đề','Người bán','Giá','Trạng thái','Thao tác'].map(h => <th key={h}>{h}</th>)}</tr>
                  </thead>
                  <tbody>
                    {listingsList.map(l => (
                      <tr key={l.id}>
                        <td style={{ fontWeight:600, maxWidth:200, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{l.title}</td>
                        <td style={{ color:C.muted }}>{l.sellerName || '—'}</td>
                        <td className="serif" style={{ color:C.accent, fontWeight:700 }}>₫ {l.price?.toLocaleString()}</td>
                        <td>{statusBadge(l.status)}</td>
                        <td>
                          <div style={{ display:'flex', gap:4 }}>
                            {l.status === 'PENDING' && (
                              <button className="btn btn-green btn-sm" onClick={() => updateListing.mutate({ id:l.id, status:'ACTIVE' })}>Duyệt</button>
                            )}
                            {l.status === 'ACTIVE' && (
                              <button className="btn btn-danger btn-sm" onClick={() => updateListing.mutate({ id:l.id, status:'REMOVED' })}>Gỡ</button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── MAIN APP ─────────────────────────────────────────────────────────────────

const PAGES = ['Homepage','Category Listing','Product Detail','Create Listing','Chat','Order','User Profile','Seller Dashboard','Admin Dashboard','Login']

export default function FlowerMarketApp() {
  const [active, setActive]   = useState('Homepage')
  const [pageParams, setPageParams] = useState({})

  const navigate = (page, params = {}) => {
    setActive(page)
    setPageParams(params)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const props = { onNav: navigate, params: pageParams }
  const pages = {
    'Homepage':          <HomePage {...props} />,
    'Category Listing':  <CategoryPage {...props} />,
    'Product Detail':    <ProductDetailPage {...props} />,
    'Create Listing':    <CreateListingPage {...props} />,
    'Chat':              <ChatPage {...props} />,
    'Order':             <OrderPage {...props} />,
    'User Profile':      <UserProfilePage {...props} />,
    'Seller Dashboard':  <SellerDashboard {...props} />,
    'Admin Dashboard':   <AdminDashboard {...props} />,
    'Login':             <LoginPage onNav={navigate} />,
  }

  return (
    <QueryClientProvider client={qc}>
      <style>{CSS}</style>
      <Toaster position="top-right" toastOptions={{ duration:3500, style:{ fontFamily:"'DM Sans',sans-serif", fontSize:13 } }} />

      <div style={{ fontFamily:"'DM Sans',sans-serif", background:C.bg, minHeight:'100vh' }}>
        {/* Top header */}
        <div style={{ background:C.dark, padding:'8px 24px', display:'flex', alignItems:'center', gap:16, position:'sticky', top:0, zIndex:300 }}>
          <span className="serif" style={{ color:'#fff', fontSize:14, fontWeight:700, cursor:'pointer' }} onClick={() => navigate('Homepage')}>🌸 FlowerMarket</span>
          <span style={{ fontSize:10, color:C.accent, letterSpacing:'0.1em', fontWeight:700 }}>+ Spring Boot 3</span>
          <div style={{ flex:1 }} />
          <span style={{ fontSize:10, color:'#555' }}>Backend: localhost:8080/api</span>
        </div>

        {/* Tab nav (ẩn khi đang ở Login) */}
        {active !== 'Login' && (
          <div className="tab-nav">
            {PAGES.filter(p => p !== 'Login').map(page => (
              <button key={page} className={`tab-btn${active===page?' active':''}`} onClick={() => navigate(page)}>
                {page}
              </button>
            ))}
          </div>
        )}

        {/* Page content */}
        <div key={active + JSON.stringify(pageParams)}>
          {pages[active]}
        </div>

        {/* Footer */}
        {active !== 'Login' && (
          <div style={{ background:C.dark, padding:'20px 24px', marginTop:48 }}>
            <div style={{ maxWidth:1200, margin:'0 auto', display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:24 }}>
              {[
                { title:'🌸 FlowerMarket', items:['Về chúng tôi','Blog','Tuyển dụng','Điều khoản'] },
                { title:'Người mua', items:['Cách mua hàng','Chính sách hoàn tiền','Hỗ trợ đơn hàng','Theo dõi giao hàng'] },
                { title:'Người bán', items:['Bắt đầu bán','Phí dịch vụ','Trung tâm bán hàng','Quy định đăng tin'] },
                { title:'Kết nối', items:['Facebook','Instagram','Zalo','Hotline: 1800-1234'] },
              ].map(col => (
                <div key={col.title}>
                  <div style={{ color:'#fff', fontWeight:700, fontSize:13, marginBottom:12, fontFamily:"'Playfair Display',serif" }}>{col.title}</div>
                  {col.items.map(item => (
                    <div key={item} style={{ color:'#666', fontSize:12, marginBottom:6, cursor:'pointer' }}>{item}</div>
                  ))}
                </div>
              ))}
            </div>
            <div style={{ borderTop:'1px solid #333', marginTop:24, paddingTop:16, textAlign:'center' }}>
              <span className="serif" style={{ color:'#444', fontSize:12 }}>
                © 2025 FlowerMarket · React 18 + Spring Boot 3 · JWT Auth · STOMP WebSocket · MySQL + Redis + RabbitMQ
              </span>
            </div>
          </div>
        )}
      </div>
    </QueryClientProvider>
  )
}
