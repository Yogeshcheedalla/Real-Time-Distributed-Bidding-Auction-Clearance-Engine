import { useEffect } from 'react'
import { NavLink, Route, Routes, Link, useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
import Landing from './pages/Landing'
import Auctions from './pages/Auctions'
import AuctionDetail from './pages/AuctionDetail'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import MyBids from './pages/MyBids'
import Payments from './pages/Payments'
import Seller from './pages/Seller'
import Admin from './pages/Admin'

const nav = 'px-3 py-2 rounded-lg text-sm font-semibold text-slate-400 hover:text-white hover:bg-slate-800/60'

export default function App() {
  const { user, ready, init, logout } = useAuth()
  const navigate = useNavigate()
  useEffect(() => { init() }, [])

  return (
    <div className="min-h-screen font-sans">
      <header className="sticky top-0 z-50 backdrop-blur bg-ink/80 border-b border-line">
        <div className="max-w-6xl mx-auto px-5 h-14 flex items-center gap-4">
          <Link to="/" className="flex items-center gap-2 font-extrabold text-lg">
            <span className="grid place-items-center w-8 h-8 rounded-lg bg-gradient-to-r from-acc to-acc2 text-ink text-sm">⚡</span>
            Bid<span className="text-transparent bg-clip-text bg-gradient-to-r from-acc to-acc2">Velocity</span>
          </Link>
          <nav className="flex-1 flex items-center gap-1">
            <NavLink className={({ isActive }) => nav + (isActive ? ' bg-slate-800 text-white' : '')} to="/auctions">Marketplace</NavLink>
            <NavLink className={nav} to="/auctions?status=OPEN">Live now</NavLink>
            {user?.roles.includes('SELLER') && <NavLink className={nav} to="/seller">Sell</NavLink>}
            {user?.roles.includes('ADMIN') && <NavLink className={nav} to="/admin">Admin</NavLink>}
          </nav>
          {user ? (
            <div className="flex items-center gap-2">
              <NavLink className={nav} to="/dashboard">{user.firstName.split(' ')[0]}</NavLink>
              <NavLink className={nav} to="/bids">Bids</NavLink>
              <NavLink className={nav} to="/payments">Payments</NavLink>
              <button className="text-sm text-slate-400 hover:text-white" onClick={() => { logout(); navigate('/') }}>Sign out</button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link className="px-3 py-1.5 text-sm font-semibold text-slate-300 hover:text-white" to="/login">Sign in</Link>
              <Link className="px-4 py-1.5 text-sm font-bold rounded-xl bg-gradient-to-r from-acc to-acc2 text-ink" to="/register">Get started</Link>
            </div>
          )}
        </div>
      </header>

      {!ready ? (
        <div className="max-w-6xl mx-auto p-5"><div className="h-64 rounded-2xl bg-panel animate-pulse" /></div>
      ) : (
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/auctions" element={<Auctions />} />
          <Route path="/auction/:id" element={<AuctionDetail />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/bids" element={<MyBids />} />
          <Route path="/payments" element={<Payments />} />
          <Route path="/seller" element={<Seller />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="*" element={<div className="max-w-6xl mx-auto p-16 text-center"><h1 className="text-6xl font-extrabold">404</h1><p className="text-slate-400 mt-2">No route: this page lives in no registered service.</p><Link to="/" className="text-acc font-bold">← Back to the arena</Link></div>} />
        </Routes>
      )}

      <footer className="border-t border-line mt-16 py-8 text-center text-xs text-slate-500">
        BidVelocity · gateway :8080 → auth :8081 · auction :8082 · bidding :8083 · payment :8084 · Eureka :8761 · PostgreSQL × 4 (database-per-service)
      </footer>
    </div>
  )
}
