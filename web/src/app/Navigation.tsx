import { NavLink } from 'react-router'
import {
  Blocks,
  CalendarDays,
  ChartNoAxesCombined,
  CheckSquare2,
  Files,
  Home,
  ReceiptText,
  UsersRound,
  Wallet,
} from 'lucide-react'

const futureAreas = [
  { label: 'Contribuyentes', Icon: UsersRound },
  { label: 'Obligaciones', Icon: CalendarDays },
  { label: 'Tareas', Icon: CheckSquare2 },
  { label: 'Documentos', Icon: Files },
  { label: 'Honorarios', Icon: Wallet },
  { label: 'Reportes', Icon: ChartNoAxesCombined },
  { label: 'CFDI', Icon: ReceiptText },
]

export function Navigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Navegación principal">
      <p className="nav-heading">TU ESPACIO</p>
      <NavLink end to="/desarrollo" className="nav-link" onClick={onNavigate}>
        <Home size={19} aria-hidden="true" />
        Bienvenida
      </NavLink>
      <NavLink
        to="/desarrollo/estados"
        className="nav-link"
        onClick={onNavigate}
      >
        <Blocks size={19} aria-hidden="true" />
        Estados de interfaz
      </NavLink>
      <div className="nav-section-heading">
        <p className="nav-heading">GESTIÓN DEL DESPACHO</p>
        <span>Próximamente</span>
      </div>
      <ul className="future-navigation">
        {futureAreas.map(({ label, Icon }) => (
          <li key={label}>
            <button
              disabled
              className="nav-link nav-future"
              title="Pendiente de implementación"
            >
              <Icon size={19} aria-hidden="true" />
              <span>{label}</span>
            </button>
          </li>
        ))}
      </ul>
    </nav>
  )
}
