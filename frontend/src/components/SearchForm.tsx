import { useEffect, useState, type FormEvent } from 'react'
import type { HotelSearchParams } from '../api/types'
import { localToday, validateDateRange } from '../utils/dateValidation'

interface SearchFormProps {
  initialValues: HotelSearchParams
  onSearch: (values: HotelSearchParams) => void
  onClear: () => void
}

export function SearchForm({ initialValues, onSearch, onClear }: SearchFormProps) {
  const today = localToday()
  const [location, setLocation] = useState(initialValues.location ?? '')
  const [checkIn, setCheckIn] = useState(initialValues.checkIn ?? '')
  const [checkOut, setCheckOut] = useState(initialValues.checkOut ?? '')
  const [validationMessage, setValidationMessage] = useState<string | null>(null)

  useEffect(() => {
    setLocation(initialValues.location ?? '')
    setCheckIn(initialValues.checkIn ?? '')
    setCheckOut(initialValues.checkOut ?? '')
    setValidationMessage(validateDateRange(initialValues.checkIn ?? '', initialValues.checkOut ?? ''))
  }, [initialValues.location, initialValues.checkIn, initialValues.checkOut])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = validateDateRange(checkIn, checkOut)
    setValidationMessage(message)
    if (message) return

    onSearch({
      location: location.trim(),
      checkIn,
      checkOut,
    })
  }

  function handleClear() {
    setLocation('')
    setCheckIn('')
    setCheckOut('')
    setValidationMessage(null)
    onClear()
  }

  return (
    <form className="search-panel" onSubmit={handleSubmit} noValidate>
      <div className="search-fields">
        <label className="field-group field-location">
          <span>Destination</span>
          <input
            type="text"
            name="location"
            value={location}
            onChange={(event) => setLocation(event.target.value)}
            placeholder="City or location"
            autoComplete="off"
          />
        </label>
        <label className="field-group">
          <span>Check-in</span>
          <input
            type="date"
            name="checkIn"
            min={today}
            value={checkIn}
            onChange={(event) => setCheckIn(event.target.value)}
          />
        </label>
        <label className="field-group">
          <span>Check-out</span>
          <input
            type="date"
            name="checkOut"
            min={checkIn > today ? checkIn : today}
            value={checkOut}
            onChange={(event) => setCheckOut(event.target.value)}
          />
        </label>
        <div className="search-actions">
          <button className="button button-primary" type="submit">Search stays</button>
          <button className="button button-secondary" type="button" onClick={handleClear}>Clear</button>
        </div>
      </div>
      {validationMessage && <p className="field-error" role="alert">{validationMessage}</p>}
    </form>
  )
}
