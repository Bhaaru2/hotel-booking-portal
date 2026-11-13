import { Link } from 'react-router-dom'
import type { HotelSummary } from '../api/types'
import { AlternativeDates } from './AlternativeDates'
import { formatInr } from '../utils/currency'

interface HotelCardProps {
  hotel: HotelSummary
  searchQuery: string
}

export function HotelCard({ hotel, searchQuery }: HotelCardProps) {
  const hasDates = Boolean(hotel.checkIn && hotel.checkOut)
  const detailsPath = `/hotels/${hotel.id}${searchQuery ? `?${searchQuery}` : ''}`

  return (
    <article className="hotel-card">
      <div className="hotel-card-topline">
        <span className="location-label"><span aria-hidden="true">⌖</span> {hotel.location}</span>
        {hasDates && hotel.available && (
          <span className={`availability-badge ${hotel.roomsLeft !== undefined && hotel.roomsLeft <= 2 ? 'availability-low' : ''}`}>
            {hotel.roomsLeft !== undefined && hotel.roomsLeft <= 2
              ? `Only ${hotel.roomsLeft} left`
              : 'Available'}
          </span>
        )}
        {hasDates && hotel.available === false && (
          <span className="availability-badge availability-sold-out">Sold out for these dates</span>
        )}
      </div>

      {hasDates && hotel.available === false && hotel.alternativeDates && hotel.alternativeDates.length > 0 && (
        <AlternativeDates hotelId={hotel.id} alternatives={hotel.alternativeDates} searchQuery={searchQuery} compact />
      )}

      <div className="hotel-card-heading">
        <div>
          <h2>{hotel.name}</h2>
          <p className="hotel-description">{hotel.description}</p>
        </div>
      </div>

      <ul className="amenity-list" aria-label={`Amenities at ${hotel.name}`}>
        {hotel.amenities.map((amenity) => <li className="amenity-tag" key={amenity}>{amenity}</li>)}
      </ul>

      <div className="hotel-card-footer">
        <div className="price-block">
          <p className="nightly-price">{formatInr(hotel.pricePerNight)} <span>/ night</span></p>
          {hasDates && hotel.nights !== undefined && hotel.totalAmount !== undefined && (
            <p className="stay-total">{hotel.nights} {hotel.nights === 1 ? 'night' : 'nights'} · {formatInr(hotel.totalAmount)} total</p>
          )}
        </div>
        <Link className="button button-outline" to={detailsPath}>View details</Link>
      </div>
    </article>
  )
}
