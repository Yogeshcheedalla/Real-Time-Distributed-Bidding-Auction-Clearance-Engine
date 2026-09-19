import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

/**
 * Live auction feed over WebSocket/STOMP via the gateway (/ws). Falls back to
 * REST polling automatically if the socket can't connect, so the room always
 * updates — never a fake "connected" state.
 */
export function useAuctionSocket(auctionId: number, onEvent: (e: Record<string, string>) => void) {
  const [connected, setConnected] = useState(false)
  const cb = useRef(onEvent); cb.current = onEvent

  useEffect(() => {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${proto}://${location.host}/ws`,
      reconnectDelay: 4000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/auction/${auctionId}`, (msg) => {
          try { cb.current(JSON.parse(msg.body)) } catch { /* ignore malformed */ }
        })
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    })
    try { client.activate() } catch { setConnected(false) }
    return () => { try { client.deactivate() } catch { /* noop */ } }
  }, [auctionId])

  return connected
}
