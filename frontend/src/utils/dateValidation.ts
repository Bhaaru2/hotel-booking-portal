export function localToday(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

export function validateDateRange(checkIn: string, checkOut: string): string | null {
  if ((checkIn && !checkOut) || (!checkIn && checkOut)) {
    return 'Please select both check-in and check-out dates.'
  }
  if (!checkIn && !checkOut) return null
  if (checkOut <= checkIn) return 'Check-out must be after check-in.'
  if (checkIn < localToday()) return 'Check-in cannot be in the past.'
  return null
}
