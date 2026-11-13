import { useEffect, useState, type FormEvent } from 'react'
import { localToday, validateDateRange } from '../utils/dates'

interface HotelDateFormProps {
  initialCheckIn: string
  initialCheckOut: string
  onUpdate: (checkIn: string, checkOut: string) => void
}

export function HotelDateForm({ initialCheckIn, initialCheckOut, onUpdate }: HotelDateFormProps) {
  const today = localToday()
  const [checkIn, setCheckIn] = useState(initialCheckIn)
  const [checkOut, setCheckOut] = useState(initialCheckOut)
  const [validationMessage, setValidationMessage] = useState<string | null>(null)

  useEffect(() => {
    setCheckIn(initialCheckIn)
    setCheckOut(initialCheckOut)
    setValidationMessage(validateDateRange(initialCheckIn, initialCheckOut))
  }, [initialCheckIn, initialCheckOut])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = validateDateRange(checkIn, checkOut)
    setValidationMessage(message)
    if (!message) onUpdate(checkIn, checkOut)
  }

  return (
    <form className="detail-date-panel" onSubmit={handleSubmit} noValidate>
      <h2>Choose your dates</h2>
      <div className="detail-date-fields">
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
        <button className="button button-primary" type="submit">Update dates</button>
      </div>
      {validationMessage && <p className="field-error" role="alert">{validationMessage}</p>}
    </form>
  )
}
