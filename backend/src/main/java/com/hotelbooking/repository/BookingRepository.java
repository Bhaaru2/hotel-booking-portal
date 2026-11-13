package com.hotelbooking.repository;

import com.hotelbooking.entity.Booking;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByReference(String reference);

    Optional<Booking> findByReferenceAndGuestEmail(String reference, String guestEmail);
}
