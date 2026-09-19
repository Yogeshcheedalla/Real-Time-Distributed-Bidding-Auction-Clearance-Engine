import React from 'react'
import ReactDOM from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { consumeOAuthToken } from './auth'
import './index.css'

const qc = new QueryClient({ defaultOptions: { queries: { refetchInterval: 10_000, retry: 1, staleTime: 2000 } } })
consumeOAuthToken()   // Google OAuth lands here with #token=...

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={qc}>
      <HashRouter>
        <App />
      </HashRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)
