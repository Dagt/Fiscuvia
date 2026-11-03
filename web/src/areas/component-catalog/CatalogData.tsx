import { useMemo, useState } from 'react'
import {
  DataTable,
  type Column,
  type TableSort,
} from '../../shared/ui/DataTable'
import { FilterBar } from '../../shared/ui/FilterBar'
import { StatusPill } from '../../shared/ui/StatusPill'
import { Button } from '../../shared/ui/button'
import { DetailsPanel, SideDrawer } from '../../shared/ui/Panels'
import type { ViewState } from '../../shared/ui/StatCard'

type DemoRow = {
  id: string
  name: string
  category: 'Revisión DEMO' | 'Referencia DEMO'
  value: number
}
const data: readonly DemoRow[] = Array.from({ length: 125 }, (_, i) => ({
  id: `DEMO-${String(i + 1).padStart(3, '0')}`,
  name:
    i === 0
      ? 'Referencia DEMO de nombre extenso para comprobar ajuste de línea y lectura accesible en espacios cortos'.padEnd(
          100,
          '.',
        )
      : i === 1
        ? 'D'.repeat(64)
        : `Referencia de interfaz DEMO ${String(i + 1).padStart(3, '0')}`,
  category: i % 2 ? 'Revisión DEMO' : 'Referencia DEMO',
  value: i === 0 ? 9876543210.98 : (i + 1) * 125.5,
}))
const number = new Intl.NumberFormat('es-MX', {
  maximumFractionDigits: 2,
  minimumFractionDigits: 2,
})

export function CatalogData() {
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(1)
  const [sort, setSort] = useState<TableSort>({
    column: 'id',
    direction: 'asc',
  })
  const [state, setState] = useState<ViewState>('ready')
  const [detail, setDetail] = useState<DemoRow | null>(null)
  const filtered = useMemo(
    () =>
      data
        .filter(
          (row) =>
            (!category || row.category === category) &&
            `${row.id} ${row.name}`
              .toLocaleLowerCase('es-MX')
              .includes(search.toLocaleLowerCase('es-MX')),
        )
        .sort((a, b) => {
          const key =
            sort.column === 'name'
              ? 'name'
              : sort.column === 'value'
                ? 'value'
                : 'id'
          const result =
            key === 'value'
              ? a.value - b.value
              : String(a[key]).localeCompare(String(b[key]), 'es-MX')
          return sort.direction === 'asc' ? result : -result
        }),
    [search, category, sort],
  )
  const columns: readonly Column<DemoRow>[] = [
    {
      id: 'id',
      header: 'Referencia',
      sortable: true,
      cell: (row) => <span className="row-reference">{row.id}</span>,
    },
    {
      id: 'name',
      header: 'Nombre demostrativo',
      sortable: true,
      cell: (row) => row.name,
    },
    {
      id: 'category',
      header: 'Categoría',
      cell: (row) => (
        <StatusPill
          tone={row.category === 'Revisión DEMO' ? 'warning' : 'info'}
        >
          {row.category}
        </StatusPill>
      ),
    },
    {
      id: 'value',
      header: 'Valor DEMO',
      sortable: true,
      numeric: true,
      cell: (row) => number.format(row.value),
    },
    {
      id: 'actions',
      header: 'Detalle',
      cell: (row) => (
        <Button
          variant="ghost"
          aria-label={`Ver detalle de ${row.id}`}
          onClick={() => setDetail(row)}
        >
          Ver
        </Button>
      ),
    },
  ]
  return (
    <section
      className="catalog-section"
      id="tabla"
      aria-labelledby="table-title"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">COMPONENTES P1</p>
          <h2 id="table-title">Datos legibles, incluso cuando crecen</h2>
          <p>
            125 registros sintéticos en memoria. Sin contribuyentes, servicios
            ni permisos de negocio.
          </p>
        </div>
        <SideDrawer
          title="Contrato de DataTable"
          description="Referencia técnica de la demostración."
          trigger={<Button variant="secondary">Ver contrato</Button>}
        >
          <p>
            La tabla recibe únicamente la página autorizada. Paginación, orden y
            total se controlan desde el consumidor.
          </p>
          <p>
            El filtrado local de este catálogo sólo opera sobre referencias
            DEMO.
          </p>
        </SideDrawer>
      </div>
      <FilterBar
        pending={state === 'loading'}
        searchLabel="Buscar en registros DEMO"
        search={search}
        onSearchChange={(value) => {
          setSearch(value)
          setPage(1)
        }}
        activeCount={Number(!!search) + Number(!!category)}
        onClear={() => {
          setSearch('')
          setCategory('')
          setPage(1)
        }}
        filters={[
          {
            id: 'category',
            label: 'Categoría DEMO',
            value: category,
            options: [
              { value: '', label: 'Todas las categorías' },
              { value: 'Revisión DEMO', label: 'Revisión DEMO' },
              { value: 'Referencia DEMO', label: 'Referencia DEMO' },
            ],
            onChange: (value) => {
              setCategory(value)
              setPage(1)
            },
          },
        ]}
      />
      <div className="field table-state-control">
        <label htmlFor="demo-table-state">Estado de tabla DEMO</label>
        <select
          className="input"
          id="demo-table-state"
          value={state}
          onChange={(event) => setState(event.target.value as ViewState)}
        >
          {(
            ['ready', 'loading', 'empty', 'error', 'unauthorized'] as const
          ).map((value, index) => (
            <option key={value} value={value}>
              {
                [
                  'Con datos',
                  'Cargando',
                  'Vacío',
                  'Error recuperable',
                  'Sin permisos',
                ][index]
              }
            </option>
          ))}
        </select>
      </div>
      <div className={detail ? 'table-with-detail' : undefined}>
        <DataTable
          label="Referencias sintéticas DEMO"
          rows={filtered.slice((page - 1) * 10, page * 10)}
          columns={columns}
          rowKey={(row) => row.id}
          rowLabel={(row) => row.id}
          page={page}
          pageSize={10}
          total={filtered.length}
          onPageChange={setPage}
          sort={sort}
          onSortChange={(value) => {
            setSort(value)
            setPage(1)
          }}
          state={state}
          onRetry={() => setState('ready')}
        />
        {detail && (
          <DetailsPanel title={`Detalle de ${detail.id}`}>
            <StatusPill tone="info">Información DEMO</StatusPill>
            <p>{detail.name}</p>
            <dl>
              <dt>Categoría</dt>
              <dd>{detail.category}</dd>
              <dt>Valor de prueba</dt>
              <dd>{number.format(detail.value)}</dd>
            </dl>
            <Button variant="secondary" onClick={() => setDetail(null)}>
              Cerrar detalle
            </Button>
          </DetailsPanel>
        )}
      </div>
    </section>
  )
}
