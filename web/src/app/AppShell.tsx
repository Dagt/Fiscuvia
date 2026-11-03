import {
  useEffect,
  useRef,
  useState,
  type PropsWithChildren,
  type ReactNode,
} from 'react'
import { useLocation } from 'react-router'
import * as Dialog from '@radix-ui/react-dialog'
import {
  ArrowUpRight,
  Building2,
  ChevronRight,
  Code2,
  Menu,
  X,
} from 'lucide-react'
import { Brand } from '../shared/ui/Brand'
import { Button } from '../shared/ui/button'
import { Navigation } from './Navigation'

function SidebarFooter({ authenticated = false }: { authenticated?: boolean }) {
  return (
    <div className="sidebar-footer">
      <p>
        Todo tu despacho,
        <br />
        <span>una sola vía.</span>
      </p>
      <ArrowUpRight size={22} aria-hidden="true" />
      <div className="sidebar-environment">
        <Code2 size={14} aria-hidden="true" />
        {authenticated ? 'Sesión protegida' : 'Vista demostrativa'}
      </div>
    </div>
  )
}

export function AppShell({
  children,
  authenticated = false,
  tenantContext,
  sessionActions,
  pageTitle,
}: PropsWithChildren<{
  authenticated?: boolean
  tenantContext?: ReactNode
  sessionActions?: ReactNode
  pageTitle?: string
}>) {
  const [menuOpen, setMenuOpen] = useState(false)
  const main = useRef<HTMLElement>(null)
  const focusContent = useRef(false)
  const location = useLocation()
  const previousPath = useRef(location.pathname)
  const title =
    pageTitle ??
    {
      '/desarrollo': 'Bienvenida',
      '/desarrollo/estados': 'Estados de interfaz',
      '/desarrollo/componentes': 'Biblioteca de componentes',
    }[location.pathname] ??
    'Página no encontrada'

  useEffect(() => {
    document.title = `${title} | Fiscuvia`
    const path = location.pathname
    if (path !== previousPath.current) {
      main.current?.focus({ preventScroll: true })
      window.scrollTo({ top: 0 })
    }
    previousPath.current = path
  }, [location.pathname, title])

  return (
    <div className="app-shell">
      <a className="skip-link" href="#contenido">
        Saltar al contenido
      </a>
      <aside className="desktop-sidebar">
        <Brand />
        <Navigation authenticated={authenticated} />
        <SidebarFooter authenticated={authenticated} />
      </aside>
      <div className="app-workspace">
        <header className="topbar">
          <div className="topbar-leading">
            <Dialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
              <Dialog.Trigger asChild>
                <Button
                  className="mobile-menu-button"
                  variant="ghost"
                  aria-label="Abrir menú de navegación"
                >
                  <Menu size={22} aria-hidden="true" />
                </Button>
              </Dialog.Trigger>
              <Dialog.Portal>
                <Dialog.Overlay className="menu-overlay" />
                <Dialog.Content
                  className="mobile-sidebar"
                  onCloseAutoFocus={(event) => {
                    if (focusContent.current) {
                      event.preventDefault()
                      main.current?.focus()
                      focusContent.current = false
                    }
                  }}
                >
                  <Dialog.Title className="sr-only">
                    Navegación de Fiscuvia
                  </Dialog.Title>
                  <Dialog.Description className="sr-only">
                    Explora la bienvenida y los estados demostrativos de la
                    interfaz.
                  </Dialog.Description>
                  <div className="mobile-brand-row">
                    <Brand />
                    <Dialog.Close asChild>
                      <Button
                        variant="ghost"
                        className="menu-close"
                        aria-label="Cerrar menú de navegación"
                      >
                        <X size={22} aria-hidden="true" />
                      </Button>
                    </Dialog.Close>
                  </div>
                  <Navigation
                    authenticated={authenticated}
                    onNavigate={() => {
                      focusContent.current = true
                      setMenuOpen(false)
                    }}
                  />
                  <SidebarFooter authenticated={authenticated} />
                </Dialog.Content>
              </Dialog.Portal>
            </Dialog.Root>
            <span className="breadcrumb-root">Tu espacio</span>
            <ChevronRight size={14} aria-hidden="true" />
            <span className="breadcrumb-current">{title}</span>
          </div>
          <div className="topbar-actions">
            {tenantContext ?? (
              <div className="context-label">
                <Building2 size={17} aria-hidden="true" />
                <span>Sin despacho conectado</span>
              </div>
            )}
            {sessionActions}
          </div>
        </header>
        <main ref={main} id="contenido" tabIndex={-1}>
          {children}
        </main>
        <footer className="workspace-footer">
          <span>Fiscuvia · Hecho para tu despacho.</span>
          <span>
            {authenticated
              ? 'Acceso verificado · Entorno local'
              : 'Entorno de desarrollo'}
          </span>
        </footer>
      </div>
    </div>
  )
}
