import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, money } from '../api'
import { useAuth } from '../auth'

async function jget<T>(path: string): Promise<T> {
  const r = await fetch(path, { headers: { Authorization: 'Bearer ' + (localStorage.getItem('bv.token') || '') } })
  const body = await r.json().catch(() => null)
  if (!r.ok) throw new Error((body && body.message) || r.statusText)
  return body as T
}
async function jsend<T>(method: string, path: string, body?: unknown): Promise<T> {
  const r = await fetch(path, { method, headers: { Authorization: 'Bearer ' + (localStorage.getItem('bv.token') || ''), 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined })
  const out = await r.json().catch(() => null)
  if (!r.ok) throw new Error((out && out.message) || r.statusText)
  return out as T
}

interface AdminUser { id: number; email: string; firstName: string; lastName: string; status: string; roles: string[] }
interface AdminAuction { id: number; title: string; status: string; currentPrice: number; bidCount: number; sellerId: number }
interface AdminPayment { id: number; auctionId: number; amount: number; status: string; winnerId: number }

export default function Admin() {
  const user = useAuth(s => s.user)
  const qc = useQueryClient()
  const [tab, setTab] = useState<'users' | 'auctions' | 'payments'>('users')
  const [err, setErr] = useState('')

  const users = useQuery({ queryKey: ['admin-users'], queryFn: () => jget<AdminUser[]>('/api/admin/users'), enabled: user?.roles.includes('ADMIN') })
  const auctions = useQuery({ queryKey: ['admin-auctions'], queryFn: async () => { const p = await jget<{ content: AdminAuction[] } | AdminAuction[]>('/api/auctions?size=50'); return Array.isArray(p) ? p : p.content }, enabled: user?.roles.includes('ADMIN') })
  const payments = useQuery({ queryKey: ['admin-payments'], queryFn: () => jget<AdminPayment[]>('/api/payments'), enabled: user?.roles.includes('ADMIN') })

  const act = useMutation({
    mutationFn: async (a: { kind: 'suspend' | 'activate' | 'close'; id: number }) => {
      if (a.kind === 'close') return jsend('POST', '/api/auctions/' + a.id + '/close')
      return jsend('PATCH', '/api/admin/users/' + a.id + '/status', { status: a.kind === 'suspend' ? 'SUSPENDED' : 'ACTIVE' })
    },
    onSuccess: () => { qc.invalidateQueries(); setErr('') },
    onError: (e: Error) => setErr(e.message),
  })

  if (!user) return <main className="max-w-6xl mx-auto p-16 text-center"><a href="#/login" className="text-acc font-bold">Sign in as admin →</a></main>
  if (!user.roles.includes('ADMIN')) return <main className="max-w-6xl mx-auto p-16 text-center text-slate-400">Admin access required.</main>

  const th = 'p-3 text-left text-[11px] uppercase tracking-wider text-slate-500'
  const td = 'p-3 border-t border-line/60 text-sm'
  const chip = (s: string) => <span className={`px-2 py-0.5 rounded-full text-[11px] font-bold border ${s === 'ACTIVE' || s === 'SOLD' || s === 'SUCCESS' ? 'text-good border-emerald-400/40' : s === 'SUSPENDED' || s === 'FAILED' || s === 'CANCELLED' ? 'text-rose-300 border-rose-400/40' : 'text-slate-400 border-slate-600'}`}>{s}</span>

  return (
    <main className="max-w-6xl mx-auto px-5 py-8">
      <h1 className="text-2xl font-extrabold mb-4">Admin console</h1>
      {err && <p className="text-rose-300 text-sm">⛔ {err}</p>}
      <div className="flex gap-1 border-b border-line mb-4">
        {(['users', 'auctions', 'payments'] as const).map(t =>
          <button key={t} onClick={() => setTab(t)} className={`px-4 py-2 text-sm font-bold border-b-2 -mb-px ${tab === t ? 'border-acc text-white' : 'border-transparent text-slate-500'}`}>{t}</button>)}
      </div>
      <div className="rounded-2xl border border-line bg-panel overflow-x-auto">
        {tab === 'users' && <table className="w-full"><thead><tr className={th.replace('p-3', 'p-3') + ' bg-slate-800/40'}><th className={th}>User</th><th className={th}>Email</th><th className={th}>Roles</th><th className={th}>Status</th><th className={th}></th></tr></thead>
          <tbody>{(users.data ?? []).map(u => <tr key={u.id}><td className={td + ' font-bold'}>{u.firstName} {u.lastName}</td><td className={td + ' text-slate-400'}>{u.email}</td><td className={td}>{u.roles.join(',')}</td><td className={td}>{chip(u.status)}</td>
            <td className={td + ' text-right'}>{u.status === 'ACTIVE'
              ? <button className="text-rose-300 text-xs font-bold" onClick={() => act.mutate({ kind: 'suspend', id: u.id })}>suspend</button>
              : <button className="text-good text-xs font-bold" onClick={() => act.mutate({ kind: 'activate', id: u.id })}>activate</button>}</td></tr>)}</tbody></table>}
        {tab === 'auctions' && <table className="w-full"><thead><tr className="bg-slate-800/40"><th className={th}>Auction</th><th className={th}>Status</th><th className={th}>Price</th><th className={th}>Bids</th><th className={th}></th></tr></thead>
          <tbody>{(auctions.data ?? []).map(a => <tr key={a.id}><td className={td + ' font-bold'}><a href={`#/auction/${a.id}`}>{a.title}</a></td><td className={td}>{chip(a.status)}</td><td className={td}>{money(a.currentPrice)}</td><td className={td}>{a.bidCount}</td>
            <td className={td + ' text-right'}>{['LIVE', 'ENDING', 'SCHEDULED'].includes(a.status) && <button className="text-amber-300 text-xs font-bold" onClick={() => act.mutate({ kind: 'close', id: a.id })}>close now</button>}</td></tr>)}</tbody></table>}
        {tab === 'payments' && <table className="w-full"><thead><tr className="bg-slate-800/40"><th className={th}>Payment</th><th className={th}>Auction</th><th className={th}>Winner</th><th className={th}>Amount</th><th className={th}>Status</th></tr></thead>
          <tbody>{(payments.data ?? []).map(p => <tr key={p.id}><td className={td}>#{p.id}</td><td className={td}>#{p.auctionId}</td><td className={td}>#{p.winnerId}</td><td className={td + ' font-bold'}>{money(p.amount)}</td><td className={td}>{chip(p.status)}</td></tr>)}</tbody></table>}
      </div>
      <p className="text-[11px] text-slate-600 mt-3">Service health: <a className="text-acc" href="http://localhost:8761" target="_blank">Eureka :8761</a> · <a className="text-acc" href="http://localhost:8080/actuator/health" target="_blank">Gateway</a> · <a className="text-acc" href="http://localhost:8081/actuator/health" target="_blank">Auth</a> · <a className="text-acc" href="http://localhost:8082/actuator/health" target="_blank">Auction</a> · <a className="text-acc" href="http://localhost:8083/actuator/health" target="_blank">Bidding</a> · <a className="text-acc" href="http://localhost:8084/actuator/health" target="_blank">Payment</a></p>
    </main>
  )
}
