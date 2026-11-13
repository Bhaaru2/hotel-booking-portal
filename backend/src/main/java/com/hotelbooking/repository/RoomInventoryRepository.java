package com.hotelbooking.repository;

import com.hotelbooking.entity.RoomInventory;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long> {

    @Query("""
            select inventory
            from RoomInventory inventory
            where inventory.hotel.id in :hotelIds
              and inventory.stayDate >= :checkIn
              and inventory.stayDate < :checkOut
            order by inventory.hotel.id, inventory.stayDate
            """)
    List<RoomInventory> findForStay(
            @Param("hotelIds") List<Long> hotelIds,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);

    @Query("""
            select inventory
            from RoomInventory inventory
            where inventory.hotel.id in :hotelIds
              and inventory.stayDate >= :rangeStart
              and inventory.stayDate < :rangeEnd
            order by inventory.hotel.id, inventory.stayDate
            """)
    List<RoomInventory> findForHotelsInRange(
            @Param("hotelIds") List<Long> hotelIds,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inventory
            from RoomInventory inventory
            where inventory.hotel.id = :hotelId
              and inventory.stayDate >= :checkIn
              and inventory.stayDate < :checkOut
            order by inventory.stayDate asc
            """)
    List<RoomInventory> findForStayForUpdate(
            @Param("hotelId") Long hotelId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);
}
