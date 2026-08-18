import { useEffect, useState } from 'react'
import { api } from '../api/client'

function fmt(iso) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

export default function FederationPage() {
  const [info, setInfo] = useState(null)
  const [bundle, setBundle] = useState(null)
  const [peers, setPeers] = useState([])
  const [peerRoots, setPeerRoots] = useState([])
  const [name, setName] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [publicKey, setPublicKey] = useState('')
  const [msg, setMsg] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([
      api.federationInfo(),
      api.federationRoots(20),
      api.listFederationPeers().catch(() => []),
      api.listPeerRoots(50).catch(() => [])
    ])
      .then(([i, b, p, r]) => {
        setInfo(i)
        setBundle(b)
        setPeers(p)
        setPeerRoots(r)
      })
      .catch(e => setMsg({ type: 'err', text: e.message }))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const onAdd = async (e) => {
    e.preventDefault()
    setMsg(null)
    try {
      await api.addFederationPeer({ name, baseUrl, publicKey })
      setName(''); setBaseUrl(''); setPublicKey('')
      setMsg({ type: 'ok', text: 'Peer registered' })
      load()
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    }
  }

  const onFetch = async (id) => {
    setMsg(null)
    try {
      const report = await api.fetchFederationPeer(id)
      setMsg({
        type: report.error ? 'err' : 'ok',
        text: report.error
          ? report.error
          : `Fetched ${report.seen} roots, stored ${report.stored}, already known ${report.alreadyKnown}`
      })
      load()
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ marginBottom: 0 }}>Federation</h2>
        <button onClick={load} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </div>
      <p style={{ color: '#555', margin: '8px 0 1rem' }}>
        Independent operators publish signed, anchored Merkle roots. A peer is not trusted —
        this node verifies the operator Ed25519 signature and, when present, the RFC 3161
        token locally.
      </p>
      {msg && <div className={`msg msg-${msg.type}`}>{msg.text}</div>}

      <div className="card">
        <h3 style={{ marginTop: 0 }}>This operator</h3>
        {info ? (
          <dl style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '0.4rem 1rem' }}>
            <dt>Operator id</dt><dd style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{info.operatorId}</dd>
            <dt>Public key</dt><dd style={{ fontFamily: 'monospace', fontSize: '0.8rem', wordBreak: 'break-all' }}>{info.publicKey}</dd>
            <dt>Algorithm</dt><dd>{info.algorithm} / {info.domainTag}</dd>
          </dl>
        ) : <p>Loading…</p>}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Published roots</h3>
        {(bundle?.roots ?? []).length === 0 ? <p>No sealed roots yet.</p> : (
          <table className="table">
            <thead>
              <tr>
                <th>Root</th>
                <th>Leaves</th>
                <th>Computed</th>
                <th>Anchor</th>
              </tr>
            </thead>
            <tbody>
              {bundle.roots.map(r => (
                <tr key={r.rootHash}>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{r.rootHash.slice(0, 16)}…</td>
                  <td>{r.leafCount}</td>
                  <td>{fmt(r.computedAt)}</td>
                  <td>
                    <span className={`badge ${r.anchor ? 'badge-ok' : 'badge-info'}`}>
                      {r.anchor ? r.anchor.authority : 'unanchored'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Register peer</h3>
        <p style={{ color: '#555', margin: '0 0 1rem' }}>Admin login required. base URL is the peer core (or public /gridveritas prefix).</p>
        <form onSubmit={onAdd}>
          <div className="form-row">
            <label>Name</label>
            <input value={name} onChange={e => setName(e.target.value)} required placeholder="utility-b" />
          </div>
          <div className="form-row">
            <label>Base URL</label>
            <input value={baseUrl} onChange={e => setBaseUrl(e.target.value)} required placeholder="https://other.example/gridveritas" />
          </div>
          <div className="form-row">
            <label>Peer public key (base64 raw Ed25519)</label>
            <input value={publicKey} onChange={e => setPublicKey(e.target.value)} required />
          </div>
          <button type="submit">Add peer</button>
        </form>
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Peers</h3>
        {peers.length === 0 ? <p>No peers registered.</p> : (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>URL</th>
                <th>Last fetch</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {peers.map(p => (
                <tr key={p.id}>
                  <td>{p.name}{p.enabled ? '' : ' (disabled)'}</td>
                  <td style={{ fontSize: '0.85rem' }}>{p.baseUrl}</td>
                  <td>
                    {fmt(p.lastFetchedAt)}
                    {p.lastError && <div className="badge badge-fail">{p.lastError}</div>}
                  </td>
                  <td><button onClick={() => onFetch(p.id)}>Fetch now</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Stored peer roots</h3>
        {peerRoots.length === 0 ? <p>None fetched yet.</p> : (
          <table className="table">
            <thead>
              <tr>
                <th>Peer</th>
                <th>Root</th>
                <th>Signature</th>
                <th>Anchor</th>
                <th>Fetched</th>
              </tr>
            </thead>
            <tbody>
              {peerRoots.map(r => (
                <tr key={r.id}>
                  <td>{r.peerName}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{r.rootHash.slice(0, 16)}…</td>
                  <td><span className={`badge ${r.signatureValid ? 'badge-ok' : 'badge-fail'}`}>{r.signatureValid ? 'valid' : 'invalid'}</span></td>
                  <td><span className={`badge ${r.anchorValid ? 'badge-ok' : 'badge-info'}`}>{r.anchorValid ? 'valid' : 'none / fail'}</span></td>
                  <td>{fmt(r.fetchedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
