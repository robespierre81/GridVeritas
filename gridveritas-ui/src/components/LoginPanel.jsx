import { useState } from 'react'
import { login, clearToken, isLoggedIn, getRole } from '../api/auth'

export default function LoginPanel() {
  const [loggedIn, setLoggedIn] = useState(isLoggedIn())
  const [role, setRole] = useState(getRole())
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const doLogin = async () => {
    setBusy(true); setError(null)
    try {
      const d = await login(username, password)
      setLoggedIn(true); setRole(d.role); setPassword('')
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const doLogout = () => { clearToken(); setLoggedIn(false); setRole(null) }

  const boxStyle = { marginTop: 'auto', paddingTop: '1rem', borderTop: '1px solid rgba(255,255,255,0.15)', fontSize: '0.82rem' }
  const inputStyle = { width: '100%', marginBottom: 4, padding: '4px 6px', fontSize: '0.82rem' }

  if (loggedIn) {
    return (
      <div style={boxStyle}>
        <div style={{ color: '#cbd5e1', marginBottom: 6 }}>Signed in as <strong style={{ color: '#fff' }}>{role}</strong></div>
        <button onClick={doLogout} style={{ width: '100%' }}>Log out</button>
      </div>
    )
  }

  return (
    <div style={boxStyle}>
      <div style={{ color: '#cbd5e1', marginBottom: 6 }}>Admin login (for writes)</div>
      <input style={inputStyle} placeholder="username" value={username} onChange={e => setUsername(e.target.value)} />
      <input style={inputStyle} type="password" placeholder="password" value={password} onChange={e => setPassword(e.target.value)}
             onKeyDown={e => { if (e.key === 'Enter') doLogin() }} />
      <button onClick={doLogin} disabled={busy || !password} style={{ width: '100%' }}>{busy ? '…' : 'Log in'}</button>
      {error && <div className="msg msg-err" style={{ fontSize: '0.75rem', marginTop: 6 }}>{error}</div>}
    </div>
  )
}
