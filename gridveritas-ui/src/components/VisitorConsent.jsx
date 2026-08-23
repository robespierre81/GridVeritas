import { useEffect, useState } from 'react'

const STORAGE_KEY = 'visitortrack-consent'
const TRACKER_SRC = 'https://e-softworks.consulting/visitortrack/tracker.js'

function doNotTrack() {
  const dnt = navigator.doNotTrack || window.doNotTrack || navigator.msDoNotTrack
  return dnt === '1' || dnt === 'yes' || navigator.globalPrivacyControl === true
}

function loadTracker() {
  if (doNotTrack()) return
  if (document.querySelector(`script[src="${TRACKER_SRC}"]`)) return
  const s = document.createElement('script')
  s.src = TRACKER_SRC
  s.async = true
  s.dataset.site = 'gridveritas'
  s.dataset.consent = 'required'
  document.head.appendChild(s)
}

export default function VisitorConsent() {
  const [choice, setChoice] = useState(() => {
    if (typeof navigator !== 'undefined' && doNotTrack()) return 'denied'
    try {
      return localStorage.getItem(STORAGE_KEY)
    } catch {
      return null
    }
  })

  useEffect(() => {
    if (choice === 'granted') loadTracker()
  }, [choice])

  function decide(value) {
    try {
      localStorage.setItem(STORAGE_KEY, value)
    } catch { /* ignore */ }
    setChoice(value)
  }

  if (choice === 'granted' || choice === 'denied') return null

  return (
    <div className="consent-bar" role="dialog" aria-label="Visitor statistics">
      <p>
        This site records page views (path, referrer, country) after you accept.
        Do Not Track and Global Privacy Control are honoured.
      </p>
      <div className="consent-bar__actions">
        <button type="button" className="btn-secondary" onClick={() => decide('denied')}>Decline</button>
        <button type="button" onClick={() => decide('granted')}>Accept</button>
      </div>
    </div>
  )
}
