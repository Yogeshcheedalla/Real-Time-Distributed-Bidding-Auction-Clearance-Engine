import axios, { AxiosError } from 'axios'
import type { Auction, AuthResponse, Bid, Payment, Paged, User } from './types'

export const API_BASE = import.meta.env.VITE_API_BASE ?? ''   // '' → same-origin via Vite proxy → gateway :8080

const http = axios.create({ baseURL: API_BASE, headers: { 'Content-Type': 'application/json' } })
http.interceptors.request.use((cfg) => {
  const t = localStorage.getItem('bv.token')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

// Self-heal transient blips (backend/tunnel restart, 5xx). Reuses the SAME request
// config, so a retried bid keeps its idempotency key (never double-bids). 4xx are
// real (validation / intentional throttle) and are surfaced immediately.
type Retriable = { __retry?: number }
http.interceptors.response.use(r => r, async (error: AxiosError) => {
  const cfg = error.config as (AxiosError['config'] & Retriable) | undefined
  if (!cfg) return Promise.reject(error)
  const status = error.response?.status
  const transient = !error.response || (status != null && status >= 500)
  if (transient) {
    cfg.__retry = (cfg.__retry ?? 0) + 1
    if (cfg.__retry <= 3) {
      await new Promise(res => setTimeout(res, 350 * cfg.__retry!))
      return http(cfg)
    }
  }
  return Promise.reject(error)
})

/** Unwraps the unified error contract {timestamp,status,error,message,path,correlationId}. */
function err(e: unknown): Error {
  const ax = e as AxiosError<{ message?: string; error?: string }>
  return new Error(ax.response?.data?.message ?? ax.message ?? 'Request failed')
}

export const api = {
  register: (b: { firstName: string; lastName: string; email: string; password: string; role: string; termsAccepted: boolean }) =>
    http.post<AuthResponse>('/api/auth/register', b).then(r => r.data).catch(e => { throw err(e) }),
  login: (b: { email: string; password: string }) =>
    http.post<AuthResponse>('/api/auth/login', b).then(r => r.data).catch(e => { throw err(e) }),
  me: () => http.get<User>('/api/users/me').then(r => r.data).catch(() => null),
  becomeSeller: () => http.post<AuthResponse>('/api/users/me/seller').then(r => r.data).catch(e => { throw err(e) }),

  listAuctions: (p: Record<string, string | number>) =>
    http.get<Paged<Auction>>('/api/auctions', { params: p }).then(r => r.data),
  getAuction: (id: number) => http.get<Auction>(`/api/auctions/${id}`).then(r => r.data).catch(e => { throw err(e) }),
  createAuction: (b: object) => http.post<Auction>('/api/auctions', b).then(r => r.data).catch(e => { throw err(e) }),
  myAuctions: () => http.get<Auction[]>('/api/auctions/mine').then(r => r.data),
  cancelAuction: (id: number) => http.post(`/api/auctions/${id}/cancel`).catch(e => { throw err(e) }),

  placeBid: (b: { auctionId: number; amount: number; idempotencyKey: string }) =>
    http.post<{ bid: Bid; extended: boolean; newEndTime: string | null; minNext: number }>('/api/bids', b).then(r => r.data).catch(e => { throw err(e) }),
  bidsFor: (id: number) => http.get<Bid[]>(`/api/bids/auction/${id}`, { params: { size: 40 } }).then(r => r.data),
  myBids: () => http.get<Bid[]>('/api/bids/my').then(r => r.data),

  myPayments: () => http.get<Payment[]>('/api/payments/my').then(r => r.data),
  processPayment: (id: number) => http.post<Payment>(`/api/payments/${id}/process`).then(r => r.data).catch(e => { throw err(e) }),

  googleLoginUrl: () => `${API_BASE}/oauth2/authorization/google`,
}

export const money = (n: number | string | null | undefined) => '₹' + Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })
export const uuid = () => (crypto.randomUUID ? crypto.randomUUID() : 'k' + Date.now() + Math.random().toString(36).slice(2))
