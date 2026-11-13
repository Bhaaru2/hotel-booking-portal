import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import { createBooking, getHotel } from '../api/client'
import type { BookingRequest, BookingResponse, HotelSummary } from '../api/types'
import { HotelNotFound } from '../components/HotelNotFound'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { formatInr } from '../utils/currency'
import { validateDateRange } from '../utils/dates'

type GuestField = 'guestName' | 'guestEmail'
type GuestFieldErrors = Record<GuestField, string[]>

interface SubmissionError {
  message: string
  soldOut: boolean
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const GENERIC_ERROR = 'Something went wrong, please try again.'

function emptyFieldErrors(): GuestFieldErrors {
  return { guestName: [], guestEmail: [] }
}

function resultsPath(query: string): string {
  return `/${query ? `?${query}` : ''}`
}

function detailsPath(hotelId: string | undefined, query: string): string {
  return `/hotels/${hotelId}${query ? `?${query}` : ''}`
}

export function BookingPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const queryString = searchParams.toString()
  const checkIn = searchParams.get('checkIn') ?? ''
  const checkOut = searchParams.get('checkOut') ?? ''
  const dateValidationMessage = validateDateRange(checkIn, checkOut)
  const navigate = useNavigate()

  const [hotel, setHotel] = useState<HotelSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState<Error | null>(null)
  const [retryCount, setRetryCount] = useState(0)
  const [guestName, setGuestName] = useState('')
  const [guestEmail, setGuestEmail] = useState('')
  const [fieldErrors, setFieldErrors] = useState<GuestFieldErrors>(emptyFieldErrors)
  const [submissionError, setSubmissionError] = useState<SubmissionError | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)

  useEffect(() => {
    const hotelId = Number(id)
    if (!id || !Number.isSafeInteger(hotelId) || hotelId <= 0) {
      setHotel(null)
      setNotFound(true)
      setLoadError(null)
      setLoading(false)
      return
    }
    if (dateValidationMessage) {
      setHotel(null)
      setNotFound(false)
      setLoadError(null)
      setLoading(false)
      return
    }

    let active = true
    setLoading(true)
    setLoadError(null)
    setNotFound(false)
    getHotel(hotelId, { checkIn, checkOut })
      .then((result) => {
        if (active) setHotel(result)
      })
      .catch((reason: unknown) => {
        if (!active) return
        setHotel(null)
        if (reason instanceof ApiError && reason.status === 404) {
          setNotFound(true)
        } else {
          setLoadError(reason instanceof Error ? reason : new Error(GENERIC_ERROR))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [id, checkIn, checkOut, dateValidationMessage, retryCount])

  function validateGuest(): GuestFieldErrors {
    const errors = emptyFieldErrors()
    const trimmedName = guestName.trim()
    const trimmedEmail = guestEmail.trim()

    if (!trimmedName) errors.guestName.push('Full name is required.')
    else if (trimmedName.length > 100) errors.guestName.push('Full name must be 100 characters or fewer.')

    if (!trimmedEmail) errors.guestEmail.push('Email is required.')
    else if (!EMAIL_PATTERN.test(trimmedEmail)) errors.guestEmail.push('Enter a valid email address.')
    else if (trimmedEmail.length > 150) errors.guestEmail.push('Email must be 150 characters or fewer.')

    return errors
  }

  function handleApiError(reason: unknown) {
    if (reason instanceof ApiError && reason.status === 409) {
      setSubmissionError({ message: 'Sorry, these dates just sold out.', soldOut: true })
      return
    }

    if (reason instanceof ApiError && reason.status === 400 && reason.fieldErrors.length > 0) {
      const mapped = emptyFieldErrors()
      let hasUnmappedField = false
      for (const fieldError of reason.fieldErrors) {
        if (fieldError.field === 'guestName' || fieldError.field === 'guestEmail') {
          mapped[fieldError.field].push(fieldError.message)
        } else {
          hasUnmappedField = true
        }
      }
      setFieldErrors(mapped)
      setSubmissionError(hasUnmappedField
        ? { message: reason.message || GENERIC_ERROR, soldOut: false }
        : null)
      return
    }

    setSubmissionError({
      message: reason instanceof Error && reason.message ? reason.message : GENERIC_ERROR,
      soldOut: false,
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current || !hotel || !id) return

    const validationErrors = validateGuest()
    setFieldErrors(validationErrors)
    setSubmissionError(null)
    if (validationErrors.guestName.length > 0 || validationErrors.guestEmail.length > 0) return

    const payload: BookingRequest = {
      hotelId: hotel.id,
      checkIn,
      checkOut,
      guestName: guestName.trim(),
      guestEmail: guestEmail.trim(),
    }
    submittingRef.current = true
    setSubmitting(true)
    try {
      const response: BookingResponse = await createBooking(payload)
      navigate('/booking/confirmed', { state: response, replace: true })
    } catch (reason: unknown) {
      handleApiError(reason)
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  if (dateValidationMessage) {
    return (
      <section className="message-panel booking-guard">
        <p className="eyebrow">Dates needed</p>
        <h1>Choose valid stay dates</h1>
        <p role="alert" className="field-error">{dateValidationMessage}</p>
        <Link className="text-link" to={detailsPath(id, queryString)}>Back to hotel details</Link>
      </section>
    )
  }

  if (notFound) return <HotelNotFound backTo={resultsPath(queryString)} />
  if (loading) return <LoadingSpinner />
  if (loadError) {
    return (
      <section className="booking-load-error">
        <div className="error-panel" role="alert">
          <div><h2>We couldn’t load this hotel</h2><p>{loadError.message || GENERIC_ERROR}</p></div>
          <button className="button button-secondary" type="button" onClick={() => setRetryCount((count) => count + 1)}>Retry</button>
        </div>
      </section>
    )
  }
  if (!hotel) return null

  if (hotel.available === false) {
    return (
      <section className="message-panel booking-guard">
        <p className="eyebrow">Availability changed</p>
        <h1>Sold out for these dates</h1>
        <p>There are no rooms left for the stay you selected.</p>
        <Link className="button button-secondary" to={detailsPath(id, queryString)}>Back to hotel</Link>
      </section>
    )
  }

  if (hotel.available !== true || hotel.totalAmount === undefined || hotel.nights === undefined) {
    return (
      <section className="message-panel booking-guard">
        <p className="eyebrow">Quote unavailable</p>
        <h1>We couldn’t confirm availability</h1>
        <p>Please return to the hotel and check your dates again.</p>
        <Link className="button button-secondary" to={detailsPath(id, queryString)}>Back to hotel</Link>
      </section>
    )
  }

  return (
    <div className="booking-page">
      <Link className="back-link" to={detailsPath(id, queryString)}>← Back to hotel</Link>
      <div className="booking-layout">
        <section className="booking-summary-card" aria-labelledby="booking-summary-heading">
          <p className="eyebrow">Review your stay</p>
          <h1 id="booking-summary-heading">{hotel.name}</h1>
          <p className="booking-location">{hotel.location}</p>
          <dl className="booking-summary-list">
            <div><dt>Check-in</dt><dd>{hotel.checkIn ?? checkIn}</dd></div>
            <div><dt>Check-out</dt><dd>{hotel.checkOut ?? checkOut}</dd></div>
            <div><dt>Nights</dt><dd>{hotel.nights}</dd></div>
            <div><dt>Price per night</dt><dd>{formatInr(hotel.pricePerNight)}</dd></div>
          </dl>
          <div className="booking-total"><span>Total</span><strong>{formatInr(hotel.totalAmount)}</strong></div>
        </section>

        <section className="guest-form-panel" aria-labelledby="guest-form-heading">
          <p className="eyebrow">One more step</p>
          <h2 id="guest-form-heading">Guest details</h2>

          {submissionError && (
            <div className={`booking-error-banner ${submissionError.soldOut ? 'booking-error-sold-out' : ''}`} role="alert">
              <p>{submissionError.message}</p>
              {submissionError.soldOut && (
                <Link className="button button-secondary" to={detailsPath(id, queryString)}>Back to hotel</Link>
              )}
            </div>
          )}

          <form className="guest-form" onSubmit={handleSubmit} noValidate>
            <label className="field-group">
              <span>Full name</span>
              <input
                type="text"
                name="guestName"
                autoComplete="name"
                maxLength={100}
                value={guestName}
                aria-invalid={fieldErrors.guestName.length > 0}
                aria-describedby={fieldErrors.guestName.length > 0 ? 'guest-name-error' : undefined}
                onChange={(event) => {
                  setGuestName(event.target.value)
                  setFieldErrors((current) => ({ ...current, guestName: [] }))
                }}
              />
              {fieldErrors.guestName.map((message, index) => (
                <span className="field-error" id="guest-name-error" key={`${message}-${index}`}>{message}</span>
              ))}
            </label>

            <label className="field-group">
              <span>Email</span>
              <input
                type="email"
                name="guestEmail"
                autoComplete="email"
                maxLength={150}
                value={guestEmail}
                aria-invalid={fieldErrors.guestEmail.length > 0}
                aria-describedby={fieldErrors.guestEmail.length > 0 ? 'guest-email-error' : undefined}
                onChange={(event) => {
                  setGuestEmail(event.target.value)
                  setFieldErrors((current) => ({ ...current, guestEmail: [] }))
                }}
              />
              {fieldErrors.guestEmail.map((message, index) => (
                <span className="field-error" id="guest-email-error" key={`${message}-${index}`}>{message}</span>
              ))}
            </label>

            <div className="confirm-total"><span>Total</span><strong>{formatInr(hotel.totalAmount)}</strong></div>
            <button className="button button-primary confirm-button" type="submit" disabled={submitting}>
              {submitting ? 'Confirming...' : 'Confirm booking'}
            </button>
          </form>
        </section>
      </div>
    </div>
  )
}
