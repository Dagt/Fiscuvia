import { Navigate, Route, Routes } from 'react-router'
import { CatalogPage } from '../areas/component-catalog/CatalogPage'
import { WelcomePage } from '../areas/welcome/WelcomePage'
import { StatesPage } from '../areas/interface-demo/StatesPage'
import { NotFoundState } from '../shared/ui/States'
import { AppShell } from './AppShell'
import { LoginPage, SessionPage } from '../areas/identity/AuthPages'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<SessionPage />}>
        <Route path="/app" element={null} />
        <Route path="/app/taxpayers" element={null} />
      </Route>
      <Route
        path="/desarrollo/componentes"
        element={
          <AppShell>
            <CatalogPage />
          </AppShell>
        }
      />
      <Route
        path="/desarrollo"
        element={
          <AppShell>
            <WelcomePage />
          </AppShell>
        }
      />
      <Route
        path="/desarrollo/estados"
        element={
          <AppShell>
            <StatesPage />
          </AppShell>
        }
      />
      <Route
        path="*"
        element={
          <AppShell>
            <div className="page-container surface not-found">
              <NotFoundState />
            </div>
          </AppShell>
        }
      />
    </Routes>
  )
}
