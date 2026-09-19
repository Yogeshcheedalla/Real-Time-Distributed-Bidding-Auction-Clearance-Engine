import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

export const GoogleButton = () => (
  <a href={api.googleLoginUrl()}
     className="w-full flex items-center justify-center gap-2.5 px-4 py-2.5 rounded-xl bg-white text-slate-800 font-bold text-sm hover:bg-slate-200 transition">
    <svg width="18" height="18" viewBox="0 0 48 48"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.3 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3l5.7-5.7C34.2 6.1 29.4 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.3-.4-3.5z"/><path fill="#FF3D00" d="M6.3 14.7l6.6 6.6C14.7 17.6 19 14 24 14c3.1 0 5.9 1.2 8 3l5.7-5.7C34.2 8.1 29.4 6 24 6c-7.6 0-14.1 4.3-17.7 8.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.3 0-9.7-3.3-11.3-8l-6.5 5C9.8 39.6 16.3 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.1-4.1 5.6l6.2 5.2C36.9 39.2 44 34 44 24c0-1.3-.1-2.3-.4-3.5z"/></svg>
    Continue with Google
  </a>
)

export const GoogleDivider = () => (
  <div className="flex items-center gap-3 my-4 text-[11px] font-bold text-slate-500">
    <span className="h-px flex-1 bg-line" />OR<span className="h-px flex-1 bg-line" />
  </div>
)

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const go = async (e: React.FormEvent) => {
    e.preventDefault(); setErr(''); setBusy(true)
    try { const u = await login(email, password); navigate(u.roles.includes('ADMIN') ? '/dashboard' : u.roles.includes('SELLER') ? '/seller' : '/auctions') }
    catch (ex) { setErr((ex as Error).message) }
    finally { setBusy(false) }
  }

  return (
    <main className="max-w-md mx-auto px-5 py-16">
      <div className="text-center font-extrabold tracking-[.2em] text-slate-400 text-xs mb-2">BIDVELOCITY</div>
      <div className="rounded-2xl border border-line bg-panel p-7">
        <h1 className="text-xl font-extrabold m-0">Welcome back</h1>
        <p className="text-sm text-slate-500 mt-1 mb-5">Sign in to continue</p>
        <form onSubmit={go} className="space-y-3">
          <div><label className="text-xs font-bold text-slate-400">Email</label>
            <input className="w-full mt-1 bg-slate-800/60 border border-line rounded-xl px-3 py-2.5 outline-none focus:border-acc" type="email" value={email} onChange={e => setEmail(e.target.value)} required /></div>
          <div><label className="text-xs font-bold text-slate-400">Password</label>
            <input className="w-full mt-1 bg-slate-800/60 border border-line rounded-xl px-3 py-2.5 outline-none focus:border-acc" type="password" value={password} onChange={e => setPassword(e.target.value)} required /></div>
          {err && <p className="text-rose-300 text-sm m-0">⛔ {err}</p>}
          <button disabled={busy} className="w-full py-2.5 rounded-xl font-extrabold bg-gradient-to-r from-acc to-acc2 text-ink disabled:opacity-50">{busy ? 'Signing in…' : 'SIGN IN'}</button>
        </form>
        <GoogleDivider />
        <GoogleButton />
        <p className="text-sm text-slate-500 text-center mt-5 mb-0">Don't have an account? <Link to="/register" className="text-acc font-bold">Sign up</Link></p>
        <p className="text-[11px] text-slate-600 text-center mt-3 mb-0">Demo (dev-only): seller@bidvelocity.io / Seller@123</p>
      </div>
    </main>
  )
}
