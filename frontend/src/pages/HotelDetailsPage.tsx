import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import { getHotel } from '../api/client'
import type { HotelSearchParams, HotelSummary } from '../api/types'
import { ErrorMessage } from '../components/ErrorMessage'
import { AlternativeDates } from '../components/AlternativeDates'
import { HotelDateForm } from '../components/HotelDateForm'
import { HotelNotFound } from '../components/HotelNotFound'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { formatInr } from '../utils/currency'
import { validateDateRange } from '../utils/dates'

function backToResults(searchParams: URLSearchParams): string {
  const query = searchParams.toString()
  return `/${query ? `?${query}` : ''}`
}

function bookPath(hotelId: number, checkIn: string, checkOut: string): string {
  const query = new URLSearchParams({ checkIn, checkOut })
  return `/hotels/${hotelId}/book?${query.toString()}`
}

export function HotelDetailsPage() {
  const { id } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryString = searchParams.toString()
  const [hotel, setHotel] = useState<HotelSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [retryCount, setRetryCount] = useState(0)

  const selected = new URLSearchParams(queryString)
  const checkIn = selected.get('checkIn') ?? ''
  const checkOut = selected.get('checkOut') ?? ''
  const location = selected.get('location') ?? undefined

  useEffect(() => {
    const hotelId = Number(id)
    if (!id || !Number.isSafeInteger(hotelId) || hotelId <= 0) {
      setHotel(null)
      setNotFound(true)
      setError(null)
      setLoading(false)
      return
    }

    const dateError = validateDateRange(checkIn, checkOut)
    let active = true
    setLoading(true)
    setError(null)
    setNotFound(false)
    getHotel(hotelId, dateError ? {} : { checkIn, checkOut })
      .then((result) => {
        if (active) setHotel(result)
      })
      .catch((reason: unknown) => {
        if (!active) return
        setHotel(null)
        if (reason instanceof ApiError && reason.status === 404) {
          setNotFound(true)
        } else {
          setError(reason instanceof Error ? reason : new Error('Unable to load hotel details.'))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [id, checkIn, checkOut, queryString, retryCount])

  function updateDates(nextCheckIn: string, nextCheckOut: string) {
    const next = new URLSearchParams(searchParams)
    if (location) next.set('location', location)
    if (nextCheckIn) next.set('checkIn', nextCheckIn)
    else next.delete('checkIn')
    if (nextCheckOut) next.set('checkOut', nextCheckOut)
    else next.delete('checkOut')
    setSearchParams(next)
  }

  if (notFound) {
    return <HotelNotFound backTo={backToResults(searchParams)} />
  }

  if (loading) return <LoadingSpinner />

  if (error) {
    return <ErrorMessage error={error} title="We couldn’t load this hotel" onRetry={() => setRetryCount((count) => count + 1)} />
  }

  if (!hotel) return null

  const hasQuote = Boolean(
    checkIn && checkOut && hotel.checkIn && hotel.checkOut
      && hotel.nights !== undefined && hotel.totalAmount !== undefined,
  )
  const searchForBackLink: HotelSearchParams = { location, checkIn, checkOut }
  const backQuery = new URLSearchParams()
  if (searchForBackLink.location) backQuery.set('location', searchForBackLink.location)
  if (searchForBackLink.checkIn) backQuery.set('checkIn', searchForBackLink.checkIn)
  if (searchForBackLink.checkOut) backQuery.set('checkOut', searchForBackLink.checkOut)
  const backPath = backToResults(backQuery)

  return (
    <div className="hotel-detail-page">
      <Link className="back-link" to={backPath}>← Back to results</Link>
      <div className="detail-layout">
        <article className="detail-main">
          <p className="eyebrow">{hotel.location}</p>
          <h1>{hotel.name}</h1>
          <p className="detail-description">{hotel.description}</p>
          <section className="detail-amenities" aria-labelledby="amenities-heading">
            <h2 id="amenities-heading">What this stay offers</h2>
            <ul className="amenity-list">
              {hotel.amenities.map((amenity) => <li className="amenity-tag" key={amenity}>{amenity}</li>)}
            </ul>
          </section>
          <div className="detail-nightly-price">
            <span>Price per night</span>
            <strong>{formatInr(hotel.pricePerNight)}</strong>
          </div>
        </article>

        <aside className="detail-sidebar">
          <HotelDateForm initialCheckIn={checkIn} initialCheckOut={checkOut} onUpdate={updateDates} />
          {hasQuote ? (
            <section className="quote-panel" aria-labelledby="quote-heading">
              <p className="eyebrow">Your stay</p>
              <h2 id="quote-heading">Price quote</h2>
              <dl className="quote-list">
                <div><dt>Check-in</dt><dd>{hotel.checkIn}</dd></div>
                <div><dt>Check-out</dt><dd>{hotel.checkOut}</dd></div>
                <div><dt>Nights</dt><dd>{hotel.nights}</dd></div>
                <div><dt>Price per night</dt><dd>{formatInr(hotel.pricePerNight)}</dd></div>
              </dl>
              <p className="price-math">{hotel.nights} {hotel.nights === 1 ? 'night' : 'nights'} × {formatInr(hotel.pricePerNight)}</p>
              <div className="quote-total"><span>Total amount</span><strong>{formatInr(hotel.totalAmount!)}</strong></div>

              {hotel.available ? (
                <div className="availability-result">
                  <span className={`availability-badge ${hotel.roomsLeft !== undefined && hotel.roomsLeft <= 2 ? 'availability-low' : ''}`}>
                    Available
                  </span>
                  <p>{hotel.roomsLeft !== undefined && hotel.roomsLeft <= 2
                    ? `Only ${hotel.roomsLeft} left`
                    : `${hotel.roomsLeft ?? 0} rooms left`}</p>
                  <Link className="button button-primary book-button" to={bookPath(hotel.id, hotel.checkIn!, hotel.checkOut!)}>
                    Book now
                  </Link>
                </div>
              ) : (
                <div className="availability-result">
                  <p className="sold-out-message">Sold out for these dates</p>
                  {hotel.alternativeDates && hotel.alternativeDates.length > 0 ? (
                    <AlternativeDates hotelId={hotel.id} alternatives={hotel.alternativeDates} searchQuery={queryString} />
                  ) : (
                    <p className="no-alternative-dates">No nearby dates available. Try different dates.</p>
                  )}
                  <button className="button button-disabled book-button" type="button" disabled>Book now</button>
                </div>
              )}
            </section>
          ) : (
            <section className="availability-prompt" role="status">
              <span className="prompt-icon" aria-hidden="true">⌖</span>
              <h2>Check availability</h2>
              <p>Choose your dates to see room availability and your total price.</p>
            </section>
          )}
        </aside>
      </div>
    </div>
  )
}
