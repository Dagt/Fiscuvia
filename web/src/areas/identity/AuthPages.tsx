import { useEffect, useState, type PropsWithChildren } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router'
import {
  ArrowRight,
  Building2,
  Check,
  ChevronLeft,
  LockKeyhole,
  LogOut,
  ShieldCheck,
} from 'lucide-react'
import { Brand } from '../../shared/ui/Brand'
import { Button } from '../../shared/ui/button'
import { LoadingState, PermissionState } from '../../shared/ui/States'
import { ApiClientError } from '../../shared/api/client'
import {
  authStatus,
  authorizeTenant,
  bffBase,
  currentIdentity,
  getCsrf,
  logout,
  type Membership,
} from '../../shared/api/session-client'
import './auth.css'

function AuthFrame({ children }: PropsWithChildren) {
  return (
    <main className="auth-layout" id="contenido">
      <aside className="auth-brand-panel">
        <Link to="/login" aria-label="Fiscuvia, acceso">
          <Brand />
        </Link>
        <div className="auth-brand-copy">
          <span className="auth-eyebrow">UN ESPACIO PARA TU EQUIPO</span>
          <h1>
            Todo tu despacho,
            <br />
            <em>una sola vía.</em>
          </h1>
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
      <section className="auth-workspace">
        <div className="auth-content">{children}</div>
        <footer>Fiscuvia · Hecho para tu despacho.</footer>
      </section>
    </main>
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
      <h2>{title}</h2>
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
const roleLabel: Record<Membership['role'], string> = {
  FIRM_OWNER_ADMIN: 'Administración del despacho',
  FIRM_SUPERVISOR: 'Supervisión',
  ACCOUNTANT: 'Contabilidad',
  ASSISTANT: 'Asistencia',
}
export function SessionPage() {
  const navigate = useNavigate()
  const identity = useQuery({
    queryKey: ['current-identity'],
    queryFn: currentIdentity,
    retry: false,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
  const [selected, setSelected] = useState<Membership | null>(null)
  const [busy, setBusy] = useState(false)
  const [denied, setDenied] = useState(false)
  const [failed, setFailed] = useState(false)
  const expired =
    identity.error instanceof ApiClientError && identity.error.status === 401
  useEffect(() => {
    if (expired) void navigate('/login?estado=expirada', { replace: true })
  }, [expired, navigate])
  async function choose(tenantId: string) {
    setBusy(true)
    setDenied(false)
    setFailed(false)
    try {
      setSelected(await authorizeTenant(tenantId))
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401)
        await navigate('/login?estado=expirada', { replace: true })
      else if (error instanceof ApiClientError && error.status === 403)
        setDenied(true)
      else setFailed(true)
    } finally {
      setBusy(false)
    }
  }
  async function exit() {
    setBusy(true)
    try {
      await logout()
      await navigate('/login', { replace: true })
      window.location.reload()
    } catch {
      setFailed(true)
      setBusy(false)
    }
  }
  return (
    <AuthFrame>
      <AuthHeading
        title={selected ? 'Tu acceso está listo' : 'Elige tu despacho'}
        description={
          selected
            ? 'Tu membresía fue verificada por el servidor para este despacho.'
            : 'Sólo aparecen los despachos con una membresía activa para tu identidad.'
        }
      />
      {identity.isPending || busy ? (
        <LoadingState />
      ) : identity.isError ? (
        <div className="auth-notice" role="alert">
          No pudimos verificar tu sesión.
          <Button variant="outline" onClick={() => void identity.refetch()}>
            Reintentar
          </Button>
        </div>
      ) : (
        identity.data && (
          <>
            <nav className="auth-session-nav" aria-label="Sesión">
              <span>
                <Check size={16} aria-hidden="true" /> Identidad verificada
              </span>
              <Button variant="ghost" onClick={() => void exit()}>
                <LogOut size={16} aria-hidden="true" /> Cerrar sesión
              </Button>
            </nav>
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
            {selected ? (
              <div className="auth-authorized">
                <Building2 size={32} aria-hidden="true" />
                <h3>Despacho autorizado</h3>
                <p>{roleLabel[selected.role]}</p>
                <p>Las áreas fiscales se incorporarán en próximas entregas.</p>
                <Button
                  variant="outline"
                  onClick={() => {
                    setSelected(null)
                    void identity.refetch()
                  }}
                >
                  <ChevronLeft size={16} aria-hidden="true" /> Cambiar de
                  despacho
                </Button>
              </div>
            ) : identity.data.memberships.length === 0 ? (
              <div className="auth-notice">
                <Building2 size={28} aria-hidden="true" />
                <h3>Aún no tienes despachos asignados</h3>
                <p>
                  Tu identidad está verificada. Solicita una invitación a la
                  persona administradora de tu despacho.
                </p>
              </div>
            ) : (
              <div className="auth-memberships">
                {identity.data.memberships.map((member, index) => (
                  <button
                    key={`${member.membershipId}-${member.role}`}
                    className="auth-membership"
                    onClick={() => void choose(member.tenantId)}
                  >
                    <Building2 aria-hidden="true" />
                    <span>
                      <strong>Despacho {index + 1}</strong>
                      <small>{roleLabel[member.role]}</small>
                    </span>
                    <ArrowRight size={18} aria-hidden="true" />
                  </button>
                ))}
              </div>
            )}
            {!identity.data.privilegedMfaVerified && (
              <p className="auth-security-note">
                Las acciones administrativas requieren verificación MFA
                reciente. El acceso privilegiado permanece bloqueado.
              </p>
            )}
          </>
        )
      )}
    </AuthFrame>
  )
}
