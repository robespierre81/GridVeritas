import { useEffect, useState } from 'react'
import { api } from '../api/client'

export default function SourcesPage() {
  const [sources, setSources] = useState([])
  const [name, setName] = useState('')
  const [publicKey, setPublicKey] = useState('')
  const [msg, setMsg] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = () => api.listSources().then(setSources).catch(e => setMsg({ type: 'err', text: e.message }))

  useEffect(() => { load() }, [])

  const onCreate = async (e) => {
    e.preventDefault()
    setLoading(true)
    setMsg(null)
    try {
      await api.createSource({ name, publicKey })
      setName('')
      setPublicKey('')
      setMsg({ type: 'ok', text: 'Source created' })
      load()
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h2>Sources</h2>
      <div className="card">
        <h3 style={{ marginBottom: '1rem' }}>Create source</h3>
        <form onSubmit={onCreate}>
          <div className="form-row">
            <label>Name</label>
            <input value={name} onChange={e => setName(e.target.value)} required placeholder="demo-gateway-01" />
          </div>
          <div className="form-row">
            <label>Public key (optional for MVP)</label>
            <input value={publicKey} onChange={e => setPublicKey(e.target.value)} placeholder="demo-public-key" />
          </div>
          <button type="submit" disabled={loading}>{loading ? 'Creating…' : 'Create source'}</button>
        </form>
        {msg && <div className={`msg msg-${msg.type}`}>{msg.text}</div>}
      </div>

      <div className="card">
        <h3 style={{ marginBottom: '1rem' }}>Registered sources</h3>
        {sources.length === 0 ? (
          <p>No sources yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>ID</th>
                <th>Status</th>
                <th>Last seen</th>
              </tr>
            </thead>
            <tbody>
              {sources.map(s => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{s.id}</td>
                  <td><span className="badge badge-info">{s.status}</span></td>
                  <td>{s.lastSeenAt ? new Date(s.lastSeenAt).toLocaleString() : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
