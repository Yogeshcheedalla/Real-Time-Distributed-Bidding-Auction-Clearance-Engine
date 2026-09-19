import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { AuctionCard } from './Landing'

export default function Auctions() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? 0)
  const [q, setQ] = useState(params.get('q') ?? '')
  const query = { q: q || undefined, category: params.get('category') || undefined, status: params.get('status') || undefined, sort: params.get('sort') || 'ending', page, size: 12 }
  const { data } = useQuery({ queryKey: ['auctions', query], queryFn: () => api.listAuctions(query as Record<string, string | number>) })
  const rows = data?.content ?? []

  const set = (k: string, v?: string) => { const p = new URLSearchParams(params); v ? p.set(k, v) : p.delete(k); p.delete('page'); setParams(p) }

  return (
    <main className="max-w-6xl mx-auto px-5 pt-8 pb-16">
      <h1 className="text-3xl font-extrabold mb-1">Marketplace</h1>
      <p className="text-slate-500 text-sm mb-5">{data?.totalElements ?? 0} auctions</p>
      <div className="flex flex-wrap gap-2 mb-6">
        <input value={q} onChange={e => setQ(e.target.value)} onKeyDown={e => e.key === 'Enter' && set('q', q)} placeholder="Search title, description…" className="flex-1 min-w-52 bg-panel border border-line rounded-xl px-3 py-2 text-sm outline-none focus:border-acc" />
        <select className="bg-panel border border-line rounded-xl px-3 py-2 text-sm" value={params.get('status') ?? ''} onChange={e => set('status', e.target.value || undefined)}>
          <option value="">All statuses</option><option value="OPEN">Open for bidding</option><option value="SCHEDULED">Upcoming</option><option value="SOLD">Sold</option><option value="UNSOLD">Unsold</option>
        </select>
        <select className="bg-panel border border-line rounded-xl px-3 py-2 text-sm" value={params.get('sort') ?? 'ending'} onChange={e => set('sort', e.target.value)}>
          <option value="ending">Ending soon</option><option value="newest">Newly listed</option><option value="bids">Most bids</option><option value="priceAsc">Price ↑</option><option value="priceDesc">Price ↓</option>
        </select>
      </div>
      {rows.length ? <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">{rows.map(a => <AuctionCard key={a.id} a={a} />)}</div>
        : <div className="text-center py-20 text-slate-500 rounded-2xl border border-dashed border-line">🔍 No auctions match.<div className="text-xs mt-1 opacity-70">Start the backend (scripts\start-*.bat) and create one from the seller studio.</div></div>}
      {(data?.totalPages ?? 0) > 1 && (
        <div className="flex gap-1.5 justify-center mt-8">
          <button disabled={page === 0} onClick={() => set('page', String(page - 1))} className="px-3 py-1.5 rounded-lg border border-line text-sm disabled:opacity-30">←</button>
          <span className="px-3 py-1.5 text-sm text-slate-400">{page + 1} / {data!.totalPages}</span>
          <button disabled={page + 1 >= data!.totalPages} onClick={() => set('page', String(page + 1))} className="px-3 py-1.5 rounded-lg border border-line text-sm disabled:opacity-30">→</button>
        </div>
      )}
    </main>
  )
}
