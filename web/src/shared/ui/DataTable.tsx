import { useId, type ReactNode } from 'react'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import { Button } from './button'
import { EmptyState, ErrorState, LoadingState, PermissionState } from './States'
import type { ViewState } from './StatCard'
import { useCompactLayout } from './useCompactLayout'

export type TableSort = { column: string; direction: 'asc' | 'desc' }
export type Column<T> = {
  id: string
  header: string
  cell: (row: T) => ReactNode
  sortable?: boolean
  numeric?: boolean
}
/** Only the supplied authorized page is rendered. Fetching/filtering/authorization belong to the caller. */
export function DataTable<T>({
  label,
  rows,
  columns,
  rowKey,
  rowLabel,
  page,
  pageSize,
  total,
  hasNext,
  onPageChange,
  sort,
  onSortChange,
  state = 'ready',
  onRetry,
  renderMobile,
  compactBelow,
}: {
  label: string
  rows: readonly T[]
  columns: readonly Column<T>[]
  rowKey: (row: T) => string
  rowLabel: (row: T) => string
  page: number
  pageSize: number
  total?: number
  hasNext?: boolean
  onPageChange: (page: number) => void
  sort?: TableSort
  onSortChange?: (sort: TableSort) => void
  state?: ViewState
  onRetry: () => void
  renderMobile?: (row: T) => ReactNode
  compactBelow?: number
}) {
  const compact = useCompactLayout(compactBelow)
  const id = useId()
  if (state === 'loading') return <LoadingState />
  if (state === 'error') return <ErrorState onRetry={onRetry} />
  if (state === 'unauthorized') return <PermissionState />
  if (state === 'empty' || rows.length === 0)
    return (
      <EmptyState
        title="No hay resultados"
        description="Prueba otros filtros o limpia la búsqueda."
      />
    )
  const pages =
    total === undefined ? undefined : Math.max(1, Math.ceil(total / pageSize))
  return (
    <section className="data-table" aria-label={label}>
      {compact ? (
        <ul className="data-mobile-list" aria-label={label}>
          {rows.map((row) => (
            <li key={rowKey(row)}>
              <h3>{rowLabel(row)}</h3>
              {renderMobile ? (
                renderMobile(row)
              ) : (
                <dl>
                  {columns.map((column) => (
                    <div key={column.id}>
                      <dt>{column.header}</dt>
                      <dd>{column.cell(row)}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <div
          className="table-scroll"
          role="region"
          aria-labelledby={id}
          tabIndex={0}
        >
          <table>
            <caption id={id}>{label}</caption>
            <thead>
              <tr>
                {columns.map((column) => {
                  const active = sort?.column === column.id
                  const Icon = active
                    ? sort.direction === 'asc'
                      ? ArrowUp
                      : ArrowDown
                    : ArrowUpDown
                  return (
                    <th
                      key={column.id}
                      scope="col"
                      className={column.numeric ? 'numeric' : undefined}
                      aria-sort={
                        column.sortable
                          ? active
                            ? sort.direction === 'asc'
                              ? 'ascending'
                              : 'descending'
                            : 'none'
                          : undefined
                      }
                    >
                      {column.sortable && onSortChange ? (
                        <Button
                          variant="ghost"
                          onClick={() =>
                            onSortChange({
                              column: column.id,
                              direction:
                                active && sort.direction === 'asc'
                                  ? 'desc'
                                  : 'asc',
                            })
                          }
                        >
                          {column.header}
                          <Icon size={16} aria-hidden="true" />
                        </Button>
                      ) : (
                        column.header
                      )}
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={rowKey(row)}>
                  {columns.map((column) => (
                    <td
                      key={column.id}
                      className={column.numeric ? 'numeric' : undefined}
                    >
                      {column.cell(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <nav className="table-pagination" aria-label={`Paginación: ${label}`}>
        <p aria-live="polite">
          {total === undefined ? (
            `Página ${page} · ${rows.length} en esta página`
          ) : (
            <>
              {(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)} de{' '}
              {total} · Página {page} de {pages}
            </>
          )}
        </p>
        <div className="component-actions">
          <Button
            variant="secondary"
            disabled={page <= 1}
            onClick={() => onPageChange(page - 1)}
          >
            Anterior
          </Button>
          <Button
            variant="secondary"
            disabled={
              pages === undefined ? !hasNext || page >= 10001 : page >= pages
            }
            onClick={() => onPageChange(page + 1)}
          >
            Siguiente
          </Button>
        </div>
      </nav>
    </section>
  )
}
