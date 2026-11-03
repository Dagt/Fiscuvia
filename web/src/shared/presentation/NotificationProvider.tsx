import { useState, type PropsWithChildren } from 'react'
import { X } from 'lucide-react'
import { Button } from '../ui/button'
import { NotificationContext, type Notification } from './notification-context'
import { StatusPill } from '../ui/StatusPill'

export function NotificationProvider({ children }: PropsWithChildren) {
  const [notice, setNotice] = useState<Notification | null>(null)
  return (
    <NotificationContext
      value={(value) =>
        setNotice(typeof value === 'string' ? { message: value } : value)
      }
    >
      {children}
      <div
        className="notification-region"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {notice && (
          <div className="notification">
            <StatusPill tone={notice.tone ?? 'success'}>
              {notice.message}
            </StatusPill>
            {notice.action && (
              <Button variant="ghost" onClick={notice.action.onClick}>
                {notice.action.label}
              </Button>
            )}
            <Button
              variant="ghost"
              aria-label="Cerrar notificación"
              onClick={() => setNotice(null)}
            >
              <X size={18} aria-hidden="true" />
            </Button>
          </div>
        )}
      </div>
    </NotificationContext>
  )
}
