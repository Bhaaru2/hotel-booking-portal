import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { BookingResponse } from '../api/types'
import { formatInr } from '../utils/currency'

function isBookingResponse(value: unknown): value is BookingResponse {
  if (typeof value !== 'object' || value === null) return false
  const booking = value as Partial<BookingResponse>
  return typeof booking.reference === 'string'
    && typeof booking.hotelId === 'number'
    && typeof booking.hotelName === 'string'
    && typeof booking.guestName === 'string'
    && typeof booking.guestEmail === 'string'
    && typeof booking.checkIn === 'string'
    && typeof booking.checkOut === 'string'
    && typeof booking.nights === 'number'
    && typeof booking.totalAmount === 'number'
    && (booking.status === 'CONFIRMED' || booking.status === 'CANCELLED')
    && typeof booking.createdAt === 'string'
}

function copyWithFallback(value: string): boolean {
  const textArea = document.createElement('textarea')
  textArea.value = value
  textArea.setAttribute('readonly', '')
  textArea.style.position = 'fixed'
  textArea.style.opacity = '0'
  document.body.appendChild(textArea)
  textArea.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    textArea.remove()
  }
}

export function BookingConfirmationPage() {
  const location = useLocation()
  const booking = isBookingResponse(location.state) ? location.state : null
  const [copyFeedback, setCopyFeedback] = useState('')

  async function copyReference() {
    if (!booking) return
    let copied = false
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(booking.reference)
        copied = true
      }
    } catch {
      copied = false
    }
    if (!copied) copied = copyWithFallback(booking.reference)
    setCopyFeedback(copied ? 'Copied' : 'Copy didn’t work. Select the reference to copy it.')
  }

  if (!booking) {
    return (
      <section className="message-panel confirmation-missing">
        <p className="eyebrow">Confirmation</p>
        <h1>We couldn’t load your confirmation</h1>
        <p>We couldn't load your confirmation. If you just booked, use Find my booking with your reference and email.</p>
        <div className="confirmation-actions">
          <Link className="button button-primary" to="/find-booking">Find my booking</Link>
          <Link className="button button-secondary" to="/">Back to hotels</Link>
        </div>
      </section>
    )
  }

  return (
    <section className="confirmation-page">
      <div className="confirmation-heading">
        <span className="confirmation-check" aria-hidden="true">✓</span>
        <p className="eyebrow">Booking confirmed</p>
        <h1>Your stay is reserved</h1>
      </div>

      <div className="confirmation-card">
        <div className="reference-block">
          <div>
            <p className="eyebrow">Your booking reference</p>
            <p className="booking-reference">{booking.reference}</p>
          </div>
          <button className="button button-secondary" type="button" onClick={copyReference}>Copy reference</button>
        </div>
        {copyFeedback && <p className="copy-feedback" role="status">{copyFeedback}</p>}

        <dl className="confirmation-details">
          <div><dt>Hotel</dt><dd>{booking.hotelName}</dd></div>
          <div><dt>Guest</dt><dd>{booking.guestName}</dd></div>
          <div><dt>Email</dt><dd>{booking.guestEmail}</dd></div>
          <div><dt>Check-in</dt><dd>{booking.checkIn}</dd></div>
          <div><dt>Check-out</dt><dd>{booking.checkOut}</dd></div>
          <div><dt>Nights</dt><dd>{booking.nights}</dd></div>
          <div><dt>Total</dt><dd>{formatInr(booking.totalAmount)}</dd></div>
          <div><dt>Status</dt><dd><span className="confirmation-status">{booking.status}</span></dd></div>
        </dl>

        <p className="confirmation-note">Keep your reference and email. You will need both to view or cancel your booking.</p>
        <div className="confirmation-actions">
          <Link className="button button-primary" to="/find-booking" state={{ reference: booking.reference }}>Find my booking</Link>
          <Link className="button button-secondary" to="/">Back to hotels</Link>
        </div>
      </div>
    </section>
  )
}
