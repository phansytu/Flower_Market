import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from '@tanstack/react-query'
import { getListings, getListingById, getMyListings, createListing, updateListing, deleteListing, searchListings } from '../api/listings'
import { orders }        from '../api/orders'
import { reviews }       from '../api/reviews'
import { payments }      from '../api/payments'
import { users }         from '../api/users'
import { notifications } from '../api/notifications'
import { chat }          from '../api/chat'
import { admin }         from '../api/admin'

// ─── LISTINGS ───────────────────────────────────────────────────────────────

export const useListings = (params) =>
  useQuery({
    queryKey: ['listings', params],
    queryFn:  () => getListings(params).then(r => r.data),
    staleTime: 30_000,
  })

export const useListingById = (id) =>
  useQuery({
    queryKey: ['listing', id],
    queryFn:  () => getListingById(id).then(r => r.data),
    enabled:  !!id,
  })

export const useMyListings = (params) =>
  useQuery({
    queryKey: ['myListings', params],
    queryFn:  () => getMyListings(params).then(r => r.data),
  })

export const useSearchListings = (params) =>
  useQuery({
    queryKey: ['search', params],
    queryFn:  () => searchListings(params).then(r => r.data),
    enabled:  !!params?.keyword,
  })

export const useCreateListing = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: createListing,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['listings'] })
      qc.invalidateQueries({ queryKey: ['myListings'] })
    },
  })
}

export const useUpdateListing = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, formData }) => updateListing(id, formData),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ['listing', id] })
      qc.invalidateQueries({ queryKey: ['myListings'] })
    },
  })
}

export const useDeleteListing = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: deleteListing,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['myListings'] }),
  })
}

// ─── ORDERS ─────────────────────────────────────────────────────────────────

export const useMyOrders = (params) =>
  useQuery({
    queryKey: ['myOrders', params],
    queryFn:  () => orders.getMy(params).then(r => r.data),
  })

export const useOrderById = (id) =>
  useQuery({
    queryKey: ['order', id],
    queryFn:  () => orders.getById(id).then(r => r.data),
    enabled:  !!id,
  })

export const useCreateOrder = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: orders.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['myOrders'] }),
  })
}

export const useCancelOrder = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: orders.cancel,
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['order', id] }),
  })
}

export const useUpdateOrderStatus = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }) => orders.updateStatus(id, status),
    onSuccess: (_, { id }) => qc.invalidateQueries({ queryKey: ['order', id] }),
  })
}

// ─── REVIEWS ────────────────────────────────────────────────────────────────

export const useListingReviews = (listingId, params) =>
  useQuery({
    queryKey: ['reviews', 'listing', listingId, params],
    queryFn:  () => reviews.getByListing(listingId, params).then(r => r.data),
    enabled:  !!listingId,
  })

export const useListingRatingStats = (listingId) =>
  useQuery({
    queryKey: ['ratingStats', listingId],
    queryFn:  () => reviews.getListingStats(listingId).then(r => r.data),
    enabled:  !!listingId,
  })

export const useCreateReview = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: reviews.create,
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['reviews', 'listing', vars.listingId] })
      qc.invalidateQueries({ queryKey: ['ratingStats', vars.listingId] })
    },
  })
}

// ─── PAYMENTS ───────────────────────────────────────────────────────────────

export const useProcessPayment = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: payments.process,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['myOrders'] }),
  })
}

export const usePaymentByOrder = (orderId) =>
  useQuery({
    queryKey: ['payment', 'order', orderId],
    queryFn:  () => payments.getByOrder(orderId).then(r => r.data),
    enabled:  !!orderId,
  })

// ─── USERS ──────────────────────────────────────────────────────────────────

export const useMe = () =>
  useQuery({
    queryKey: ['me'],
    queryFn:  () => users.getMe().then(r => r.data),
  })

export const useUpdateProfile = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: users.updateProfile,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}

export const useAddAddress = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: users.addAddress,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}

// ─── NOTIFICATIONS ──────────────────────────────────────────────────────────

export const useNotifications = (params) =>
  useQuery({
    queryKey: ['notifications', params],
    queryFn:  () => notifications.getAll(params).then(r => r.data),
    refetchInterval: 30_000,  // polling mỗi 30s
  })

export const useUnreadCount = () =>
  useQuery({
    queryKey: ['notifCount'],
    queryFn:  () => notifications.getUnreadCount().then(r => r.data),
    refetchInterval: 20_000,
  })

export const useMarkAllRead = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: notifications.markAllRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['notifCount'] })
    },
  })
}

// ─── CHAT ───────────────────────────────────────────────────────────────────

export const useConversations = () =>
  useQuery({
    queryKey: ['conversations'],
    queryFn:  () => chat.getConversations().then(r => r.data),
    refetchInterval: 10_000,
  })

export const useMessages = (convId, params) =>
  useQuery({
    queryKey: ['messages', convId, params],
    queryFn:  () => chat.getMessages(convId, params).then(r => r.data),
    enabled:  !!convId,
  })

// ─── ADMIN ──────────────────────────────────────────────────────────────────

export const useAdminDashboard = () =>
  useQuery({
    queryKey: ['adminDashboard'],
    queryFn:  () => admin.getDashboard().then(r => r.data),
    staleTime: 60_000,
  })

export const useAdminUsers = (params) =>
  useQuery({
    queryKey: ['adminUsers', params],
    queryFn:  () => admin.getUsers(params).then(r => r.data),
  })

export const useAdminListings = (params) =>
  useQuery({
    queryKey: ['adminListings', params],
    queryFn:  () => admin.getListings(params).then(r => r.data),
  })

export const useAdminOrders = (params) =>
  useQuery({
    queryKey: ['adminOrders', params],
    queryFn:  () => admin.getOrders(params).then(r => r.data),
  })

export const useBanUser = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }) => admin.banUser(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['adminUsers'] }),
  })
}

export const useAdminUpdateListing = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }) => admin.updateListingStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['adminListings'] }),
  })
}

export const useAdminToggleUser = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => admin.toggleUser(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['adminUsers'] }),
  })
}

// Seller dashboard dùng lại hooks trên (myListings + myOrders)
export const useSellerStats = () => {
  const listings = useMyListings({ page: 0, size: 100 })
  const ordersList = useMyOrders({ page: 0, size: 100 })
  return { listings, ordersList }
}
