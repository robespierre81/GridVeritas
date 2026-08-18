import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'

const CARDS = [
  { key: 'sources',       label: 'Sources',            accent: '#0d9488', to: '/sources' },
  { key: 'attestations',  label: 'Attestations',       accent: '#2563eb', to: '/attestations' },
  { key: 'merkleRoots',   label: 'Merkle roots',       accent: '#7c3aed', to: null },
  { key: 'anchors',       label: 'External anchors',   accent: '#7c3aed', to: null },
  { key: 'anomalies',     label: 'Anomaly findings',   accent: '#d97706', to: '/anomalies' },
  { key: 'verifications', label: 'Verifications',      accent: '#0d9488', to: '/audit-trail' },
  { key: 'auditEvents',   label: 'Audit events',       accent: '#64748b', to: '/audit-trail' },
  { key: 'peerRoots',     label: 'Peer roots (M13)',   accent: '#0369a1', to: '/federation' },
  { key: 'settlements',   label: 'Settlements (M14)',  accent: '#b45309', to: '/settlements' }
]

function fmtTime(iso) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

function ago(iso) {
  if (!iso) return '—'
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return `${s}s ago`
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`
  return `${Math.floor(s / 86400)}d ago`
}

export default function DashboardPage() {
  const [stats, setStats] = useState(null)
  const [cluster, setCluster] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true); setError(null)
    Promise.all([api.getStats(), api.listInstances()])
      .then(([s, c]) => { setStats(s); setCluster(c) })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }
  useEffect(() => {
    load()
    const id = setInterval(load, 10_000)
    return () => clearInterval(id)
  }, [])

  const Card = ({ c }) => {
    const value = stats ? (stats[c.key] ?? 0) : '—'
    const inner = (
      <div className="card" style={{ borderLeft: `4px solid ${c.accent}`, margin: 0, height: '100%' }}>
        <div style={{ fontSize: '2.2rem', fontWeight: 700, color: c.accent, lineHeight: 1 }}>{value}</div>
        <div style={{ color: '#555', marginTop: 6 }}>{c.label}</div>
      </div>
    )
    return c.to
      ? <Link to={c.to} style={{ textDecoration: 'none', color: 'inherit' }}>{inner}</Link>
      : inner
  }

  const instances = [...(cluster?.instances ?? [])].sort((a, b) => {
    if (a.online !== b.online) return a.online ? -1 : 1
    return (a.instanceId || '').localeCompare(b.instanceId || '')
  })
  const onlineCount = cluster?.onlineCount ?? 0

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ marginBottom: 0 }}>Overview</h2>
        <button onClick={load} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </div>
      <p style={{ color: '#555', marginTop: 8 }}>
        Live counts across the verification fabric — source signatures, Merkle provenance, and
        external anchors, plus detective and audit signals.
      </p>

      {error && <div className="msg msg-err">{error}</div>}

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: '1rem', marginTop: '1rem'
      }}>
        <div className="card" style={{ borderLeft: '4px solid #059669', margin: 0, height: '100%' }}>
          <div style={{ fontSize: '2.2rem', fontWeight: 700, color: '#059669', lineHeight: 1 }}>
            {cluster ? onlineCount : '—'}
          </div>
          <div style={{ color: '#555', marginTop: 6 }}>
            Core instances online
            {cluster && instances.length > onlineCount
              ? ` / ${instances.length} known`
              : ''}
          </div>
        </div>
        {CARDS.map(c => <Card key={c.key} c={c} />)}
      </div>

      <div className="card" style={{ marginTop: '1.5rem' }}>
        <h3 style={{ marginTop: 0 }}>Running core instances</h3>
        <p style={{ color: '#555', margin: '0 0 1rem' }}>
          Each core replica registers a heartbeat. Online means a beat in the last 30 seconds.
        </p>
        {instances.length === 0 && !loading && <p>No instance heartbeats yet.</p>}
        {instances.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Instance</th>
                <th>Status</th>
                <th>Started</th>
                <th>Last heartbeat</th>
              </tr>
            </thead>
            <tbody>
              {instances.map(i => (
                <tr key={i.instanceId}>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{i.instanceId}</td>
                  <td>
                    <span className={`badge ${i.online ? 'badge-ok' : 'badge-fail'}`}>
                      {i.online ? 'online' : 'offline'}
                    </span>
                  </td>
                  <td>{fmtTime(i.startedAt)}</td>
                  <td>{fmtTime(i.lastHeartbeatAt)} <span style={{ color: '#64748b' }}>({ago(i.lastHeartbeatAt)})</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card" style={{ marginTop: '1.5rem' }}>
        <strong>Try the demo:</strong> seed data, then open a record under{' '}
        <Link to="/attestations">Attestations</Link> and click <em>view</em> to see its full
        chain of custody in <Link to="/proof">Proof &amp; Anchor</Link> — leaf → root → external anchor.
      </div>
    </>
  )
}
