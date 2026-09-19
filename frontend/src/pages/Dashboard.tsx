import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, money } from '../api'
import { useAuth } from '../auth'
import { AuctionCard, Countdown } from './Landing'

export default function Dashboard() {
  const user = useAuth(s => s.user)
  const { data: bids } = useQuery({ queryKey: ['mybids'], queryFn: api.myBids, enabled: !!user })
  const { data: payments } = useQuery({ queryKey: ['mypayments'], queryFn: api.myPayments, enabled: !!user })
  const { data: myAuctions } = useQuery({ queryKey: ['myauctions'], queryFn: api.myAuctions, enabled: !!user && user.roles.includes('SELLER') })
  const { data: live } = useQuery({ queryKey: ['live'], queryFn: () => api.listAuctions({ status: 'OPEN', sort: 'ending', size: 8 }) })

  if (!user) return <main className="max-w-6xl mx-auto p-16 text-center"><p className="text-slate-400">Sign in to see your dashboard.</p><Link to="/login" className="text-acc font-bold">Sign in →</Link></main>

  const bidIds = new Set((bids ?? []).map(b => b.auctionId))
  const watching = (live?.content ?? []).filter(a => bidIds.has(a.id))
  const winning = (bids ?? []).length && watching.length ? '—' : '0'
  const endingSoon = watching.slice(0, 4)
  const pending = (payments ?? []).filter(p => p.status === 'PENDING' || p.status === 'FAILED')

  const hour = new Date().getHours()
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'

  return (
    <main className="max-w-6xl mx-auto px-5 py-8">
      <h1 className="text-2xl font-extrabold m-0">{greeting}, {user.firstName}</h1>
      <p className="text-slate-500 text-sm mt-1 mb-6">Here's what's happening with your auctions.</p>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {[
          ['My bids', (bids ?? []).length, '/bids'],
          ['Winning', String(winning), '/bids'],
          ['Active watch', String(endingSoon.length), '/auctions?status=OPEN'],
          ['Payments due', pending.length ? money(pending.reduce((s, p) => s + Number(p.amount), 0)) : 'none', '/payments'],
        ].map(([l, v, to]) => (
          <Link key={String(l)} to={String(to)} className="rounded-2xl border border-line bg-panel p-4 hover:border-slate-600">
            <div className="text-xs font-bold text-slate-500">{String(l).toUpperCase()}</div>
            <div className="text-2xl font-extrabold mt-1">{String(v)}</div>
          </Link>
        ))}
      </div>

      {myAuctions && myAuctions.length > 0 && (
        <section className="mb-8">
          <div className="flex justify-between items-center mb-3"><h2 className="text-lg font-extrabold m-0">My auctions</h2><Link to="/seller" className="text-sm text-acc font-bold">Seller studio →</Link></div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">{myAuctions.slice(0, 4).map(a => <AuctionCard key={a.id} a={a} />)}</div>
        </section>
      )}

      <section className="grid lg:grid-cols-2 gap-5">
        <div className="rounded-2xl border border-line bg-panel p-5">
          <h2 className="text-base font-extrabold m-0 mb-3">⚡ Ending soon (you're in the room)</h2>
          {endingSoon.length ? endingSoon.map(a => (
            <Link key={a.id} to={`/auction/${a.id}`} className="flex justify-between items-center py-2.5 border-b border-line/60 hover:bg-slate-800/40 px-2 rounded-lg">
              <div><div className="font-bold text-sm">{a.emoji} {a.title}</div><div className="text-xs text-slate-500">{a.bidCount} bids · current {money(a.currentPrice)}</div></div>
              <Countdown end={a.endTime} />
            </Link>
          )) : <p className="text-sm text-slate-500 m-0">Bid on an auction to track its countdown here.</p>}
        </div>
        <div className="rounded-2xl border border-line bg-panel p-5">
          <h2 className="text-base font-extrabold m-0 mb-3">🏷 Recent bids</h2>
          {(bids ?? []).slice(0, 6).map(b => (
            <div key={b.id} className="flex justify-between py-2 border-b border-line/60 text-sm">
              <Link to={`/auction/${b.auctionId}`} className="font-semibold hover:text-acc">Auction #{b.auctionId}</Link>
              <b>{money(b.amount)}</b>
            </div>
          ))}
          {!(bids ?? []).length && <p className="text-sm text-slate-500 m-0">No bids yet. <Link to="/auctions" className="text-acc">Find a live auction →</Link></p>}
        </div>
      </section>
    </main>
  )
}
