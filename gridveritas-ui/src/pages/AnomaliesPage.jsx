import { useEffect, useState } from 'react'
import { api } from '../api/client'

function severityBadge(sev) {
  if (sev === 'CRITICAL') return { className: 'badge badge-fail' }
  if (sev === 'WARNING') return { className: 'badge', style: { background: '#fff3cd', color: '#856404' } }
  return { className: 'badge badge-info' }
}

export default function AnomaliesPage() {
  const [list, setList] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    setError(null)
    api.listAnomalies()
      .then(setList)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  return (
    <>
      <h2>Anomalies</h2>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <p style={{ margin: 0, color: '#555' }}>
            Statistical findings from off-critical-path detection (sequence gaps, invalid-signature spikes, source silence).
          </p>
          <button onClick={load} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
        </div>

        {error && <div className="msg msg-err">{error}</div>}
        {!error && list.length === 0 && !loading && <p>No anomalies detected yet.</p>}

        {list.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Severity</th>
                <th>Type</th>
                <th>Description</th>
                <th>Source</th>
                <th>Detected</th>
              </tr>
            </thead>
            <tbody>
              {list.map(a => {
                const b = severityBadge(a.severity)
                return (
                  <tr key={a.id}>
                    <td><span className={b.className} style={b.style}>{a.severity}</span></td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{a.type}</td>
                    <td>{a.description}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                      {a.sourceId ? `${a.sourceId.slice(0, 8)}…` : '—'}
                    </td>
                    <td>{a.detectedAt ? new Date(a.detectedAt).toLocaleString() : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
