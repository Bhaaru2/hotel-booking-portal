import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layout/AppLayout'
import { ComingSoonPage } from './pages/ComingSoonPage'
import { HotelSearchPage } from './pages/HotelSearchPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { HotelDetailsPage } from './pages/HotelDetailsPage'
import { BookingPlaceholderPage } from './pages/BookingPlaceholderPage'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<HotelSearchPage />} />
          <Route path="/hotels/:id" element={<HotelDetailsPage />} />
          <Route path="/hotels/:id/book" element={<BookingPlaceholderPage />} />
          <Route path="/coming-soon" element={<ComingSoonPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
