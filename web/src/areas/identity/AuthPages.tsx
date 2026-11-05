import { useEffect, type PropsWithChildren } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import {
  ArrowRight,
  Building2,
  LockKeyhole,
  LogOut,
  ShieldCheck,
} from 'lucide-react'
import { Brand } from '../../shared/ui/Brand'
import { Button } from '../../shared/ui/button'
import { LoadingState, PermissionState } from '../../shared/ui/States'
import { AppShell } from '../../app/AppShell'
import { PageHeader } from '../../shared/ui/PageHeader'
import { AuthorizedTenantContext } from './AuthorizedTenantContext'
import { roleLabel } from './role-labels'
import { useAuthorizedSession } from './useAuthorizedSession'
import { authStatus, bffBase, getCsrf } from '../../shared/api/session-client'
import './auth.css'
import { FirmSettings } from '../tenants/FirmSettings'

function AuthFrame({ children }: PropsWithChildren) {
  return (
    <div className="auth-layout">
      <a className="skip-link" href="#contenido">
        Saltar al contenido
      </a>
      <aside className="auth-brand-panel">
        <Link to="/login" aria-label="Fiscuvia, acceso">
          <Brand />
        </Link>
        <div className="auth-brand-copy">
          <span className="auth-eyebrow">UN ESPACIO PARA TU EQUIPO</span>
          <p className="auth-brand-title">
            Todo tu despacho,
            <br />
            <em>una sola vía.</em>
          </p>
          <p>Un acceso seguro. Cada despacho en su propio espacio.</p>
          <div className="auth-assurance">
            <ShieldCheck size={22} aria-hidden="true" />
            <div>
              <strong>La confianza empieza en el acceso</strong>
              <span>Identidad verificada y permisos por despacho.</span>
            </div>
          </div>
        </div>
        <span className="auth-local">
          Entorno local · Proveedor de producción pendiente
        </span>
      </aside>
      <main className="auth-workspace" id="contenido" tabIndex={-1}>
        <div className="auth-content">{children}</div>
        <footer>Fiscuvia · Hecho para tu despacho.</footer>
      </main>
    </div>
  )
}
function AuthHeading({
  title,
  description,
}: {
  title: string
  description: string
}) {
  useEffect(() => {
    document.title = `${title} | Fiscuvia`
  }, [title])
  return (
    <header className="auth-heading">
      <div className="auth-symbol">
        <LockKeyhole aria-hidden="true" />
      </div>
      <h1>{title}</h1>
      <p>{description}</p>
    </header>
  )
}
export function LoginPage() {
  const [params] = useSearchParams()
  const status = useQuery({
    queryKey: ['auth-status'],
    queryFn: authStatus,
    retry: false,
  })
  const csrf = useQuery({
    queryKey: ['login-csrf'],
    queryFn: getCsrf,
    retry: false,
    staleTime: 0,
  })
  const failed = params.get('error') === 'autenticacion'
  return (
    <AuthFrame>
      <AuthHeading
        title={
          failed ? 'No pudimos verificar tu acceso' : 'Bienvenido a Fiscuvia'
        }
        description={
          failed
            ? 'La autenticación no se completó. Inicia de nuevo para obtener una sesión segura.'
            : 'Accede con la identidad autorizada para colaborar con tu equipo.'
        }
      />
      {params.get('estado') === 'expirada' && (
        <p className="auth-notice" role="status">
          Tu sesión terminó. Vuelve a iniciar sesión para continuar.
        </p>
      )}
      {status.isPending || csrf.isPending ? (
        <LoadingState />
      ) : status.isError || csrf.isError ? (
        <div className="auth-notice" role="alert">
          No pudimos conectar con el servicio de acceso.
          <Button
            variant="outline"
            onClick={() => {
              void status.refetch()
              void csrf.refetch()
            }}
          >
            Reintentar
          </Button>
        </div>
      ) : !status.data ? (
        <div className="auth-notice" role="status">
          <strong>Acceso local sin configurar</strong>
          <p>
            El emisor OIDC debe estar disponible antes de iniciar sesión. No hay
            acceso de demostración.
          </p>
        </div>
      ) : (
        <form action={`${bffBase()}/auth/login`} method="post">
          <input type="hidden" name="_csrf" value={csrf.data?.token} />
          <Button type="submit" className="auth-enter">
            Continuar con acceso seguro{' '}
            <ArrowRight size={18} aria-hidden="true" />
          </Button>
        </form>
      )}
      <div className="auth-security-note">
        <ShieldCheck size={17} aria-hidden="true" />
        <p>
          La contraseña y el segundo factor se verifican en el proveedor de
          identidad. Fiscuvia no te los solicitará aquí.
        </p>
      </div>
      <Link className="auth-dev-link" to="/desarrollo">
        Explorar la base visual de desarrollo{' '}
        <ArrowRight size={15} aria-hidden="true" />
      </Link>
    </AuthFrame>
  )
}
export function SessionPage() {
  const {
    identity,
    selected,
    busy,
    denied,
    failed,
    choose,
    change,
    exit,
    unavailable,
  } = useAuthorizedSession()
  useEffect(() => {
    if (selected) document.getElementById('contenido')?.focus()
  }, [selected])
  if (identity.isPending || identity.isError || !identity.data)
    return (
      <AuthFrame>
        <AuthHeading
          title="Verifica tu sesión"
          description="Estamos comprobando tu acceso seguro."
        />
        {identity.isPending ? (
          <LoadingState />
        ) : (
          <div className="auth-notice" role="alert">
            No pudimos verificar tu sesión.
            <Button variant="secondary" onClick={() => void identity.refetch()}>
              Reintentar
            </Button>
          </div>
        )}
      </AuthFrame>
    )
  return (
    <AppShell
      authenticated
      pageTitle={selected ? 'Tu despacho' : 'Elige tu despacho'}
      tenantContext={
        selected ? (
          <Button
            variant="ghost"
            aria-label={`Contexto actual: ${selected.commercialName || 'Nombre comercial pendiente'}`}
            title={selected.commercialName || 'Nombre comercial pendiente'}
            onClick={() => void change()}
          >
            <Building2 size={18} aria-hidden="true" />
            <span className="firm-context-name">
              {selected.commercialName || 'Nombre comercial pendiente'}
            </span>
          </Button>
        ) : (
          <span className="context-label">Sin despacho seleccionado</span>
        )
      }
      sessionActions={
        <Button
          variant="ghost"
          aria-label="Cerrar sesión"
          disabled={busy}
          onClick={() => void exit()}
        >
          <LogOut size={18} aria-hidden="true" />
          <span className="logout-label">Cerrar sesión</span>
        </Button>
      }
    >
      <div className="page-container session-page">
        <PageHeader
          eyebrow="IDENTIDAD VERIFICADA"
          title={selected ? 'Configuración del despacho' : 'Elige tu despacho'}
          description={
            selected
              ? `${roleLabel[selected.role]} · Datos de contacto e identidad para tu equipo.`
              : 'Sólo aparecen los despachos con una membresía activa para tu identidad.'
          }
          secondaryActions={
            selected ? (
              <Button variant="secondary" onClick={() => void change()}>
                Cambiar de despacho
              </Button>
            ) : undefined
          }
        />
        {denied && (
          <div role="alert">
            <PermissionState />
          </div>
        )}
        {failed && (
          <p className="auth-notice" role="alert">
            No pudimos completar la operación. Vuelve a intentarlo.
          </p>
        )}
        {busy ? (
          <LoadingState />
        ) : selected ? (
          <>
            <FirmSettings
              userId={identity.data.userId}
              tenantId={selected.tenantId}
              onUnavailable={(status) => void unavailable(status)}
            />
          </>
        ) : (
          <AuthorizedTenantContext
            memberships={identity.data.memberships}
            onChoose={(id) => void choose(id)}
            pending={busy}
          />
        )}
        {!identity.data.privilegedMfaVerified && (
          <p className="auth-security-note">
            <ShieldCheck size={18} aria-hidden="true" />
            Las acciones administrativas requieren verificación MFA reciente. El
            acceso privilegiado permanece bloqueado.
          </p>
        )}
      </div>
    </AppShell>
  )
}
