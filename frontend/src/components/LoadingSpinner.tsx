export function LoadingSpinner() {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>Finding stays…</span>
    </div>
  )
}
