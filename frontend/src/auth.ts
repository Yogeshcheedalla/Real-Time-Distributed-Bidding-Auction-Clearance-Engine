import { create } from 'zustand'
import { api } from './api'
import type { User } from './types'

interface AuthState {
  user: User | null
  ready: boolean
  init: () => Promise<void>
  login: (email: string, password: string) => Promise<User>
  register: (b: { firstName: string; lastName: string; email: string; password: string; role: string }) => Promise<User>
  logout: () => void
}

export const useAuth = create<AuthState>((set) => ({
  user: null,
  ready: false,
  async init() {
    const t = localStorage.getItem('bv.token')
    if (t) { const u = await api.me(); set({ user: u, ready: true }) }
    else set({ ready: true })
  },
  async login(email, password) {
    const r = await api.login({ email, password })
    localStorage.setItem('bv.token', r.accessToken)
    set({ user: r.user })
    return r.user
  },
  async register(b) {
    const r = await api.register({ ...b, termsAccepted: true })
    localStorage.setItem('bv.token', r.accessToken)
    set({ user: r.user })
    return r.user
  },
  logout() { localStorage.removeItem('bv.token'); set({ user: null }) },
}))

/** Consumes the token hand-off after Google OAuth redirect (#/auth/callback#token=...). */
export function consumeOAuthToken(): boolean {
  const hash = window.location.hash
  const m = hash.match(/#token=([^&]+)/)
  if (m) {
    localStorage.setItem('bv.token', decodeURIComponent(m[1]))
    window.location.hash = '#/'
    return true
  }
  return false
}
