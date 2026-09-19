import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, money } from '../api'
import { useAuth } from '../auth'
import type { Payment } from '../types'

const badge = (s: Payment['status']) => {
  const map: Record<string, string> = {
    SUCCESS: 'text-good border-emerald-400/40 bg-emerald-400/10',
    PENDING: 'text-acc border-cyan-400/40 bg-cyan-400/10',
    PROCESSING: 'text-amber-300 border-amber-400/40 bg-amber-400/10',
    FAILED: 'text-rose-300 border-rose-400/40 bg-rose-400/10',
    EXPIRED: 'text-slate-400 border-slate-500/40', REFUNDED: 'text-slate-400 border-slate-500/40',
  }
  return <span className={`px-2 py-0.5 rounded-full text-[11px] font-bold border ${map[s]}`}>{s}</span>
}

export default function Payments() {
  const user = useAuth(s => s.user)
  const qc = useQueryClient()
  const [busy, setBusy] = useState<number | null>(null)
  const { data, error } = useQuery({ queryKey: ['mypayments'], queryFn: api.myPayments, enabled: !!user })

  const pay = async (p: Payment) => {
    setBusy(p.id)
    try { await api.processPayment(p.id); qc.invalidateQueries({ queryKey: ['mypayments'] }) }
    finally { setBusy(null) }
  }

  if (!user) return <main className="max-w-5xl mx-auto p-16 text-center"><Link to="/login" className="text-acc font-bold">Sign in →</Link></main>

  return (
    <main className="max-w-5xl mx-auto px-5 py-8">
      <h1 className="text-2xl font-extrabold mb-1">Payments</h1>
      <p className="text-sm text-slate-500 mt-0 mb-6">Invoices are opened by the <span className="mono">WinnerDeclared</span> event — never a synchronous call chain from the bid path.</p>
      {error && <p className="text-rose-300 text-sm">payment-service unreachable — scripts\start-payment.bat</p>}
      {(data ?? []).length ? (
        <div className="space-y-3">{(data ?? []).map(p => (
          <div key={p.id} className="flex flex-wrap items-center gap-3 rounded-2xl border border-line bg-panel p-4">
            <div className="flex-1 min-w-40">
              <Link to={`/auction/${p.auctionId}`} className="font-bold hover:text-acc">{p.auctionTitle || `Auction #${p.auctionId}`}</Link>
              <div className="text-xs text-slate-500">{p.provider} · {p.attempts} attempt(s){p.failureReason ? ' · last: ' + p.failureReason : ''}</div>
            </div>
            <div className="font-extrabold tabular-nums">{money(p.amount)}</div>
            {badge(p.status)}
            {['PENDING', 'FAILED'].includes(p.status) && (
              <button disabled={busy === p.id} onClick={() => pay(p)} className="px-4 py-1.5 rounded-xl font-bold text-sm bg-gradient-to-r from-acc to-acc2 text-ink disabled:opacity-50">
                {busy === p.id ? 'Processing…' : p.status === 'FAILED' ? 'Retry payment' : 'Pay now'}
              </button>
            )}
          </div>
        ))}</div>
      ) : <div className="text-center py-16 text-slate-500 rounded-2xl border border-dashed border-line">No payment history. Invoices appear here when you win an auction.</div>}
    </main>
  )
}
