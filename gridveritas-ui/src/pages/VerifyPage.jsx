import { useState } from 'react'
import { api } from '../api/client'

export default function VerifyPage() {
  const [payloadHash, setPayloadHash] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const onVerify = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    setError(null)
    try {
      const res = await api.verify(payloadHash)
      setResult(res)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h2>Verify</h2>
      <div className="card">
        <p style={{ marginBottom: '1rem', color: '#555' }}>
          Enter a payload hash to check whether a matching attestation exists in the core.
        </p>
        <form onSubmit={onVerify}>
          <div className="form-row">
            <label>Payload hash</label>
            <input value={payloadHash} onChange={e => setPayloadHash(e.target.value)} required
                   placeholder="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" />
          </div>
          <button type="submit" disabled={loading}>{loading ? 'Checking…' : 'Verify'}</button>
        </form>

        {error && <div className="msg msg-err">{error}</div>}

        {result && (
          <div className={`msg ${result.valid ? 'msg-ok' : 'msg-err'}`} style={{ marginTop: '1rem' }}>
            <strong>{result.valid ? 'Valid' : 'Not found'}</strong>
            <div>{result.message}</div>
            {result.attestationId && (
              <div style={{ marginTop: '0.5rem', fontFamily: 'monospace', fontSize: '0.85rem' }}>
                Attestation ID: {result.attestationId}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
