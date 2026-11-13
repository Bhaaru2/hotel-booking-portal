package com.hotelbooking.repository;

import com.hotelbooking.entity.Booking;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByReference(String reference);

    Optional<Booking> findByReferenceAndGuestEmail(String reference, String guestEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            where booking.reference = :reference
              and booking.guestEmail = :guestEmail
            """)
    Optional<Booking> findForCancellation(
            @Param("reference") String reference,
            @Param("guestEmail") String guestEmail);
}
