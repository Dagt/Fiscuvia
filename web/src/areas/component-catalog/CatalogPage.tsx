import { PageHeader } from '../../shared/ui/PageHeader'
import { Brand } from '../../shared/ui/Brand'
import { StatCard, type ViewState } from '../../shared/ui/StatCard'
import { StatusPill, type Tone } from '../../shared/ui/StatusPill'
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PermissionState,
} from '../../shared/ui/States'
import { useNotification } from '../../shared/presentation/notification-context'
import { CatalogControls } from './CatalogControls'
import { CatalogData } from './CatalogData'
import './catalog.css'

export function CatalogPage() {
  const notify = useNotification()
  return (
    <div className="page-container catalog-page">
      <PageHeader
        title="Una sola identidad visual"
        description="Biblioteca de componentes Fiscuvia: composición, interacción y estados compartidos."
        eyebrow="CATÁLOGO DE DESARROLLO"
        action={<StatusPill tone="info">Demostración visual</StatusPill>}
      />
      <div className="catalog-intro">
        <div>
          <Brand />
          <p>Todo tu despacho, una sola vía.</p>
        </div>
        <p>
          Esta página es una herramienta de desarrollo. Los registros, valores y
          acciones son DEMO; no representan actividad fiscal ni permisos reales.
        </p>
      </div>
      <nav className="catalog-index" aria-label="Secciones del catálogo">
        <a href="#fundamentos">Identidad y tokens</a>
        <a href="#controles">Controles y formularios</a>
        <a href="#tabla">Tabla y filtros</a>
        <a href="#estados">Estados y notificaciones</a>
      </nav>
      <section
        className="catalog-section"
        id="fundamentos"
        aria-labelledby="foundation-title"
      >
        <div className="section-heading">
          <div>
            <p className="eyebrow">FUNDAMENTOS APROBADOS</p>
            <h2 id="foundation-title">Sobriedad, contraste y espacio</h2>
            <p>
              Inter local · Iconografía Lucide · Navegación navy · Acentos teal
            </p>
          </div>
        </div>
        <div className="token-grid">
          {[
            ['Navy', 'navy'],
            ['Teal principal', 'primary'],
            ['Superficie', 'surface'],
            ['Fondo', 'canvas'],
            ['Texto principal', 'ink'],
            ['Texto secundario', 'muted'],
          ].map(([label, token]) => (
            <div className="token-swatch" key={token}>
              <span
                style={{ background: `var(--${token})` }}
                aria-hidden="true"
              />
              <strong>{label}</strong>
              <code>--{token}</code>
            </div>
          ))}
        </div>
        <div className="type-specimens">
          <p className="specimen-title">Claridad desde el primer vistazo</p>
          <p className="specimen-section">Una jerarquía que organiza</p>
          <p>
            Texto de lectura · 14 px / 1.6. Nombres largos, acciones y datos
            conservan su lugar.
          </p>
          <p className="field-help">
            Ayuda y contexto · 12 px / 1.6. El color nunca es la única señal.
          </p>
        </div>
        <div
          className="spacing-specimens"
          aria-label="Escala de espacios en píxeles"
        >
          {[4, 8, 12, 16, 24, 32, 48].map((value) => (
            <div key={value}>
              <span
                style={{ width: `var(--space-${value})` }}
                aria-hidden="true"
              />
              <small>{value}</small>
            </div>
          ))}
        </div>
      </section>
      <CatalogControls />
      <CatalogData />
      <section
        className="catalog-section"
        id="estados"
        aria-labelledby="states-title"
      >
        <div className="section-heading">
          <div>
            <p className="eyebrow">RESPUESTAS TRANSVERSALES</p>
            <h2 id="states-title">Cada estado tiene una respuesta</h2>
            <p>
              Estados explícitos sin ceros ficticios ni promesas de
              funcionalidad.
            </p>
          </div>
        </div>
        <div className="component-actions">
          {(['success', 'warning', 'danger', 'info', 'neutral'] as const).map(
            (tone: Tone, index) => (
              <StatusPill key={tone} tone={tone}>
                {
                  [
                    'Correcto DEMO',
                    'Atención DEMO',
                    'Error DEMO',
                    'Información DEMO',
                    'Pendiente DEMO',
                  ][index]!
                }
              </StatusPill>
            ),
          )}
        </div>
        <div className="stat-grid">
          {(
            ['ready', 'loading', 'empty', 'error', 'unauthorized'] as const
          ).map((state: ViewState) => (
            <StatCard
              key={state}
              title={
                state === 'ready'
                  ? 'Referencias DEMO'
                  : `Indicador DEMO · ${{ loading: 'carga', empty: 'vacío', error: 'error', unauthorized: 'sin acceso', ready: '' }[state]}`
              }
              value="125"
              description="Sólo demostración de presentación."
              state={state}
              onRetry={() =>
                notify({ message: 'Reintento DEMO solicitado.', tone: 'info' })
              }
            />
          ))}
        </div>
        <div className="state-specimens">
          <LoadingState />
          <EmptyState />
          <ErrorState
            onRetry={() => notify('Reintento DEMO. No se consultaron datos.')}
          />
          <PermissionState />
        </div>
      </section>
    </div>
  )
}
