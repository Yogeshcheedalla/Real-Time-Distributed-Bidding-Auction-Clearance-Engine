import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, money } from '../api'
import type { Auction } from '../types'

export const statusChip = (s: Auction['status']) => {
  const map: Record<string, string> = {
    LIVE: 'bg-rose-500/15 text-rose-300 border-rose-500/40',
    ENDING: 'bg-amber-400 text-ink border-amber-300',
    SCHEDULED: 'bg-cyan-400/10 text-acc border-cyan-400/40',
    SOLD: 'bg-emerald-400/10 text-good border-emerald-400/40',
    UNSOLD: 'bg-slate-700/40 text-slate-300 border-slate-600', CANCELLED: 'bg-slate-700/40 text-slate-300 border-slate-600',
    DRAFT: 'bg-slate-700/40 text-slate-400 border-slate-600', ENDED: 'bg-slate-700/40 text-slate-300 border-slate-600',
  }
  return <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold border ${map[s] ?? map.DRAFT}`}>
    {s === 'LIVE' && <span className="w-1.5 h-1.5 rounded-full bg-rose-400 animate-pulse" />}
    {s === 'ENDING' ? '⚡ ENDING SOON' : s}
  </span>
}

export function Countdown({ end }: { end: string }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    setNow(Date.now())
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [end])
  const ms = Math.max(0, +new Date(end) - now)
  const s = Math.floor(ms / 1000)
  const t = `${s >= 3600 ? String(Math.floor(s / 3600)).padStart(2, '0') + ':' : ''}${String(Math.floor(s % 3600 / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
  return <span className={`font-bold tabular-nums ${s > 0 && s < 60 ? 'text-bad blink-critical' : ''}`}>{t}</span>
}

export function AuctionCard({ a }: { a: Auction }) {
  const open = a.status === 'LIVE' || a.status === 'ENDING'
  return (
    <Link to={`/auction/${a.id}`} className="group rounded-2xl border border-line bg-panel overflow-hidden hover:border-slate-600 hover:-translate-y-1 transition">
      <div className="relative h-32 grid place-items-center text-5xl overflow-hidden" style={{ background: "linear-gradient(135deg,#0ea5e9,#6366f1)" }}>{a.emoji}{a.imageUrl && <img src={a.imageUrl} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover" onError={(e) => { (e.target as HTMLImageElement).style.display = "none" }} />}</div>
      <div className="p-4 space-y-1.5">
        <div className="flex justify-between items-center">{statusChip(a.status)}<span className="text-xs text-slate-500">{a.bidCount} bids</span></div>
        <div className="font-bold truncate">{a.title}</div>
        <div className="flex justify-between items-end">
          <div><div className="text-[11px] text-slate-500 font-semibold">CURRENT BID</div><div className={`text-lg font-extrabold ${a.status === 'ENDING' ? 'text-amber-300' : ''}`}>{money(a.currentPrice)}</div></div>
          {(open || a.status === 'SCHEDULED') && <div className="text-right"><div className="text-[11px] text-slate-500 font-semibold">{a.status === 'SCHEDULED' ? 'STARTS IN' : 'ENDS IN'}</div><Countdown end={a.status === 'SCHEDULED' ? a.startTime : a.endTime} /></div>}
        </div>
      </div>
    </Link>
  )
}

export default function Landing() {
  const { data } = useQuery({ queryKey: ['live'], queryFn: () => api.listAuctions({ status: 'OPEN', sort: 'ending', size: 4 }) })
  const live = data?.content ?? []
  return (
    <main>
      <section className="max-w-6xl mx-auto px-5 pt-20 pb-10 grid lg:grid-cols-[1.05fr_.95fr] gap-12 items-center">
        <div>
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold border border-rose-500/40 bg-rose-500/10 text-rose-300">
            <span className="w-2 h-2 rounded-full bg-good animate-pulse" /> {live.length} AUCTIONS LIVE RIGHT NOW
          </span>
          <h1 className="mt-5 text-5xl lg:text-6xl font-extrabold leading-[1.05] tracking-tight">
            REAL-TIME<br />AUCTIONS.<br /><span className="text-transparent bg-clip-text bg-gradient-to-r from-acc to-acc2">ZERO DELAY.</span>
          </h1>
          <p className="mt-5 text-slate-400 max-w-md"><b className="text-slate-200">Bid faster. Compete fairly. Win confidently.</b><br />Gateway-routed microservices, race-safe bidding, deterministic winner resolution, anti-sniping and event-driven settlement.</p>
          <div className="mt-7 flex gap-3">
            <Link to="/auctions" className="px-6 py-3 rounded-xl font-bold bg-gradient-to-r from-acc to-acc2 text-ink">Explore Live Auctions</Link>
            <Link to="/seller" className="px-6 py-3 rounded-xl font-bold border border-slate-600 text-slate-200 hover:bg-slate-800">Start Selling</Link>
          </div>
        </div>
        <div className="rounded-2xl border border-line bg-panel p-5">
          <div className="flex justify-between items-center mb-3"><h3 className="font-bold m-0">🔴 Live now</h3><span className="text-xs font-bold text-good flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-good" />STOMP /topic/auction</span></div>
          {live.slice(0, 3).map(a => (
            <Link key={a.id} to={`/auction/${a.id}`} className="flex justify-between items-center py-2.5 px-3 rounded-lg hover:bg-slate-800/50 border-b border-line/60">
              <div><div className="font-bold text-sm">{a.emoji} {a.title.slice(0, 30)}{a.title.length > 30 ? '…' : ''}</div><div className="text-xs text-slate-500">{a.bidCount} bidders · {a.category}</div></div>
              <div className="text-right"><div className="font-extrabold">{money(a.currentPrice)}</div><Countdown end={a.endTime} /></div>
            </Link>
          ))}
          {!live.length && <p className="text-sm text-slate-500 py-6 text-center">Nothing live this second — <Link className="text-acc" to="/auctions">browse upcoming</Link>.</p>}
        </div>
      </section>
      <section className="max-w-6xl mx-auto px-5 py-10">
        <h2 className="text-2xl font-extrabold mb-5">Featured live auctions</h2>
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">{live.map(a => <AuctionCard key={a.id} a={a} />)}</div>
      </section>
      <section className="max-w-6xl mx-auto px-5 py-10">
        <div className="grid md:grid-cols-3 gap-4">
          {[
            ['🧵 Race-safe concurrency', 'SELECT … FOR UPDATE on the bid ledger row: 100 simultaneous bidders can never corrupt the price ladder — proven in the backend test suite.'],
            ['⏱ Anti-sniping', 'A bid in the final window extends the clock (configurable seconds and max extensions), always displayed in the room.'],
            ['⚖️ Deterministic fairness', 'Winner = amount DESC → earliest accepted → unique bid id. Same ledger, same winner, every time.'],
          ].map(([t, d]) => (
            <div key={t} className="rounded-2xl border border-line bg-panel p-5"><div className="font-bold mb-1">{t}</div><p className="text-sm text-slate-400 m-0">{d}</p></div>
          ))}
        </div>
      </section>
    </main>
  )
}
