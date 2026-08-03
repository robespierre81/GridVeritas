import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'

function Badge({ ok, labels }) {
  // labels = { yes, no, unknown }
  if (ok === true) return <span className="badge badge-ok">{labels.yes}</span>
  if (ok === false) return <span className="badge badge-fail">{labels.no}</span>
  return <span className="badge badge-info">{labels.unknown ?? '—'}</span>
}

function Node({ title, accent, children }) {
  return (
    <div style={{
      border: `2px solid ${accent}`, borderRadius: 10, padding: '0.75rem 1rem',
      background: 'var(--card, #fff)'
    }}>
      <div style={{ fontWeight: 600, color: accent, marginBottom: 4 }}>{title}</div>
      <div style={{ fontFamily: 'monospace', fontSize: '0.8rem', wordBreak: 'break-all' }}>{children}</div>
    </div>
  )
}

const arrow = <div style={{ textAlign: 'center', color: '#9ca3af', fontSize: '1.2rem', margin: '2px 0' }}>↓</div>

export default function ProofPage() {
  const [params, setParams] = useSearchParams()
  const [id, setId] = useState(params.get('id') || '')
  const [att, setAtt] = useState(null)
  const [proof, setProof] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = async (theId) => {
    const target = (theId ?? id).trim()
    if (!target) return
    setLoading(true); setError(null); setProof(null); setAtt(null)
    try {
      const [a, p] = await Promise.all([
        api.getAttestation(target).catch(() => null),
        api.getProof(target)
      ])
      setAtt(a); setProof(p)
      setParams({ id: target })
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (params.get('id')) load(params.get('id'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const sealed = proof && proof.status === 'SEALED'
  const tampered = proof && proof.provenanceIntact === false
  const fullyVerified = sealed && !tampered && proof.anchored
    && proof.anchorSignatureValid && att?.signatureValid === true

  let banner = null
  if (proof) {
    if (tampered) banner = { text: '⚠ TAMPERING DETECTED — the stored record no longer matches its anchored leaf', cls: 'badge-fail', bg: '#fde8e8', fg: '#9b1c1c' }
    else if (proof.status === 'PENDING_SEAL') banner = { text: 'Not yet sealed — this attestation has not been included in a Merkle root yet', cls: 'badge-info', bg: '#eef2ff', fg: '#3730a3' }
    else if (fullyVerified) banner = { text: '✓ VERIFIED — authentic, unaltered, and externally anchored', cls: 'badge-ok', bg: '#e6f4ea', fg: '#1e7e34' }
    else banner = { text: 'Sealed — verification partial (see badges below)', cls: 'badge-info', bg: '#fff8e1', fg: '#856404' }
  }

  return (
    <>
      <h2>Proof &amp; Anchor</h2>

      <div className="card">
        <p style={{ marginTop: 0, color: '#555' }}>
          The full chain of custody for one attestation: the anchored leaf, the audit path to the
          Merkle root, and the external RFC 3161 anchor. <strong>Provenance intact</strong> means the
          stored record still matches the leaf that was externally anchored.
        </p>
        <div className="form-row" style={{ display: 'flex', gap: '0.5rem' }}>
          <input style={{ flex: 1 }} placeholder="attestation id (UUID)" value={id}
                 onChange={e => setId(e.target.value)}
                 onKeyDown={e => { if (e.key === 'Enter') load() }} />
          <button onClick={() => load()} disabled={loading || !id.trim()}>{loading ? 'Loading…' : 'Load proof'}</button>
        </div>
        {error && <div className="msg msg-err">{error}</div>}
      </div>

      {banner && (
        <div className="card" style={{ background: banner.bg, color: banner.fg, fontWeight: 600 }}>
          {banner.text}
        </div>
      )}

      {proof && (
        <div className="card">
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '1rem' }}>
            <Badge ok={att?.signatureValid} labels={{ yes: 'signature valid', no: 'signature invalid' }} />
            <Badge ok={proof.provenanceIntact} labels={{ yes: 'provenance intact', no: 'provenance broken' }} />
            <Badge ok={proof.anchored ? true : undefined} labels={{ yes: 'anchored', unknown: 'anchor pending' }} />
            <Badge ok={proof.anchorSignatureValid} labels={{ yes: 'anchor signature ok', no: 'anchor signature FAILED' }} />
            <Badge ok={proof.anchorTrusted} labels={{ yes: 'trust-pinned', unknown: 'trust not pinned' }} />
          </div>

          {sealed && (
            <div style={{ maxWidth: 620 }}>
              <Node title="Leaf (anchored)" accent="#059669">
                {proof.leafHash}
                {tampered && (
                  <div style={{ color: '#9b1c1c', marginTop: 6 }}>
                    current record recomputes to: {proof.currentLeaf} ✗
                  </div>
                )}
              </Node>
              {arrow}
              <div style={{ border: '1px dashed #cbd5e1', borderRadius: 10, padding: '0.5rem 1rem' }}>
                <div style={{ fontWeight: 600, color: '#64748b', marginBottom: 4 }}>
                  Audit path ({proof.auditPath?.length || 0} step{proof.auditPath?.length === 1 ? '' : 's'})
                </div>
                {proof.auditPath?.length
                  ? proof.auditPath.map((s, i) => (
                      <div key={i} style={{ fontFamily: 'monospace', fontSize: '0.75rem', wordBreak: 'break-all' }}>
                        <span style={{ color: '#7c3aed' }}>{s.position}</span> · {s.hash}
                      </div>
                    ))
                  : <div style={{ color: '#94a3b8' }}>single-leaf tree (root = leaf)</div>}
              </div>
              {arrow}
              <Node title="Merkle root" accent="#2563eb">
                {proof.rootHash}
                <div style={{ color: '#64748b', marginTop: 4 }}>
                  {proof.leafCount} leaves · {proof.computedAt ? new Date(proof.computedAt).toLocaleString() : ''}
                </div>
              </Node>
              {arrow}
              {proof.anchored
                ? <Node title="External anchor (RFC 3161)" accent="#7c3aed">
                    {proof.anchorAuthority}
                    <div style={{ color: '#64748b', marginTop: 4 }}>
                      time: {proof.anchorTime ? new Date(proof.anchorTime).toLocaleString() : '—'}
                      {proof.anchorSerial ? ` · serial: ${proof.anchorSerial}` : ''}
                    </div>
                    {proof.anchorToken && (
                      <details style={{ marginTop: 6 }}>
                        <summary style={{ cursor: 'pointer' }}>RFC 3161 token (base64)</summary>
                        <div style={{ maxHeight: 120, overflow: 'auto', fontSize: '0.7rem' }}>{proof.anchorToken}</div>
                      </details>
                    )}
                  </Node>
                : <div style={{ color: '#856404' }}>Anchor pending — the anchoring job has not run for this root yet.</div>}
            </div>
          )}
        </div>
      )}
    </>
  )
}
