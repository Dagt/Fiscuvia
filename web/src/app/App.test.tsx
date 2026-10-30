import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { AppProviders } from './AppProviders'

function renderRoute(path = '/desarrollo') {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </AppProviders>,
  )
}

describe('rutas y shell', () => {
  it.each(['/', '/desarrollo'])('muestra la bienvenida en %s', async (path) => {
    renderRoute(path)
    expect(
      await screen.findByRole('heading', { name: 'Bienvenido a Fiscuvia' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Sin despacho conectado')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Bienvenida' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(document.title).toBe('Bienvenida | Fiscuvia')
  })

  it('navega a los estados y devuelve el foco al contenido', async () => {
    const user = userEvent.setup()
    renderRoute()
    await user.click(screen.getByRole('link', { name: 'Explorar la interfaz' }))
    expect(
      screen.getByRole('heading', { name: 'Cada estado, una respuesta clara' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Estados de interfaz' }),
    ).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('main')).toHaveFocus()
    expect(document.title).toBe('Estados de interfaz | Fiscuvia')
  })

  it('mantiene las áreas futuras deshabilitadas y sin enlaces de negocio', () => {
    renderRoute()
    const nav = within(
      screen.getByRole('navigation', { name: 'Navegación principal' }),
    )
    for (const name of [
      'Contribuyentes',
      'Obligaciones',
      'Tareas',
      'Documentos',
      'Honorarios',
      'Reportes',
      'CFDI',
    ]) {
      expect(nav.getByRole('button', { name })).toBeDisabled()
      expect(nav.queryByRole('link', { name })).not.toBeInTheDocument()
    }
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
  })

  it.each(['/inexistente', '/app/taxpayers', '/login'])(
    'no simula un módulo en %s y permite volver',
    async (path) => {
      const user = userEvent.setup()
      renderRoute(path)
      expect(
        screen.getByRole('heading', { name: 'No encontramos esta página' }),
      ).toBeInTheDocument()
      await user.click(screen.getByRole('link', { name: 'Volver al inicio' }))
      expect(
        screen.getByRole('heading', { name: 'Bienvenido a Fiscuvia' }),
      ).toBeInTheDocument()
    },
  )
})

describe('estados de presentación', () => {
  it.each([
    ['vacio', 'Un espacio para empezar'],
    ['carga', 'Preparando la vista'],
    ['error', 'No pudimos cargar esta vista'],
    ['permisos', 'Necesitas permisos para continuar'],
    ['desconocido', 'Un espacio para empezar'],
  ])('abre directamente el estado %s', (state, heading) => {
    renderRoute(`/desarrollo/estados?estado=${state}`)
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    expect(
      screen.getByText(/No representan datos ni permisos de una cuenta real/),
    ).toBeInTheDocument()
  })

  it('recupera el ejemplo, anuncia el resultado y permite cerrar el aviso', async () => {
    const user = userEvent.setup()
    renderRoute('/desarrollo/estados?estado=error')
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(
      screen.getByRole('heading', { name: 'Demostración recuperada' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(
      'El reintento de demostración terminó correctamente.',
    )
    await user.click(
      screen.getByRole('button', { name: 'Cerrar notificación' }),
    )
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    await user.click(screen.getByRole('link', { name: 'Sin elementos' }))
    await user.click(screen.getByRole('link', { name: 'Error recuperable' }))
    expect(
      screen.getByRole('button', { name: 'Reintentar' }),
    ).toBeInTheDocument()
  })
})
