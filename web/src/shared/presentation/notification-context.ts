import { createContext, useContext } from 'react'
import type { Tone } from '../ui/StatusPill'

export type Notification = {
  message: string
  tone?: Tone
  action?: { label: string; onClick: () => void }
}

export const NotificationContext = createContext<
  ((message: string | Notification) => void) | null
>(null)

export function useNotification() {
  const notify = useContext(NotificationContext)
  if (!notify) throw new Error('useNotification requires NotificationProvider')
  return notify
}
