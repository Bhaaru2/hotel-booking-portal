import { Link } from 'react-router-dom'
import type { AlternativeDateSummary } from '../api/types'
import { formatInr } from '../utils/currency'

interface AlternativeDatesProps {
  hotelId: number
  alternatives: AlternativeDateSummary[]
  searchQuery?: string
  compact?: boolean
}

function formatDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return value
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' })
    .format(new Date(year, month - 1, day))
}

function suggestionPath(hotelId: number, suggestion: AlternativeDateSummary, searchQuery: string): string {
  const query = new URLSearchParams(searchQuery)
  query.set('checkIn', suggestion.checkIn)
  query.set('checkOut', suggestion.checkOut)
  return `/hotels/${hotelId}?${query.toString()}`
}

export function AlternativeDates({ hotelId, alternatives, searchQuery = '', compact = false }: AlternativeDatesProps) {
  const suggestions = alternatives.slice(0, 3)

  return (
    <section className={`alternative-dates ${compact ? 'alternative-dates-compact' : ''}`} aria-label="Nearby available dates">
      <h3>{compact ? 'Nearby dates' : 'Nearby dates with rooms available'}</h3>
      <ul>
        {suggestions.map((suggestion) => (
          <li key={`${suggestion.checkIn}-${suggestion.checkOut}`}>
            <Link className="alternative-date-link" to={suggestionPath(hotelId, suggestion, searchQuery)}>
              <span className="alternative-date-range">{formatDate(suggestion.checkIn)} - {formatDate(suggestion.checkOut)}</span>
              <span>{suggestion.nights} {suggestion.nights === 1 ? 'night' : 'nights'}</span>
              <span>{suggestion.roomsLeft} {suggestion.roomsLeft === 1 ? 'room' : 'rooms'} left</span>
              <strong>{formatInr(suggestion.totalAmount)}</strong>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
