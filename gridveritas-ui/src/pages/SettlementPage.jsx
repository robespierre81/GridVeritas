import { useEffect, useState } from 'react'
import { api } from '../api/client'

function fmt(iso) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

export default function SettlementPage() {
  const [mapping, setMapping] = useState(null)
  const [aggregators, setAggregators] = useState([])
  const [resources, setResources] = useState([])
  const [settlements, setSettlements] = useState([])
  const [sources, setSources] = useState([])
  const [open, setOpen] = useState(null)
  const [msg, setMsg] = useState(null)
  const [aggName, setAggName] = useState('Demo Aggregator')
  const [resName, setResName] = useState('Battery-01')
  const [externalId, setExternalId] = useState('PJM-REF-BESS-01')
  const [aggregatorId, setAggregatorId] = useState('')
  const [sourceId, setSourceId] = useState('')
  const [resourceId, setResourceId] = useState('')
  const [periodStart, setPeriodStart] = useState('2026-08-01T00:00')
  const [periodEnd, setPeriodEnd] = useState('2026-08-01T01:00')

  const load = () => {
    Promise.all([
      api.settlementMapping(),
      api.listAggregators(),
      api.listDerResources(),
      api.listSettlements(),
      api.listSources()
    ]).then(([m, a, r, s, src]) => {
      setMapping(m)
      setAggregators(a)
      setResources(r)
      setSettlements(s)
      setSources(src)
      if (!aggregatorId && a[0]) setAggregatorId(a[0].id)
      if (!resourceId && r[0]) setResourceId(r[0].id)
      if (!sourceId && src[0]) setSourceId(src[0].id)
    }).catch(e => setMsg({ type: 'err', text: e.message }))
  }

  useEffect(() => { load() }, [])

  const addAggregator = async (e) => {
    e.preventDefault()
    try {
      await api.createAggregator({ name: aggName, partyRole: 'AGGREGATOR' })
      setMsg({ type: 'ok', text: 'Aggregator created' })
      load()
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    }
  }

  const addResource = async (e) => {
    e.preventDefault()
    try {
      await api.createDerResource({
        aggregatorId, name: resName, resourceType: 'BATTERY',
        externalId, sourceIds: [sourceId]
      })
      setMsg({ type: 'ok', text: 'Resource created' })
      load()
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    }
  }

  const addSettlement = async (e) => {
    e.preventDefault()
    try {
      const created = await api.createSettlement({
        resourceId,
        periodStart: new Date(periodStart).toISOString(),
        periodEnd: new Date(periodEnd).toISOString(),
        market: 'PJM'
      })
      setOpen(created)
      setMsg({ type: 'ok', text: `Settlement ${created.id} with ${created.attestationCount} interval(s)` })
      load()
    } catch (err) {
      setMsg({ type: 'err', text: err.message })
    }
  }

  return (
    <>
      <h2>DER MV&amp;S reference</h2>
      <p style={{ color: '#555', margin: '0 0 1rem' }}>
        Thin settlement view over existing attestations. This is a FERC Order No. 2222
        mapping demo — not market clearing and not an RTO/ISO certification.
      </p>
      {msg && <div className={`msg msg-${msg.type}`}>{msg.text}</div>}

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Mapping ({mapping?.targetFormat})</h3>
        <p>{mapping?.disclaimer}</p>
        {mapping?.evidentiaryMap && (
          <table className="table">
            <thead><tr><th>Need</th><th>GridVeritas primitive</th></tr></thead>
            <tbody>
              {mapping.evidentiaryMap.map((row, i) => (
                <tr key={i}>
                  <td>{row.fercOrMarketNeed}</td>
                  <td>{row.gridveritasPrimitive}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Create (admin)</h3>
        <form onSubmit={addAggregator} className="form-row">
          <label>Aggregator</label>
          <input value={aggName} onChange={e => setAggName(e.target.value)} />
          <button type="submit">Add aggregator</button>
        </form>
        <form onSubmit={addResource} className="form-row">
          <label>Resource on aggregator</label>
          <select value={aggregatorId} onChange={e => setAggregatorId(e.target.value)}>
            {aggregators.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <input value={resName} onChange={e => setResName(e.target.value)} />
          <input value={externalId} onChange={e => setExternalId(e.target.value)} />
          <select value={sourceId} onChange={e => setSourceId(e.target.value)}>
            {sources.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <button type="submit" disabled={!aggregatorId || !sourceId}>Add resource</button>
        </form>
        <form onSubmit={addSettlement}>
          <label>Settlement period</label>
          <select value={resourceId} onChange={e => setResourceId(e.target.value)}>
            {resources.map(r => <option key={r.id} value={r.id}>{r.name} ({r.externalId || r.id})</option>)}
          </select>
          <input type="datetime-local" value={periodStart} onChange={e => setPeriodStart(e.target.value)} />
          <input type="datetime-local" value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} />
          <button type="submit" disabled={!resourceId}>Build settlement view</button>
        </form>
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Settlements</h3>
        {settlements.length === 0 ? <p>None yet.</p> : (
          <table className="table">
            <thead>
              <tr><th>Resource</th><th>Period</th><th>Intervals</th><th>Anchored</th><th></th></tr>
            </thead>
            <tbody>
              {settlements.map(s => (
                <tr key={s.id}>
                  <td>{s.resourceName}</td>
                  <td>{fmt(s.periodStart)} → {fmt(s.periodEnd)}</td>
                  <td>{s.attestationCount}</td>
                  <td>{s.anchoredCount}</td>
                  <td><button onClick={() => api.getSettlement(s.id).then(setOpen)}>Open</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {open && (
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Settlement {open.id}</h3>
          <p>{open.disclaimer}</p>
          <p>Format {open.formatName} / market {open.market}. Provenance intact: {open.provenanceIntactCount}/{open.attestationCount}.</p>
          <table className="table">
            <thead>
              <tr>
                <th>Beginning UTC</th><th>Ending UTC</th><th>Attestation</th><th>Sig</th><th>Anchor</th><th>Intact</th>
              </tr>
            </thead>
            <tbody>
              {open.intervals.map(i => (
                <tr key={i.attestationId}>
                  <td>{fmt(i.datetimeBeginningUtc)}</td>
                  <td>{fmt(i.datetimeEndingUtc)}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{i.attestationId.slice(0, 8)}…</td>
                  <td><span className={`badge ${i.signatureValid ? 'badge-ok' : 'badge-fail'}`}>{i.signatureValid ? 'ok' : 'no'}</span></td>
                  <td><span className={`badge ${i.anchored ? 'badge-ok' : 'badge-info'}`}>{i.anchored ? 'yes' : 'pending'}</span></td>
                  <td><span className={`badge ${i.provenanceIntact ? 'badge-ok' : 'badge-info'}`}>{i.provenanceIntact ? 'yes' : 'n/a'}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
