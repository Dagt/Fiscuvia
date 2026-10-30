import { createContext, useContext } from 'react'

export const NotificationContext = createContext<
  ((message: string) => void) | null
>(null)

export function useNotification() {
  const notify = useContext(NotificationContext)
  if (!notify) throw new Error('useNotification requires NotificationProvider')
  return notify
}
