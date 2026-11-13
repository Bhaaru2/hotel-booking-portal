import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getHotels } from '../api/client'
import type { HotelSearchParams, HotelSummary } from '../api/types'
import { ErrorMessage } from '../components/ErrorMessage'
import { HotelCard } from '../components/HotelCard'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { SearchForm } from '../components/SearchForm'
import { validateDateRange } from '../utils/dateValidation'

function selectedSearch(params: URLSearchParams): HotelSearchParams {
  return {
    location: params.get('location') ?? undefined,
    checkIn: params.get('checkIn') ?? undefined,
    checkOut: params.get('checkOut') ?? undefined,
  }
}

export function HotelSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryString = searchParams.toString()
  const selected = selectedSearch(new URLSearchParams(queryString))
  const [hotels, setHotels] = useState<HotelSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    const currentSearch = selectedSearch(new URLSearchParams(queryString))
    const validationMessage = validateDateRange(currentSearch.checkIn ?? '', currentSearch.checkOut ?? '')
    if (validationMessage) {
      setHotels([])
      setError(null)
      setLoading(false)
      return
    }

    let active = true
    setLoading(true)
    setError(null)
    getHotels(currentSearch)
      .then((result) => {
        if (active) setHotels(result)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason : new Error('Unable to load hotels.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [queryString, retryCount])

  function handleSearch(values: HotelSearchParams) {
    const next = new URLSearchParams()
    if (values.location) next.set('location', values.location)
    if (values.checkIn) next.set('checkIn', values.checkIn)
    if (values.checkOut) next.set('checkOut', values.checkOut)
    setSearchParams(next)
    setRetryCount((count) => count + 1)
  }

  function handleClear() {
    setSearchParams(new URLSearchParams())
  }

  return (
    <>
      <section className="page-intro">
        <p className="eyebrow">A better stay starts here</p>
        <h1>Find your next getaway</h1>
        <p>Search stays across India by destination and dates.</p>
      </section>

      <SearchForm initialValues={selected} onSearch={handleSearch} onClear={handleClear} />

      <section className="results-section" aria-labelledby="results-heading">
        <div className="results-heading">
          <div>
            <p className="eyebrow">Handpicked stays</p>
            <h2 id="results-heading">Hotels for your next trip</h2>
          </div>
          {!loading && !error && hotels.length > 0 && (
            <p className="result-count">{hotels.length} {hotels.length === 1 ? 'stay' : 'stays'}</p>
          )}
        </div>

        {loading && <LoadingSpinner />}
        {!loading && error && <ErrorMessage error={error} onRetry={() => setRetryCount((count) => count + 1)} />}
        {!loading && !error && hotels.length === 0 && (
          <div className="empty-state" role="status">No hotels match your search.</div>
        )}
        {!loading && !error && hotels.length > 0 && (
          <div className="hotel-grid">
            {hotels.map((hotel) => (
              <HotelCard key={hotel.id} hotel={hotel} searchQuery={queryString} />
            ))}
          </div>
        )}
      </section>
    </>
  )
}
