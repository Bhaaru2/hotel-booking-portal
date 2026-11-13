import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="message-panel">
      <p className="eyebrow">404</p>
      <h1>We couldn’t find that page</h1>
      <p>It may have moved, or the address may be incorrect.</p>
      <Link className="text-link" to="/">Return to hotel search</Link>
    </section>
  )
}
