import { useEffect, useRef, useCallback, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * Hook kết nối STOMP WebSocket đến Spring Boot /api/ws
 * Gửi tin: sendMessage({ conversationId, receiverId, content })
 * Nhận tin: subscribe topic /user/queue/messages
 */
export function useWebSocket({ onMessage, enabled = true }) {
  const clientRef  = useRef(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled) return
    const token = localStorage.getItem('accessToken')
    if (!token) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/api/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        // Subscribe nhận tin
        client.subscribe('/user/queue/messages', (frame) => {
          try {
            const msg = JSON.parse(frame.body)
            onMessage?.(msg)
          } catch (_) {}
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        console.error('STOMP error', frame)
        setConnected(false)
      },
    })

    client.activate()
    clientRef.current = client

    return () => { client.deactivate() }
  }, [enabled])

  const sendMessage = useCallback((payload) => {
    if (!clientRef.current?.connected) return false
    clientRef.current.publish({
      destination: '/app/chat.send',
      body: JSON.stringify(payload),
    })
    return true
  }, [])

  return { connected, sendMessage }
}
