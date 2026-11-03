import {
  CheckCircle2,
  Info,
  AlertTriangle,
  CircleX,
  CircleDashed,
} from 'lucide-react'
export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'
const icons = {
  success: CheckCircle2,
  warning: AlertTriangle,
  danger: CircleX,
  info: Info,
  neutral: CircleDashed,
}
export function StatusPill({
  tone = 'neutral',
  children,
}: {
  tone?: Tone
  children: string
}) {
  const Icon = icons[tone]
  return (
    <span className={`status-pill tone-${tone}`}>
      <Icon size={16} aria-hidden="true" />
      {children}
    </span>
  )
}
