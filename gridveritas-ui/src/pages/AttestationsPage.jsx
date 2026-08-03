import { Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { api } from '../api/client'

export default function AttestationsPage() {
  const [sources, setSources] = useState([])
  const [sourceId, setSourceId] = useState('')
  const [list, setList] = useState([])
  const [payloadHash, setPayloadHash] = useState('')
  const [signature, setSignature] = useState('demo-signature')
  const [sequenceNr, setSequenceNr] = useState(1)
  const [msg, setMsg] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api.listSources().then(s => {
      setSources(s)
      if (s.length && !sourceId) setSourceId(s[0].id)
    }).catch(e => setMsg({ type: 'err', text: e.message }))
  }, [])

  useEffect(() => {
    if (!sourceId) return
    api.listAttestations(sourceId).then(setList).catch(e => setMsg({ type: 'err', text: e.message }))
  }, [sourceId])

  const onSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setMsg(null)
    try {
      await api.createAttestation({
        sourceId,
        payloadHash,
        timestampEpochMillis: Date.now(),
        sequenceNr: Number(sequenceNr),
        signature
      })
      setMsg({ type: 'ok', text: 'Attestation submitted' })
      setPayloadHash('')
      const refreshed = await api.listAttestations(sourceId)
      setList(refreshed)
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h2>Attestations</h2>
      <div className="card">
        <h3 style={{ marginBottom: '1rem' }}>Submit attestation</h3>
        <form onSubmit={onSubmit}>
          <div className="form-row">
            <label>Source</label>
            <select value={sourceId} onChange={e => setSourceId(e.target.value)} required>
              {sources.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div className="form-row">
            <label>Payload hash</label>
            <input value={payloadHash} onChange={e => setPayloadHash(e.target.value)} required
                   placeholder="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" />
          </div>
          <div className="form-row">
            <label>Signature</label>
            <input value={signature} onChange={e => setSignature(e.target.value)} required />
          </div>
          <div className="form-row">
            <label>Sequence number</label>
            <input type="number" value={sequenceNr} onChange={e => setSequenceNr(e.target.value)} />
          </div>
          <button type="submit" disabled={loading || !sourceId}>{loading ? 'Submitting…' : 'Submit'}</button>
        </form>
        {msg && <div className={`msg msg-${msg.type}`}>{msg.text}</div>}
      </div>

      <div className="card">
        <h3 style={{ marginBottom: '1rem' }}>Attestations for selected source</h3>
        {list.length === 0 ? <p>No attestations yet.</p> : (
          <table className="table">
            <thead>
              <tr>
                <th>Payload hash</th>
                <th>Timestamp</th>
                <th>Seq</th>
                <th>Status</th>
                <th>Signature</th>
                <th>Proof</th>
              </tr>
            </thead>
            <tbody>
              {list.map(a => (
                <tr key={a.id}>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{a.payloadHash?.slice(0, 16)}…</td>
                  <td>{a.timestamp ? new Date(a.timestamp).toLocaleString() : '—'}</td>
                  <td>{a.sequenceNr ?? '—'}</td>
                  <td><span className="badge badge-info">{a.status || 'STORED'}</span></td>
                  <td>
                    {a.signatureValid === true && <span className="badge badge-ok">valid</span>}
                    {a.signatureValid === false && <span className="badge badge-fail">invalid</span>}
                    {a.signatureValid == null && <span className="badge badge-info">—</span>}
                  </td>
                  <td>
                    <Link to={`/proof?id=${a.id}`} className="badge badge-info" style={{ textDecoration: 'none' }}>view</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
