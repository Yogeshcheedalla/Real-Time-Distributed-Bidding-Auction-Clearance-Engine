import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import { useAuth } from '../auth'
import { AuctionCard } from './Landing'

const toLocal = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)

export default function Seller() {
  const user = useAuth(s => s.user)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [f, setF] = useState({ title: '', description: '', category: 'Electronics', emoji: '📦', imageUrl: '', startingPrice: '1000', reservePrice: '', minIncrement: '100', startTime: toLocal(new Date(Date.now() + 5 * 60000)), endTime: toLocal(new Date(Date.now() + 10 * 60000)), antiSnipe: true, extensionWindowSecs: '30', maxExtensions: '3' })
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const { data: mine } = useQuery({ queryKey: ['myauctions'], queryFn: api.myAuctions, enabled: !!user?.roles.includes('SELLER') })

  if (!user) return <main className="max-w-5xl mx-auto p-16 text-center"><Link to="/login" className="text-acc font-bold">Sign in →</Link></main>
  if (!user.roles.includes('SELLER')) return <main className="max-w-5xl mx-auto p-16 text-center"><h2>Seller account required</h2><p className="text-slate-400">Register a seller account or sign in with the demo seller.</p><Link to="/register" className="text-acc font-bold">Open seller account →</Link></main>

  const up = (k: string, v: string | boolean) => setF(s => ({ ...s, [k]: v }))
  const field = 'w-full mt-1 bg-slate-800/60 border border-line rounded-xl px-3 py-2 outline-none focus:border-acc text-sm'
  const label = 'text-[11px] font-bold text-slate-500'

  const create = async (e: React.FormEvent) => {
    e.preventDefault(); setErr(''); setBusy(true)
    try {
      await api.createAuction({
        title: f.title, description: f.description, category: f.category, emoji: f.emoji, imageUrl: f.imageUrl.trim() || null,
        startingPrice: Number(f.startingPrice), minIncrement: Number(f.minIncrement),
        reservePrice: f.reservePrice ? Number(f.reservePrice) : null,
        startTime: new Date(f.startTime).toISOString(), endTime: new Date(f.endTime).toISOString(),
        antiSnipingEnabled: f.antiSnipe, extensionWindowSecs: Number(f.extensionWindowSecs), maxExtensions: Number(f.maxExtensions),
      })
      qc.invalidateQueries({ queryKey: ['myauctions'] }); setF(s => ({ ...s, title: '', description: '' }))
    } catch (ex) { setErr((ex as Error).message) }
    finally { setBusy(false) }
  }

  return (
    <main className="max-w-6xl mx-auto px-5 py-8">
      <h1 className="text-2xl font-extrabold mb-6">Seller studio</h1>
      <div className="grid lg:grid-cols-2 gap-5">
        <form onSubmit={create} className="rounded-2xl border border-line bg-panel p-5 space-y-2">
          <h2 className="font-extrabold m-0">Create an auction</h2>
          <p className="text-xs text-slate-500 m-0 mb-2">Scheduled → goes LIVE automatically at start time.</p>
          <div><label className={label}>Title</label><input className={field} value={f.title} onChange={e => up('title', e.target.value)} required minLength={4} placeholder="e.g. Fender Stratocaster '62 reissue" /></div>
          <div><label className={label}>Description</label><textarea className={field} rows={2} value={f.description} onChange={e => up('description', e.target.value)} /></div>
          <div className="grid grid-cols-3 gap-2">
            <div><label className={label}>Category</label><select className={field} value={f.category} onChange={e => up('category', e.target.value)}>{['Electronics', 'Collectibles', 'Art', 'Vehicles', 'Fashion', 'Home', 'Gadgets', 'Luxury'].map(c => <option key={c}>{c}</option>)}</select></div>
            <div><label className={label}>Emoji art</label><input className={field} value={f.emoji} onChange={e => up('emoji', e.target.value)} maxLength={4} /></div>
            <div><label className={label}>Image URL (optional)</label><input className={field} placeholder="https://…" value={f.imageUrl} onChange={e => up('imageUrl', e.target.value)} /></div>
            <div><label className={label}>Min increment ₹</label><input className={field} type="number" min="1" value={f.minIncrement} onChange={e => up('minIncrement', e.target.value)} required /></div>
            <div><label className={label}>Starting price ₹</label><input className={field} type="number" min="1" value={f.startingPrice} onChange={e => up('startingPrice', e.target.value)} required /></div>
            <div><label className={label}>Reserve ₹ (optional)</label><input className={field} type="number" min="0" value={f.reservePrice} onChange={e => up('reservePrice', e.target.value)} /></div>
            <div><label className={label}>Anti-snipe window s</label><input className={field} type="number" min="5" max="120" value={f.extensionWindowSecs} onChange={e => up('extensionWindowSecs', e.target.value)} /></div>
            <div><label className={label}>Start time</label><input className={field} type="datetime-local" value={f.startTime} onChange={e => up('startTime', e.target.value)} required /></div>
            <div><label className={label}>End time</label><input className={field} type="datetime-local" value={f.endTime} onChange={e => up('endTime', e.target.value)} required /></div>
            <div><label className={label}>Max extensions</label><input className={field} type="number" min="1" max="10" value={f.maxExtensions} onChange={e => up('maxExtensions', e.target.value)} /></div>
          </div>
          <label className="flex items-center gap-2 text-xs text-slate-400"><input type="checkbox" checked={f.antiSnipe} onChange={e => up('antiSnipe', e.target.checked)} /> Anti-sniping enabled</label>
          {err && <p className="text-rose-300 text-sm m-0">⛔ {err}</p>}
          <button disabled={busy} className="w-full py-2.5 rounded-xl font-extrabold bg-gradient-to-r from-acc to-acc2 text-ink disabled:opacity-50">{busy ? 'Scheduling…' : 'SCHEDULE AUCTION'}</button>
        </form>
        <div>
          <h2 className="font-extrabold">My auctions</h2>
          <div className="grid sm:grid-cols-2 gap-4">{(mine ?? []).slice(0, 8).map(a => <AuctionCard key={a.id} a={a} />)}</div>
          {!(mine ?? []).length && <div className="text-center py-14 text-slate-500 rounded-2xl border border-dashed border-line">No auctions yet — schedule your first.</div>}
        </div>
      </div>
    </main>
  )
}
