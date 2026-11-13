import { Link, Outlet } from 'react-router-dom'

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="header-inner">
          <Link className="brand" to="/">Hotel Booking Portal</Link>
          <nav aria-label="Main navigation">
            <Link className="nav-link" to="/find-booking">Find my booking</Link>
          </nav>
        </div>
      </header>
      <main className="main-content">
        <Outlet />
      </main>
      <footer className="site-footer">Find a stay that feels like yours.</footer>
    </div>
  )
}
