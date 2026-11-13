import { ApiError } from './ApiError'
import type { ApiErrorResponse, HotelSearchParams, HotelSummary } from './types'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) return false

  const body = value as Partial<ApiErrorResponse>
  return typeof body.message === 'string'
}

async function request<T>(path: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`)
  } catch {
    throw new ApiError('Unable to reach the hotel service. Please try again.')
  }

  if (!response.ok) {
    let body: unknown
    try {
      body = await response.json()
    } catch {
      throw new ApiError(`The request failed (${response.status}).`, [], response.status)
    }

    if (isApiErrorResponse(body)) {
      throw new ApiError(body.message, body.fieldErrors ?? [], body.status)
    }
    throw new ApiError(`The request failed (${response.status}).`, [], response.status)
  }

  return (await response.json()) as T
}

export async function getHotels(params: HotelSearchParams = {}): Promise<HotelSummary[]> {
  const query = new URLSearchParams()
  if (params.location) query.set('location', params.location)
  if (params.checkIn) query.set('checkIn', params.checkIn)
  if (params.checkOut) query.set('checkOut', params.checkOut)
  const queryString = query.toString()
  return request<HotelSummary[]>(`/hotels${queryString ? `?${queryString}` : ''}`)
}
