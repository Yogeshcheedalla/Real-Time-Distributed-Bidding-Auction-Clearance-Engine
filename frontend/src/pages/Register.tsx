import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { GoogleButton, GoogleDivider } from './Login'

export default function Register() {
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '', role: 'USER', terms: false })
  const [err, setErr] = useState('')
  const { register } = useAuth()
  const navigate = useNavigate()
  const up = (k: string, v: string | boolean) => setF(s => ({ ...s, [k]: v }))

  const go = async (e: React.FormEvent) => {
    e.preventDefault(); setErr('')
    if (f.password !== f.confirm) return setErr('Passwords do not match')
    if (!f.terms) return setErr('Please accept the terms to continue')
    try { const u = await register(f); navigate(u.roles.includes('SELLER') ? '/seller' : '/auctions') }
    catch (ex) { setErr((ex as Error).message) }
  }
  const field = 'w-full mt-1 bg-slate-800/60 border border-line rounded-xl px-3 py-2.5 outline-none focus:border-acc'

  return (
    <main className="max-w-md mx-auto px-5 py-16">
      <div className="text-center font-extrabold tracking-[.2em] text-slate-400 text-xs mb-2">BIDVELOCITY</div>
      <div className="rounded-2xl border border-line bg-panel p-7">
        <h1 className="text-xl font-extrabold m-0">Create your account</h1>
        <form onSubmit={go} className="space-y-3 mt-4">
          <div className="grid grid-cols-2 gap-3">
            <div><label className="text-xs font-bold text-slate-400">First name</label><input className={field} value={f.firstName} onChange={e => up('firstName', e.target.value)} required minLength={2} /></div>
            <div><label className="text-xs font-bold text-slate-400">Last name</label><input className={field} value={f.lastName} onChange={e => up('lastName', e.target.value)} required /></div>
          </div>
          <div><label className="text-xs font-bold text-slate-400">Email</label><input className={field} type="email" value={f.email} onChange={e => up('email', e.target.value)} required /></div>
          <div><label className="text-xs font-bold text-slate-400">Password (8+ characters)</label><input className={field} type="password" value={f.password} onChange={e => up('password', e.target.value)} required minLength={8} /></div>
          <div><label className="text-xs font-bold text-slate-400">Confirm password</label><input className={field} type="password" value={f.confirm} onChange={e => up('confirm', e.target.value)} required /></div>
          <div><label className="text-xs font-bold text-slate-400">Account type</label>
            <select className={field} value={f.role} onChange={e => up('role', e.target.value)}><option value="USER">Bid &amp; buy</option><option value="SELLER">Sell auctions</option></select></div>
          <label className="flex items-center gap-2 text-xs text-slate-400"><input type="checkbox" checked={f.terms} onChange={e => up('terms', e.target.checked)} /> I accept the auction terms &amp; fair-bidding policy</label>
          {err && <p className="text-rose-300 text-sm m-0">⛔ {err}</p>}
          <button className="w-full py-2.5 rounded-xl font-extrabold bg-gradient-to-r from-acc to-acc2 text-ink">CREATE ACCOUNT</button>
        </form>
        <GoogleDivider />
        <GoogleButton />
        <p className="text-sm text-slate-500 text-center mt-5 mb-0">Already registered? <Link to="/login" className="text-acc font-bold">Sign in</Link></p>
      </div>
    </main>
  )
}
