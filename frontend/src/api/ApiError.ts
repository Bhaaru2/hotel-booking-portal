import type { FieldError } from './types'

export class ApiError extends Error {
  readonly fieldErrors: FieldError[]
  readonly status: number | undefined

  constructor(message: string, fieldErrors: FieldError[] = [], status?: number) {
    super(message)
    this.name = 'ApiError'
    this.fieldErrors = fieldErrors
    this.status = status
  }
}
