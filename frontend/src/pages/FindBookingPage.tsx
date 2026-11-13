import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import { cancelBooking, findBooking } from '../api/client'
import type { BookingResponse } from '../api/types'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { formatInr } from '../utils/currency'
import { localToday } from '../utils/dates'

function getInitialReference(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('reference' in state)) return ''
  const reference = state.reference
  return typeof reference === 'string' ? reference : ''
}

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

export function FindBookingPage() {
  const location = useLocation()
  const [reference, setReference] = useState(() => getInitialReference(location.state))
  const [email, setEmail] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ reference?: string; email?: string }>({})
  const [booking, setBooking] = useState<BookingResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [lastSearch, setLastSearch] = useState<{ reference: string; email: string } | null>(null)

  async function lookup(search: { reference: string; email: string }) {
    setLoading(true)
    setError('')
    setNotice('')
    setBooking(null)
    setConfirmCancel(false)
    setLastSearch(search)
    try {
      setBooking(await findBooking(search.reference, search.email))
    } catch (reason: unknown) {
      setError(reason instanceof ApiError && reason.status === 404
        ? 'No booking found for that reference and email.'
        : reason instanceof Error ? reason.message : 'Something went wrong, please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedReference = reference.trim().toUpperCase()
    const normalizedEmail = email.trim().toLowerCase()
    const errors: { reference?: string; email?: string } = {}
    if (!normalizedReference) errors.reference = 'Enter your booking reference.'
    if (!normalizedEmail) errors.email = 'Enter the email used for the booking.'
    else if (!isValidEmail(normalizedEmail)) errors.email = 'Enter a valid email address.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    await lookup({ reference: normalizedReference, email: normalizedEmail })
  }

  async function handleCancel() {
    if (!booking || !lastSearch) return
    setCancelling(true)
    setError('')
    setNotice('')
    try {
      const updated = await cancelBooking(lastSearch.reference, lastSearch.email)
      setBooking(updated)
      setConfirmCancel(false)
      setNotice(updated.alreadyCancelled
        ? 'This booking was already cancelled.'
        : 'Booking cancelled and your room has been released.')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Something went wrong, please try again.')
    } finally {
      setCancelling(false)
    }
  }

  function resetSearch() {
    setReference('')
    setEmail('')
    setFieldErrors({})
    setBooking(null)
    setLastSearch(null)
    setError('')
    setNotice('')
    setConfirmCancel(false)
  }

  const isPastBooking = booking !== null && booking.checkIn < localToday()

  return (
    <section className="find-booking-page">
      <div className="page-intro">
        <p className="eyebrow">Your reservation</p>
        <h1>Find my booking</h1>
        <p>Enter the reference and email used when you made the booking.</p>
      </div>

      <form className="lookup-form" onSubmit={handleLookup} noValidate>
        <div className="field-group">
          <label htmlFor="booking-reference">Booking reference</label>
          <input id="booking-reference" autoComplete="off" value={reference}
            aria-invalid={Boolean(fieldErrors.reference)} aria-describedby={fieldErrors.reference ? 'reference-error' : undefined}
            onChange={(event) => setReference(event.target.value)} />
          {fieldErrors.reference && <span className="field-error" id="reference-error">{fieldErrors.reference}</span>}
        </div>
        <div className="field-group">
          <label htmlFor="booking-email">Email</label>
          <input id="booking-email" type="email" autoComplete="email" value={email}
            aria-invalid={Boolean(fieldErrors.email)} aria-describedby={fieldErrors.email ? 'email-error' : undefined}
            onChange={(event) => setEmail(event.target.value)} />
          {fieldErrors.email && <span className="field-error" id="email-error">{fieldErrors.email}</span>}
        </div>
        <button className="button button-primary" type="submit" disabled={loading}>
          {loading ? 'Searching…' : 'Find booking'}
        </button>
      </form>

      {loading && <LoadingSpinner />}
      {error && (
        <div className="booking-error-banner" role="alert">
          <p>{error}</p>
          {lastSearch && !error.startsWith('No booking found') && (
            <button className="button button-secondary" type="button" onClick={() => void lookup(lastSearch)} disabled={loading}>Retry</button>
          )}
        </div>
      )}

      {booking && (
        <article className="lookup-result-card">
          <div className="lookup-result-heading">
            <div><p className="eyebrow">Booking reference</p><h2>{booking.reference}</h2></div>
            <span className={`booking-status-badge ${booking.status === 'CONFIRMED' ? 'status-confirmed' : 'status-cancelled'}`}>{booking.status}</span>
          </div>
          <dl className="confirmation-details lookup-details">
            <div><dt>Hotel</dt><dd>{booking.hotelName}</dd></div>
            <div><dt>Guest</dt><dd>{booking.guestName}</dd></div>
            <div><dt>Email</dt><dd>{booking.guestEmail}</dd></div>
            <div><dt>Check-in</dt><dd>{booking.checkIn}</dd></div>
            <div><dt>Check-out</dt><dd>{booking.checkOut}</dd></div>
            <div><dt>Nights</dt><dd>{booking.nights}</dd></div>
            <div><dt>Total</dt><dd>{formatInr(booking.totalAmount)}</dd></div>
          </dl>

          {notice && <p className="lookup-success" role="status">{notice}</p>}
          {isPastBooking && booking.status === 'CONFIRMED' && (
            <p className="lookup-note">This booking cannot be cancelled because check-in is in the past.</p>
          )}
          {booking.status === 'CONFIRMED' && !isPastBooking && !confirmCancel && (
            <button className="button button-secondary" type="button" onClick={() => setConfirmCancel(true)}>Cancel booking</button>
          )}
          {confirmCancel && booking.status === 'CONFIRMED' && (
            <div className="cancel-confirmation" role="group" aria-label="Confirm booking cancellation">
              <p>Are you sure? This releases your room.</p>
              <button className="button button-primary" type="button" onClick={() => void handleCancel()} disabled={cancelling}>
                {cancelling ? 'Cancelling…' : 'Yes, cancel booking'}
              </button>
              <button className="button button-secondary" type="button" onClick={() => setConfirmCancel(false)} disabled={cancelling}>Keep booking</button>
            </div>
          )}
          <button className="button button-outline search-again" type="button" onClick={resetSearch}>Search again</button>
        </article>
      )}
      {!booking && !loading && !error && <p className="lookup-help"><Link to="/">Back to hotel search</Link></p>}
    </section>
  )
}
