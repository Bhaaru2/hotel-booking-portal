import { Link } from 'react-router-dom'

interface HotelNotFoundProps {
  backTo: string
  backLabel?: string
}

export function HotelNotFound({ backTo, backLabel = 'Back to results' }: HotelNotFoundProps) {
  return (
    <section className="message-panel">
      <p className="eyebrow">Hotel unavailable</p>
      <h1>Hotel not found</h1>
      <p>We couldn’t find a hotel with that ID.</p>
      <Link className="text-link" to={backTo}>{backLabel}</Link>
    </section>
  )
}
