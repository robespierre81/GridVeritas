import { NavLink, Routes, Route } from 'react-router-dom'
import SourcesPage from './pages/SourcesPage'
import AttestationsPage from './pages/AttestationsPage'
import VerifyPage from './pages/VerifyPage'
import AuditPage from './pages/AuditPage'

export default function App() {
  return (
    <div className="layout">
      <aside className="sidebar">
        <h1>GridVeritas</h1>
        <nav>
          <NavLink to="/" end>Sources</NavLink>
          <NavLink to="/attestations">Attestations</NavLink>
          <NavLink to="/verify">Verify</NavLink>
          <NavLink to="/audit">Audit</NavLink>
        </nav>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<SourcesPage />} />
          <Route path="/attestations" element={<AttestationsPage />} />
          <Route path="/verify" element={<VerifyPage />} />
          <Route path="/audit" element={<AuditPage />} />
        </Routes>
      </main>
    </div>
  )
}
