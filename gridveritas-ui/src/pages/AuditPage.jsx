import { useEffect, useState } from 'react'
import { api } from '../api/client'

export default function AuditPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.audit().then(setData).catch(e => setError(e.message))
  }, [])

  return (
    <>
      <h2>Audit</h2>
      <div className="card">
        {error && <div className="msg msg-err">{error}</div>}
        {!error && !data && <p>Loading…</p>}
        {data && (
          <>
            <p style={{ marginBottom: '0.75rem' }}>Current audit endpoint response (MVP placeholder):</p>
            <pre style={{ background: '#f0f3f7', padding: '1rem', borderRadius: 6, overflow: 'auto' }}>
              {JSON.stringify(data, null, 2)}
            </pre>
          </>
        )}
      </div>
    </>
  )
}
