import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { Button } from './button'
import { Field } from './Field'
import { StatusPill } from './StatusPill'
import { StatCard } from './StatCard'
import { ConfirmationDialog, DetailsPanel, SideDrawer } from './Panels'
import { DataTable, type TableSort } from './DataTable'
import { FilterBar } from './FilterBar'
import { NotificationProvider } from '../presentation/NotificationProvider'
import { useNotification } from '../presentation/notification-context'

describe('contratos visuales reutilizables', () => {
  it('un enlace pendiente no navega ni dispara acciones', async () => {
    const action = vi.fn()
    render(
      <Button asChild pending onClick={action}>
        <a href="#private-target">Continuar DEMO</a>
      </Button>,
    )
    const link = screen.getByRole('link', { name: 'Continuar DEMO' })
    expect(link).toHaveAttribute('aria-disabled', 'true')
    expect(link).toHaveAttribute('tabindex', '-1')
    await userEvent.click(link)
    expect(action).not.toHaveBeenCalled()
    expect(window.location.hash).not.toBe('#private-target')
  })
  it('bloquea acciones pendientes y conserva el nombre accesible', async () => {
    const action = vi.fn()
    render(
      <Button pending onClick={action}>
        Guardar ejemplo
      </Button>,
    )
    const button = screen.getByRole('button', { name: 'Guardar ejemplo' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(button)
    expect(action).not.toHaveBeenCalled()
  })
  it('conecta etiqueta, descripción y error sin depender de color', () => {
    render(
      <Field
        label="Nombre DEMO"
        required
        description="Sólo prueba"
        error="Escribe tres caracteres"
      />,
    )
    const input = screen.getByRole('textbox', { name: 'Nombre DEMO' })
    expect(input).toHaveAccessibleDescription(
      'Sólo prueba Escribe tres caracteres',
    )
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toBeRequired()
  })
  it('conserva readonly y disabled como estados distintos', () => {
    render(
      <>
        <Field label="Lectura" readOnly value="DEMO" />
        <Field label="Deshabilitado" disabled />
      </>,
    )
    expect(screen.getByLabelText('Lectura')).toHaveAttribute('readonly')
    expect(screen.getByLabelText('Deshabilitado')).toBeDisabled()
  })
  it('expresa el estado con texto además del icono', () => {
    render(<StatusPill tone="danger">Requiere atención DEMO</StatusPill>)
    expect(screen.getByText('Requiere atención DEMO')).toBeVisible()
    expect(document.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })
  it.each(['loading', 'empty', 'error', 'unauthorized'] as const)(
    'no revela el valor de StatCard en estado %s',
    (state) => {
      render(
        <StatCard
          title="Ejemplo"
          description="DEMO"
          state={state}
          value="9876543"
        />,
      )
      expect(screen.queryByText('9876543')).not.toBeInTheDocument()
    },
  )
  it('permite reintentar el indicador fallido', async () => {
    const retry = vi.fn()
    render(
      <StatCard
        title="Ejemplo"
        description="DEMO"
        state="error"
        onRetry={retry}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(retry).toHaveBeenCalledOnce()
  })
  it('confirma una vez, bloquea doble envío y restaura el foco', async () => {
    let finish!: () => void
    const action = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    )
    render(
      <ConfirmationDialog
        trigger={<Button>Reiniciar</Button>}
        title="Confirmar ejemplo"
        description="Sólo DEMO"
        confirmLabel="Confirmar"
        onConfirm={action}
        destructive
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reiniciar' }))
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(screen.getByRole('button', { name: 'Procesando…' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('alertdialog')).toBeVisible()
    finish()
    expect(
      await screen.findByRole('button', { name: 'Reiniciar' }),
    ).toBeVisible()
    await vi.waitFor(() =>
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument(),
    )
    expect(action).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Reiniciar' })).toHaveFocus()
  })
  it('mantiene el diálogo abierto con error seguro si falla la acción', async () => {
    render(
      <ConfirmationDialog
        trigger={<Button>Abrir</Button>}
        title="Confirmar"
        description="DEMO"
        confirmLabel="Continuar"
        onConfirm={() => Promise.reject(new Error('CANARY_PRIVATE'))}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }))
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Puedes reintentar',
    )
    expect(screen.queryByText('CANARY_PRIVATE')).not.toBeInTheDocument()
  })
  it.each(['loading', 'error', 'unauthorized'] as const)(
    'retira detalle y acciones del drawer en estado %s',
    async (state) => {
      const retry = vi.fn()
      render(
        <SideDrawer
          trigger={<Button>Abrir estado</Button>}
          title="Detalle"
          description="DEMO"
          state={state}
          onRetry={retry}
          footer={<Button>Acción privada</Button>}
        >
          <p>PRIVATE-CANARY</p>
        </SideDrawer>,
      )
      await userEvent.click(
        screen.getByRole('button', { name: 'Abrir estado' }),
      )
      expect(screen.queryByText('PRIVATE-CANARY')).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Acción privada' }),
      ).not.toBeInTheDocument()
      if (state === 'error') {
        await userEvent.click(
          screen.getByRole('button', { name: 'Reintentar' }),
        )
        expect(retry).toHaveBeenCalledOnce()
      }
    },
  )
  it('cierra drawer con Escape y devuelve foco al disparador', async () => {
    render(
      <SideDrawer
        trigger={<Button>Abrir panel</Button>}
        title="Detalle"
        description="DEMO"
      >
        <p>Contenido de prueba</p>
      </SideDrawer>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Abrir panel' }))
    expect(screen.getByRole('dialog')).toHaveAccessibleName('Detalle')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir panel' })).toHaveFocus()
  })
  it('DetailsPanel permanece inline y no crea un modal', () => {
    render(
      <DetailsPanel title="Detalle inline">
        <p>DEMO</p>
      </DetailsPanel>,
    )
    expect(
      screen.getByRole('complementary', { name: 'Detalle inline' }),
    ).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

const rows = [{ id: 'DEMO-1', name: 'Nombre de prueba' }]
function TableExample({
  state = 'ready',
}: {
  state?: 'ready' | 'loading' | 'empty' | 'error' | 'unauthorized'
}) {
  const [page, setPage] = useState(1)
  const [sort, setSort] = useState<TableSort>({
    column: 'name',
    direction: 'asc',
  })
  return (
    <DataTable
      label="Tabla DEMO"
      rows={rows}
      columns={[
        {
          id: 'name',
          header: 'Nombre',
          sortable: true,
          cell: (row) => row.name,
        },
      ]}
      rowKey={(row) => row.id}
      rowLabel={(row) => row.id}
      page={page}
      pageSize={10}
      total={21}
      onPageChange={setPage}
      sort={sort}
      onSortChange={setSort}
      state={state}
      onRetry={() => {}}
    />
  )
}
describe('listas controladas y notificaciones', () => {
  it('expone orden semántico y cambia sólo por callback', async () => {
    render(<TableExample />)
    expect(screen.getByRole('columnheader')).toHaveAttribute(
      'aria-sort',
      'ascending',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Nombre' }))
    expect(screen.getByRole('columnheader')).toHaveAttribute(
      'aria-sort',
      'descending',
    )
  })
  it('respeta límites de paginación y muestra el total explícito', async () => {
    render(<TableExample />)
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
    expect(screen.getByText(/Página 3 de 3/)).toBeVisible()
  })
  it.each(['loading', 'empty', 'error', 'unauthorized'] as const)(
    'retira filas y conteos en estado %s',
    (state) => {
      render(<TableExample state={state} />)
      expect(screen.queryByText('Nombre de prueba')).not.toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    },
  )
  it('muestra una alternativa móvil con etiquetas por campo', () => {
    const original = window.matchMedia
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    render(<TableExample />)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('list')).getByText('Nombre de prueba'),
    ).toBeVisible()
    window.matchMedia = original
  })
  it('controla búsqueda y limpieza sin peticiones a APIs', async () => {
    const search = vi.fn(),
      clear = vi.fn()
    render(
      <FilterBar
        search=""
        onSearchChange={search}
        filters={[]}
        onClear={clear}
        activeCount={1}
      />,
    )
    await userEvent.type(screen.getByRole('searchbox'), 'A')
    expect(search).toHaveBeenCalledWith('A')
    await userEvent.click(
      screen.getByRole('button', { name: 'Limpiar filtros' }),
    )
    expect(clear).toHaveBeenCalledOnce()
  })
  it('notifica sin robar foco y permite acción y cierre', async () => {
    const action = vi.fn()
    function Example() {
      const notify = useNotification()
      return (
        <Button
          onClick={() =>
            notify({
              message: 'Aviso DEMO',
              tone: 'info',
              action: { label: 'Deshacer', onClick: action },
            })
          }
        >
          Notificar
        </Button>
      )
    }
    render(
      <NotificationProvider>
        <Example />
      </NotificationProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Notificar' }))
    expect(screen.getByRole('status')).toHaveTextContent('Aviso DEMO')
    expect(screen.getByRole('button', { name: 'Notificar' })).toHaveFocus()
    await userEvent.click(screen.getByRole('button', { name: 'Deshacer' }))
    expect(action).toHaveBeenCalledOnce()
    await userEvent.click(
      screen.getByRole('button', { name: 'Cerrar notificación' }),
    )
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })
})
