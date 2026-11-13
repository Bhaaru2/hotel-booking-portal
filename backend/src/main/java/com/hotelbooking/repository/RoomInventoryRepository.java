package com.hotelbooking.repository;

import com.hotelbooking.entity.RoomInventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long> {
}
