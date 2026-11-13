import { ApiError } from '../api/ApiError'

interface ErrorMessageProps {
  error: Error
  onRetry: () => void
  title?: string
}

export function ErrorMessage({ error, onRetry, title = 'We couldn’t load hotels' }: ErrorMessageProps) {
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : []

  return (
    <section className="error-panel" role="alert" aria-live="assertive">
      <div>
        <h2>{title}</h2>
        <p>{error.message}</p>
        {fieldErrors.length > 0 && (
          <ul className="api-field-errors">
            {fieldErrors.map((fieldError) => (
              <li key={`${fieldError.field}-${fieldError.message}`}>
                <strong>{fieldError.field}:</strong> {fieldError.message}
              </li>
            ))}
          </ul>
        )}
      </div>
      <button className="button button-secondary" type="button" onClick={onRetry}>Retry</button>
    </section>
  )
}
