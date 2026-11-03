import { useId, useState } from 'react'
import { SlidersHorizontal } from 'lucide-react'
import { Button } from './button'
import { Field } from './Field'
import { useCompactLayout } from './useCompactLayout'
export type SelectFilter = {
  id: string
  label: string
  value: string
  options: readonly { value: string; label: string }[]
  onChange: (value: string) => void
}
export function FilterBar({
  search,
  onSearchChange,
  filters,
  onClear,
  activeCount,
  pending = false,
  searchLabel = 'Buscar',
}: {
  search: string
  onSearchChange: (value: string) => void
  filters: readonly SelectFilter[]
  onClear: () => void
  activeCount: number
  pending?: boolean
  searchLabel?: string
}) {
  const id = useId()
  const compact = useCompactLayout()
  const [expanded, setExpanded] = useState(false)
  return (
    <div className="filter-bar" aria-label="Filtros de la lista">
      <Field
        label={searchLabel}
        type="search"
        value={search}
        onChange={(event) => onSearchChange(event.target.value)}
        disabled={pending}
      />
      {compact && (
        <Button
          variant="secondary"
          aria-expanded={expanded}
          aria-controls={id}
          onClick={() => setExpanded((value) => !value)}
        >
          <SlidersHorizontal size={18} aria-hidden="true" />
          Filtros ({activeCount})
        </Button>
      )}
      <div id={id} className="filter-options" hidden={compact && !expanded}>
        {filters.map((filter) => (
          <div className="field" key={filter.id}>
            <label htmlFor={`${id}-${filter.id}`}>{filter.label}</label>
            <select
              className="input"
              id={`${id}-${filter.id}`}
              value={filter.value}
              disabled={pending}
              onChange={(event) => filter.onChange(event.target.value)}
            >
              {filter.options.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        ))}
        <Button
          variant="ghost"
          disabled={!activeCount || pending}
          onClick={onClear}
        >
          Limpiar filtros
        </Button>
      </div>
      <p className="filter-count" aria-live="polite">
        {activeCount}{' '}
        {activeCount === 1 ? 'filtro aplicado' : 'filtros aplicados'}
      </p>
    </div>
  )
}
