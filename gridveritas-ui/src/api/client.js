import { getReadKey, getToken } from './auth'

const API_BASE = import.meta.env.VITE_API_BASE || '/gridveritas/api/v1'

async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    'X-Read-Key': getReadKey(),
    ...options.headers
  }
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!res.ok) {
    const text = await res.text()
    if (res.status === 401) throw new Error('Unauthorized — a read key or login is required.')
    if (res.status === 403) throw new Error('Forbidden — this action needs an admin login.')
    if (res.status === 429) throw new Error('Rate limit exceeded — please retry shortly.')
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export const api = {
  listSources: () => request('/sources'),
  createSource: (body) => request('/sources', { method: 'POST', body: JSON.stringify(body) }),
  listAttestations: (sourceId) => request(`/attestations?sourceId=${sourceId}`),
  getAttestation: (id) => request(`/attestations/${id}`),
  createAttestation: (body) => request('/attestations', { method: 'POST', body: JSON.stringify(body) }),
  getProof: (id) => request(`/attestations/${id}/proof`),
  verify: (payloadHash) => request('/verify', { method: 'POST', body: JSON.stringify({ payloadHash }) }),
  listAnomalies: (sourceId) => request(sourceId ? `/anomalies?sourceId=${sourceId}` : '/anomalies'),
  askAudit: (question) => request('/audit/ask', { method: 'POST', body: JSON.stringify({ question }) }),
  listAuditLog: () => request('/audit'),
  listVerifications: () => request('/audit/verifications'),
  getStats: () => request('/stats'),
  listInstances: () => request('/cluster/instances'),
  federationInfo: () => request('/federation/info'),
  federationRoots: (limit) => request(`/federation/roots${limit ? `?limit=${limit}` : ''}`),
  listFederationPeers: () => request('/federation/peers'),
  addFederationPeer: (body) => request('/federation/peers', { method: 'POST', body: JSON.stringify(body) }),
  fetchFederationPeer: (id) => request(`/federation/peers/${id}/fetch`, { method: 'POST' }),
  listPeerRoots: (limit) => request(`/federation/peer-roots?limit=${limit || 50}`),
  settlementMapping: () => request('/settlements/mapping'),
  listAggregators: () => request('/aggregators'),
  createAggregator: (body) => request('/aggregators', { method: 'POST', body: JSON.stringify(body) }),
  listDerResources: () => request('/resources'),
  createDerResource: (body) => request('/resources', { method: 'POST', body: JSON.stringify(body) }),
  listResourceAttestations: (id) => request(`/resources/${id}/attestations`),
  listSettlements: () => request('/settlements'),
  createSettlement: (body) => request('/settlements', { method: 'POST', body: JSON.stringify(body) }),
  getSettlement: (id) => request(`/settlements/${id}`)
}
