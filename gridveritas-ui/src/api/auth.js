// Auth state for the UI.
// - Reads are authorized by a static read key (X-Read-Key), from VITE_READ_KEY.
// - Writes need a JWT obtained via /auth/token (admin/ingest login).

const API_BASE = import.meta.env.VITE_API_BASE || '/gridveritas/api/v1'
const READ_KEY = import.meta.env.VITE_READ_KEY || 'dev-read-key-change-me'

let token = sessionStorage.getItem('gv_token') || null
let role = sessionStorage.getItem('gv_role') || null

export function getReadKey() { return READ_KEY }
export function getToken() { return token }
export function getRole() { return role }
export function isLoggedIn() { return !!token }

export function clearToken() {
  token = null
  role = null
  sessionStorage.removeItem('gv_token')
  sessionStorage.removeItem('gv_role')
}

export async function login(username, password) {
  const res = await fetch(`${API_BASE}/auth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  const data = await res.json()
  token = data.token
  role = data.role
  sessionStorage.setItem('gv_token', token)
  sessionStorage.setItem('gv_role', role)
  return data
}
