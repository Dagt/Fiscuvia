import { useState, type PropsWithChildren } from 'react'
import { CheckCircle2, X } from 'lucide-react'
import { Button } from '../ui/button'
import { NotificationContext } from './notification-context'

export function NotificationProvider({ children }: PropsWithChildren) {
  const [message, setMessage] = useState('')
  return (
    <NotificationContext value={setMessage}>
      {children}
      <div
        className="notification-region"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {message && (
          <div className="notification">
            <CheckCircle2 aria-hidden="true" size={20} />
            <span>{message}</span>
            <Button
              variant="ghost"
              aria-label="Cerrar notificación"
              onClick={() => setMessage('')}
            >
              <X size={18} aria-hidden="true" />
            </Button>
          </div>
        )}
      </div>
    </NotificationContext>
  )
}
