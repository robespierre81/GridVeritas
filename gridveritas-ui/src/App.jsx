import { NavLink, Routes, Route } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage'
import SourcesPage from './pages/SourcesPage'
import AttestationsPage from './pages/AttestationsPage'
import VerifyPage from './pages/VerifyPage'
import AnomaliesPage from './pages/AnomaliesPage'
import AuditPage from './pages/AuditPage'
import ProofPage from './pages/ProofPage'
import AuditTrailPage from './pages/AuditTrailPage'
import FederationPage from './pages/FederationPage'
import SettlementPage from './pages/SettlementPage'
import LoginPanel from './components/LoginPanel'

export default function App() {
  return (
    <div className="layout">
      <aside className="sidebar" style={{ display: 'flex', flexDirection: 'column' }}>
        <h1>GridVeritas</h1>
        <nav>
          <NavLink to="/" end>Overview</NavLink>
          <NavLink to="/sources">Sources</NavLink>
          <NavLink to="/attestations">Attestations</NavLink>
          <NavLink to="/proof">Proof &amp; Anchor</NavLink>
          <NavLink to="/verify">Verify</NavLink>
          <NavLink to="/anomalies">Anomalies</NavLink>
          <NavLink to="/audit-trail">Audit Trail</NavLink>
          <NavLink to="/federation">Federation</NavLink>
          <NavLink to="/settlements">DER MV&amp;S</NavLink>
          <NavLink to="/audit">Audit Assistant</NavLink>
        </nav>
        <LoginPanel />
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/sources" element={<SourcesPage />} />
          <Route path="/attestations" element={<AttestationsPage />} />
          <Route path="/proof" element={<ProofPage />} />
          <Route path="/verify" element={<VerifyPage />} />
          <Route path="/anomalies" element={<AnomaliesPage />} />
          <Route path="/audit-trail" element={<AuditTrailPage />} />
          <Route path="/federation" element={<FederationPage />} />
          <Route path="/settlements" element={<SettlementPage />} />
          <Route path="/audit" element={<AuditPage />} />
        </Routes>
      </main>
    </div>
  )
}
