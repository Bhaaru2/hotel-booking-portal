import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layout/AppLayout'
import { FindBookingPage } from './pages/FindBookingPage'
import { HotelSearchPage } from './pages/HotelSearchPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { HotelDetailsPage } from './pages/HotelDetailsPage'
import { BookingPage } from './pages/BookingPage'
import { BookingConfirmationPage } from './pages/BookingConfirmationPage'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<HotelSearchPage />} />
          <Route path="/hotels/:id" element={<HotelDetailsPage />} />
          <Route path="/hotels/:id/book" element={<BookingPage />} />
          <Route path="/booking/confirmed" element={<BookingConfirmationPage />} />
          <Route path="/find-booking" element={<FindBookingPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
