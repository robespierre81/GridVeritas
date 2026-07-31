import { useState } from 'react'
import { api } from '../api/client'

const EXAMPLES = [
  'Which sources have anomalies, and what types?',
  'How many attestations and Merkle roots exist, and how many are anchored?',
  'Are there any invalid-signature problems right now?'
]

export default function AuditPage() {
  const [question, setQuestion] = useState('')
  const [resp, setResp] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const ask = async (q) => {
    const text = (q ?? question).trim()
    if (!text) return
    setLoading(true)
    setError(null)
    setResp(null)
    try {
      const r = await api.askAudit(text)
      setResp(r)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h2>Audit Assistant</h2>

      <div className="card">
        <p style={{ marginBottom: '0.75rem', color: '#555' }}>
          Ask a natural-language question. The assistant answers using only facts retrieved from the
          system (shown under “facts used”) and never invents data.
        </p>

        <div className="form-row">
          <label>Question</label>
          <textarea
            rows={3}
            value={question}
            onChange={e => setQuestion(e.target.value)}
            placeholder="e.g. Which sources have anomalies, and what types?"
          />
        </div>

        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
          {EXAMPLES.map(ex => (
            <button key={ex} className="btn" style={{ background: '#eef2f7', color: '#1F4E79', fontSize: '0.82rem' }}
                    onClick={() => { setQuestion(ex); ask(ex) }} disabled={loading}>
              {ex}
            </button>
          ))}
        </div>

        <button onClick={() => ask()} disabled={loading || !question.trim()}>
          {loading ? 'Thinking…' : 'Ask'}
        </button>

        {error && <div className="msg msg-err">{error}</div>}
      </div>

      {resp && (
        <div className="card">
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.75rem' }}>
            {resp.answered
              ? <span className="badge badge-ok">answered</span>
              : <span className="badge badge-info">unavailable</span>}
            {resp.model && <span style={{ color: '#777', fontSize: '0.85rem' }}>model: {resp.model}</span>}
          </div>

          {resp.answered
            ? <p style={{ whiteSpace: 'pre-wrap' }}>{resp.answer}</p>
            : <div className="msg msg-err">{resp.note}</div>}

          {resp.contextUsed && (
            <details style={{ marginTop: '1rem' }}>
              <summary style={{ cursor: 'pointer', fontWeight: 600 }}>Facts used (retrieved context)</summary>
              <pre style={{ background: '#f0f3f7', padding: '1rem', borderRadius: 6, overflow: 'auto', fontSize: '0.8rem', marginTop: '0.5rem' }}>
                {resp.contextUsed}
              </pre>
            </details>
          )}
        </div>
      )}
    </>
  )
}
