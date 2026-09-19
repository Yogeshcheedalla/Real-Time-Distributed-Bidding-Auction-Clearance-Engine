import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, money, uuid } from '../api'
import { useAuth } from '../auth'
import { useAuctionSocket } from '../useAuctionSocket'
import { Countdown, statusChip } from './Landing'
import type { Bid } from '../types'

export default function AuctionDetail() {
  const { id } = useParams()
  const aid = Number(id)
  const qc = useQueryClient()
  const user = useAuth(s => s.user)
  const [toast, setToast] = useState<{ msg: string; kind: string } | null>(null)
  const connected = useAuctionSocket(aid, (e) => {
    if (e.event?.startsWith('BID_ACCEPTED')) {
      setToast({ msg: `⚡ ${e.bidderName} bid ${money(e.amount)}${e.event === 'BID_ACCEPTED_EXTENDED' ? ' — clock extended! ⏱' : ''}`, kind: 'ok' })
      qc.invalidateQueries({ queryKey: ['auction', aid] }); qc.invalidateQueries({ queryKey: ['bids', aid] })
    }
    if (e.event === 'AUCTION_SEALED') { setToast({ msg: '🏁 Auction sealed — resolving winner', kind: 'ok' }); setTimeout(() => qc.invalidateQueries({ queryKey: ['auction', aid] }), 1500) }
  })
  useEffect(() => { if (toast) { const t = setTimeout(() => setToast(null), 4200); return () => clearTimeout(t) } }, [toast])

  const { data: a } = useQuery({ queryKey: ['auction', aid], queryFn: () => api.getAuction(aid), refetchInterval: connected ? 8000 : 2500 })
  const { data: bids } = useQuery({ queryKey: ['bids', aid], queryFn: () => api.bidsFor(aid), refetchInterval: connected ? 10000 : 3000 })
  const [amount, setAmount] = useState<string>('')
  useEffect(() => { if (a) setAmount(String(a.minNextBid)) }, [a?.id, a?.minNextBid])

  const open = a && (a.status === 'LIVE' || a.status === 'ENDING')
  const iAmTop = !!(a && user && a.highestBidId && bids?.find(b => b.id === a.highestBidId)?.bidderId === user.id)
  const recent = useMemo(() => (bids ?? []).slice(0, 12), [bids])
  const heat = useMemo(() => {
    if (!a || !bids?.length) return []
    const start = +new Date(a.startTime), end = +new Date(a.endTime)
    const buckets = Array.from({ length: 14 }, () => 0)
    bids.forEach(b => { const t = (+new Date(b.at) - start) / Math.max(1, end - start); buckets[Math.min(13, Math.max(0, Math.floor(t * 14)))]++ })
    const mx = Math.max(...buckets, 1)
    return buckets.map(v => v / mx)
  }, [a, bids])

  if (!a) return <main className="max-w-6xl mx-auto p-10 text-slate-500">Loading auction… (is the auction service up? scripts\start-auction.bat)</main>

  const submit = async () => {
    if (!user) return setToast({ msg: 'Sign in with a buyer account to bid', kind: 'err' })
    try {
      const r = await api.placeBid({ auctionId: aid, amount: Number(amount), idempotencyKey: uuid() })
      setToast({ msg: `✅ Bid accepted at ${money(r.bid.amount)}${r.extended ? ' — anti-sniping extended the clock!' : ''}`, kind: 'ok' })
      qc.invalidateQueries({ queryKey: ['auction', aid] }); qc.invalidateQueries({ queryKey: ['bids', aid] })
    } catch (e) { setToast({ msg: '⛔ ' + (e as Error).message, kind: 'err' }); qc.invalidateQueries({ queryKey: ['auction', aid] }) }
  }

  return (
    <main className="max-w-6xl mx-auto px-5 pt-6 pb-16">
      {toast && <div className={`fixed top-16 right-4 z-50 px-4 py-3 rounded-xl border text-sm font-semibold pop ${toast.kind === 'ok' ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-200' : 'bg-rose-500/10 border-rose-500/40 text-rose-200'}`}>{toast.msg}</div>}
      <div className="flex flex-wrap items-center gap-2 mb-4 text-xs">
        {statusChip(a.status)}
        <span className="px-2.5 py-0.5 rounded-full border border-line text-slate-400">{a.category}</span>
        {a.antiSnipingEnabled && <span className="px-2.5 py-0.5 rounded-full border border-line text-slate-400">🛡 anti-snipe +{a.extensionWindowSecs}s · {a.extensionCount}/{a.maxExtensions}</span>}
        <span className={`px-2.5 py-0.5 rounded-full border ${connected ? 'border-emerald-500/40 text-emerald-300' : 'border-slate-600 text-slate-500'}`}>
          <span className={`inline-block w-1.5 h-1.5 rounded-full mr-1 ${connected ? 'bg-good animate-pulse' : 'bg-slate-500'}`} />
          {connected ? 'LIVE FEED' : 'polling (socket down)'}
        </span>
        <button className="ml-auto px-3 py-1 rounded-lg border border-line text-slate-300" onClick={() => { navigator.clipboard.writeText(location.href) }}>🔗 Share</button>
      </div>

      <div className="grid lg:grid-cols-[1.1fr_.9fr] gap-5">
        <div className="space-y-5">
          <div className={`rounded-2xl overflow-hidden border ${a.status === 'ENDING' ? 'border-amber-400/70' : 'border-line'}`}><div className="relative h-72 grid place-items-center text-8xl overflow-hidden" style={{ background: 'linear-gradient(135deg,#0ea5e9,#6366f1)' }}>{a.emoji}{a.imageUrl && <img src={a.imageUrl} alt={a.title} className="absolute inset-0 h-full w-full object-cover" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }} />}</div></div>
          <div className="rounded-2xl border border-line bg-panel p-5">
            <h1 className="text-xl font-extrabold m-0">{a.title}</h1>
            <p className="text-xs text-slate-500 mt-1 mb-3">by <b>{a.sellerId === user?.id ? 'you' : 'Seller ' + a.sellerId}</b> · optimistic-lock v{a.version}</p>
            <p className="text-sm text-slate-400">{a.description}</p>
            <div className="grid grid-cols-2 gap-2 mt-4 text-xs">
              {[['Starting price', money(a.startingPrice)], ['Min increment', money(a.minIncrement)], ['Reserve', a.reservePrice ? money(a.reservePrice) : 'none'], ['Bids', String(a.bidCount)]].map(([k, v]) =>
                <div key={k} className="rounded-lg bg-slate-800/40 p-2.5"><div className="text-slate-500">{k}</div><div className="font-bold text-sm">{v}</div></div>)}
            </div>
          </div>
          <div className="rounded-2xl border border-line bg-panel p-5">
            <h3 className="text-sm font-bold m-0 mb-2">🔥 Bid intensity (auction lifetime)</h3>
            <div className="flex gap-1 h-9 items-end">{(heat.length ? heat : Array(14).fill(0)).map((v, i) => <div key={i} className="flex-1 rounded-sm bg-gradient-to-t from-acc to-acc2" style={{ height: `${Math.max(8, v * 100)}%` }} />)}</div>
          </div>
        </div>

        <div className="space-y-5">
          <div className={`rounded-2xl border p-5 ${a.status === 'ENDING' ? 'border-amber-400/70 animate-pulse' : 'border-line'} bg-panel`}>
            <div className="text-xs text-slate-500 font-bold">CURRENT HIGHEST BID</div>
            <div className={`text-4xl font-extrabold ${a.status === 'ENDING' ? 'text-amber-300' : ''}`} key={a.currentPrice}>{money(a.currentPrice)}</div>
            {iAmTop && <div className="text-good text-sm font-bold">🫵 You are the top bidder</div>}
            <div className="flex justify-between items-center mt-3">
              <span className="text-xs text-slate-500 font-bold">{a.status === 'SCHEDULED' ? 'STARTS IN' : 'TIME REMAINING'}</span>
              <span className="text-2xl font-extrabold tabular-nums"><Countdown end={a.status === 'SCHEDULED' ? a.startTime : a.endTime} /></span>
            </div>
            {a.status === 'ENDING' && <p className="text-amber-300/90 text-xs mt-2 mb-0">⚡ Under one minute. Anti-sniping may extend the clock.</p>}
            {a.status === 'SOLD' && <p className="text-good text-sm mt-2 mb-0">🏆 Sold{a.winnerId === user?.id ? ' — YOU WON' : ''} for {money(a.winningAmount ?? a.currentPrice)}. <Link className="underline" to="/payments">Settle →</Link></p>}
            {(a.status === 'UNSOLD' || a.status === 'CANCELLED') && <p className="text-slate-400 text-sm mt-2 mb-0">Closed — {a.closeReason === 'RESERVE_NOT_MET' ? 'reserve not met' : a.closeReason === 'NO_BIDS' ? 'no valid bids' : a.status.toLowerCase()}.</p>}
          </div>

          {open ? (
            <div className="rounded-2xl border border-line bg-panel p-5">
              <h3 className="font-bold m-0 mb-2">Place your bid</h3>
              <div className="flex gap-2">
                <input type="number" value={amount} onChange={e => setAmount(e.target.value)} className="flex-1 bg-slate-800/60 border border-line rounded-xl px-3 py-2.5 font-bold outline-none focus:border-acc" />
                <button onClick={submit} className="px-5 py-2.5 rounded-xl font-extrabold bg-gradient-to-r from-acc to-acc2 text-ink">BID NOW</button>
              </div>
              <div className="grid grid-cols-2 gap-2 mt-3 text-xs">
                <div className="rounded-lg bg-slate-800/40 p-2.5"><div className="text-slate-500">Minimum acceptable</div><b className="text-sm">{money(a.minNextBid)}</b></div>
                <div className="rounded-lg bg-slate-800/40 p-2.5"><div className="text-slate-500">Next clean bid</div><b className="text-sm">{money(a.minNextBid + Number(a.minIncrement))}</b></div>
              </div>
              <p className="text-[11px] text-slate-500 mt-3 mb-0">💡 Every submit carries an idempotency key — double-clicks cannot create two bids.</p>
            </div>
          ) : !user && <div className="rounded-2xl border border-line bg-panel p-5 text-sm text-slate-400"><Link to="/login" className="text-acc font-bold">Sign in</Link> to bid when this auction opens.</div>}

          <div className="rounded-2xl border border-line bg-panel p-5">
            <h3 className="font-bold m-0 mb-2">Live bid activity</h3>
            {recent.length ? recent.map((b: Bid) => (
              <div key={b.id} className={`flex justify-between py-2 px-2.5 rounded-lg text-sm border-b border-line/50 ${b.bidderId === user?.id ? 'bg-cyan-400/5 outline outline-1 outline-cyan-400/30' : ''}`}>
                <span>{b.bidderId === user?.id ? '🫵 ' : ''}{b.bidderName} <span className="text-slate-600 text-xs">· {new Date(b.at).toLocaleTimeString()}</span></span>
                <b className="tabular-nums">{money(b.amount)}</b>
              </div>
            )) : <p className="text-slate-500 text-sm m-0">No bids yet — open the floor.</p>}
          </div>
        </div>
      </div>
    </main>
  )
}
