import { Navigate, Route, Routes } from 'react-router'
import { WelcomePage } from '../areas/welcome/WelcomePage'
import { StatesPage } from '../areas/interface-demo/StatesPage'
import { NotFoundState } from '../shared/ui/States'
import { AppShell } from './AppShell'

export function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/desarrollo" replace />} />
        <Route path="/desarrollo" element={<WelcomePage />} />
        <Route path="/desarrollo/estados" element={<StatesPage />} />
        <Route
          path="*"
          element={
            <div className="page-container surface not-found">
              <NotFoundState />
            </div>
          }
        />
      </Routes>
    </AppShell>
  )
}
