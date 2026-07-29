const API_BASE = import.meta.env.VITE_API_BASE || '/gridveritas/api/v1'

async function request(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options
  })
  if (!res.ok) {
    const text = await res.text()
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
  verify: (payloadHash) => request('/verify', { method: 'POST', body: JSON.stringify({ payloadHash }) }),
  audit: () => request('/audit')
}
