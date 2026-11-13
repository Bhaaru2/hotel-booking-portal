import { Link } from 'react-router-dom'

export function ComingSoonPage() {
  return (
    <section className="message-panel">
      <p className="eyebrow">Coming soon</p>
      <h1>Booking lookup is on its way</h1>
      <p>You will be able to find and manage your booking here.</p>
      <Link className="text-link" to="/">Back to hotel search</Link>
    </section>
  )
}
