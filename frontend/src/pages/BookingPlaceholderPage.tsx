import { Link, useParams, useSearchParams } from 'react-router-dom'

export function BookingPlaceholderPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const checkIn = searchParams.get('checkIn')
  const checkOut = searchParams.get('checkOut')
  const detailPath = `/hotels/${id}${searchParams.size > 0 ? `?${searchParams.toString()}` : ''}`

  return (
    <section className="message-panel booking-placeholder">
      <p className="eyebrow">Next step</p>
      <h1>Booking form coming next</h1>
      <p>Hotel ID: <strong>{id}</strong></p>
      <p>Check-in: <strong>{checkIn ?? 'Not selected'}</strong></p>
      <p>Check-out: <strong>{checkOut ?? 'Not selected'}</strong></p>
      <Link className="text-link" to={detailPath}>Back to hotel details</Link>
    </section>
  )
}
