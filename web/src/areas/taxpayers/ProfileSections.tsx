import { useId, type ReactNode } from 'react'
import { LockKeyhole } from 'lucide-react'
import { Button } from '../../shared/ui/button'

export type ProfileSection = { id: string; label: string; available: boolean }
/** Manual tab activation: arrows move focus; Enter/Space activate the selected section. */
export function ProfileSections({
  items,
  selected,
  onChange,
  children,
}: {
  items: readonly ProfileSection[]
  selected: string
  onChange: (id: string) => void
  children: ReactNode
}) {
  const id = useId()
  return (
    <>
      <div
        className="taxpayer-profile-sections"
        role="tablist"
        aria-label="Secciones del expediente"
        onKeyDown={(event) => {
          const buttons = Array.from(
            event.currentTarget.querySelectorAll<HTMLButtonElement>(
              '[role="tab"]:not(:disabled)',
            ),
          )
          const index = buttons.indexOf(
            document.activeElement as HTMLButtonElement,
          )
          if (index < 0) return
          const target =
            event.key === 'Home'
              ? 0
              : event.key === 'End'
                ? buttons.length - 1
                : event.key === 'ArrowRight'
                  ? (index + 1) % buttons.length
                  : event.key === 'ArrowLeft'
                    ? (index + buttons.length - 1) % buttons.length
                    : null
          if (target !== null) {
            event.preventDefault()
            buttons[target]?.focus()
          }
        }}
      >
        {items.map((item) => (
          <Button
            key={item.id}
            id={`${id}-${item.id}`}
            type="button"
            role="tab"
            variant={item.id === selected ? 'primary' : 'ghost'}
            aria-selected={item.id === selected}
            aria-controls={`${id}-panel`}
            tabIndex={item.id === selected ? 0 : -1}
            disabled={!item.available}
            title={
              item.available
                ? undefined
                : 'Sección pendiente de habilitación autorizada'
            }
            onClick={() => onChange(item.id)}
          >
            {!item.available && <LockKeyhole size={15} aria-hidden="true" />}
            {item.label}
            {!item.available && (
              <span className="sr-only"> · No habilitado</span>
            )}
          </Button>
        ))}
      </div>
      <div
        role="tabpanel"
        id={`${id}-panel`}
        aria-labelledby={`${id}-${selected}`}
        tabIndex={0}
      >
        {children}
      </div>
    </>
  )
}
