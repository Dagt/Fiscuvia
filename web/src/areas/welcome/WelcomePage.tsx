import { Link } from 'react-router'
import {
  ArrowRight,
  Check,
  Compass,
  Layers3,
  MonitorSmartphone,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { Button } from '../../shared/ui/button'
import { PageHeader } from '../../shared/ui/PageHeader'

const foundations = [
  {
    Icon: Compass,
    title: 'Un lugar para cada cosa',
    description:
      'Navegación compartida y un contexto visible en todas las vistas.',
  },
  {
    Icon: Layers3,
    title: 'Claridad en cada estado',
    description:
      'Respuestas consistentes al cargar, empezar o recuperar una vista.',
  },
  {
    Icon: MonitorSmartphone,
    title: 'A tu ritmo, en tu pantalla',
    description:
      'Una experiencia que se adapta al escritorio, la tableta y el móvil.',
  },
]

export function WelcomePage() {
  return (
    <div className="page-container">
      <PageHeader
        title="Bienvenido a Fiscuvia"
        description="El punto de partida para organizar el día a día de tu despacho."
        action={
          <span className="demo-badge">
            <span />
            Vista demostrativa
          </span>
        }
      />
      <section className="welcome-hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <div className="hero-eyebrow">
            <Sparkles size={16} aria-hidden="true" />
            UNA BASE PARA LO QUE VIENE
          </div>
          <h2 id="hero-title">
            Todo tu despacho,
            <br />
            <span>una sola vía.</span>
          </h2>
          <p>
            Estamos construyendo un espacio más claro para tu trabajo. Empieza
            por conocer la navegación y los estados de esta interfaz.
          </p>
          <Button asChild className="hero-button">
            <Link to="/desarrollo/estados">
              Explorar la interfaz
              <ArrowRight size={17} aria-hidden="true" />
            </Link>
          </Button>
        </div>
        <div className="hero-illustration" aria-hidden="true">
          <div className="via-line" />
          <div className="via-node node-one">
            <Compass size={20} />
          </div>
          <div className="via-node node-two">
            <Layers3 size={20} />
          </div>
          <div className="via-node node-three">
            <Check size={20} />
          </div>
          <div className="via-label label-one">Todo conectado.</div>
          <div className="via-label label-two">Todo en su lugar.</div>
          <div className="via-caption">F I S C U V I A</div>
        </div>
      </section>
      <div className="welcome-grid">
        <section
          className="surface foundations"
          aria-labelledby="foundations-title"
        >
          <div className="section-header">
            <h2 id="foundations-title">Un buen comienzo</h2>
            <span className="subtle-label">Fundamentos de la interfaz</span>
          </div>
          <ul>
            {foundations.map(({ Icon, title, description }) => (
              <li key={title}>
                <span className="feature-icon">
                  <Icon size={20} aria-hidden="true" />
                </span>
                <div>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </div>
                <Check className="feature-check" size={17} aria-hidden="true" />
              </li>
            ))}
          </ul>
        </section>
        <aside
          className="surface development-note"
          aria-labelledby="scope-title"
        >
          <span className="feature-icon">
            <ShieldCheck size={22} aria-hidden="true" />
          </span>
          <h2 id="scope-title">Un espacio en construcción</h2>
          <p>
            Esta vista muestra la base visual de Fiscuvia. No hay un despacho,
            una sesión ni información de clientes conectados.
          </p>
          <div className="note-divider" />
          <span className="small-eyebrow">LAS SIGUIENTES ETAPAS</span>
          <p>
            Las secciones del despacho se habilitarán conforme se implementen.
            Por ahora, aparecen como próximas.
          </p>
          <Link className="text-link" to="/desarrollo/estados">
            Conocer los estados
            <ArrowRight size={15} aria-hidden="true" />
          </Link>
        </aside>
      </div>
      <div className="development-caption">
        <span className="caption-dot" />
        <p>
          Demostración de desarrollo · Sin datos fiscales ni métricas de
          clientes.
        </p>
      </div>
    </div>
  )
}
