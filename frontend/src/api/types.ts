export interface AlternativeDateSummary {
  checkIn: string
  checkOut: string
  nights: number
  roomsLeft: number
  totalAmount: number
}

export interface HotelSummary {
  id: number
  name: string
  location: string
  description: string
  amenities: string[]
  pricePerNight: number
  checkIn?: string
  checkOut?: string
  nights?: number
  available?: boolean
  roomsLeft?: number
  totalAmount?: number
  alternativeDates?: AlternativeDateSummary[]
}

export interface FieldError {
  field: string
  message: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors: FieldError[]
}

export interface HotelSearchParams {
  location?: string
  checkIn?: string
  checkOut?: string
}
