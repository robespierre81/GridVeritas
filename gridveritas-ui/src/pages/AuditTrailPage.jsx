import { useEffect, useState } from 'react'
import { api } from '../api/client'

function resultBadge(result) {
  if (result === 'VALID' || result === 'SEALED') return 'badge badge-ok'
  if (result && (result.startsWith('INVALID') || result === 'NOT_FOUND')) return 'badge badge-fail'
  return 'badge badge-info'
}

export default function AuditTrailPage() {
  const [auditLog, setAuditLog] = useState([])
  const [verifications, setVerifications] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true); setError(null)
    Promise.all([api.listAuditLog(), api.listVerifications()])
      .then(([a, v]) => { setAuditLog(a); setVerifications(v) })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const time = (t) => t ? new Date(t).toLocaleString() : '—'

  return (
    <>
      <h2>Audit Trail</h2>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ margin: 0, color: '#555' }}>
            Append-only record of verifications and security/configuration events.
          </p>
          <button onClick={load} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
        </div>
        {error && <div className="msg msg-err">{error}</div>}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Security & configuration events</h3>
        {auditLog.length === 0 && !loading && <p>No events yet.</p>}
        {auditLog.length > 0 && (
          <table className="table">
            <thead>
              <tr><th>Action</th><th>Target</th><th>Principal</th><th>Detail</th><th>Time</th></tr>
            </thead>
            <tbody>
              {auditLog.map(a => (
                <tr key={a.id}>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{a.action}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                    {a.target ? (a.target.length > 20 ? a.target.slice(0, 20) + '…' : a.target) : '—'}
                  </td>
                  <td>{a.principal}</td>
                  <td style={{ fontSize: '0.8rem', color: '#555' }}>{a.detail}</td>
                  <td>{time(a.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Verification events</h3>
        {verifications.length === 0 && !loading && <p>No verifications yet.</p>}
        {verifications.length > 0 && (
          <table className="table">
            <thead>
              <tr><th>Type</th><th>Result</th><th>Subject</th><th>Principal</th><th>Time</th></tr>
            </thead>
            <tbody>
              {verifications.map(v => (
                <tr key={v.id}>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{v.eventType}</td>
                  <td><span className={resultBadge(v.result)}>{v.result}</span></td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                    {v.subject ? (v.subject.length > 20 ? v.subject.slice(0, 20) + '…' : v.subject) : '—'}
                  </td>
                  <td>{v.principal}</td>
                  <td>{time(v.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
