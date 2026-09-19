import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, money } from '../api'
import { useAuth } from '../auth'
import type { Auction } from '../types'

export default function MyBids() {
  const user = useAuth(s => s.user)
  const { data: bids } = useQuery({ queryKey: ['mybids'], queryFn: api.myBids, enabled: !!user })
  const { data: live } = useQuery({ queryKey: ['liveall'], queryFn: () => api.listAuctions({ size: 50 }) })
  const byId = new Map<number, Auction>((live?.content ?? []).map(a => [a.id, a]))

  return (
    <main className="max-w-5xl mx-auto px-5 py-8">
      <h1 className="text-2xl font-extrabold mb-5">My bids</h1>
      {!(bids ?? []).length ? <div className="text-center py-16 text-slate-500 rounded-2xl border border-dashed border-line">No bids yet. <Link to="/auctions" className="text-acc font-bold">Hit the floor →</Link></div> : (
        <div className="rounded-2xl border border-line bg-panel overflow-x-auto">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[11px] uppercase tracking-wider text-slate-500 border-b border-line">
              <th className="p-3.5">Auction</th><th>Amount</th><th>Status</th><th className="text-right pr-4">Time</th></tr></thead>
            <tbody>{(bids ?? []).map(b => {
              const a = byId.get(b.auctionId)
              const top = a?.highestBidId === b.id
              const state = a?.winnerId && a.winnerId === user?.id && a.status === 'SOLD'
                ? <span className="px-2 py-0.5 rounded-full text-[11px] font-bold bg-emerald-400/10 text-good border border-emerald-400/40">🏆 WON</span>
                : top && (a?.status === 'LIVE' || a?.status === 'ENDING')
                  ? <span className="px-2 py-0.5 rounded-full text-[11px] font-bold text-good">LEADING</span>
                  : a && ['LIVE', 'ENDING'].includes(a.status)
                    ? <span className="px-2 py-0.5 rounded-full text-[11px] font-bold text-amber-300">OUTBID</span>
                    : <span className="px-2 py-0.5 rounded-full text-[11px] font-bold text-slate-400">{a?.status ?? 'SETTLED'}</span>
              return (
                <tr key={b.id} className="border-b border-line/60">
                  <td className="p-3.5"><Link to={`/auction/${b.auctionId}`} className="font-bold hover:text-acc">{a ? `${a.emoji} ${a.title}` : `Auction #${b.auctionId}`}</Link></td>
                  <td className="font-extrabold tabular-nums">{money(b.amount)}</td>
                  <td>{state}</td>
                  <td className="text-right pr-4 text-slate-500">{new Date(b.at).toLocaleString()}</td>
                </tr>
              )
            })}</tbody>
          </table>
        </div>
      )}
    </main>
  )
}
