package com.hotelbooking.service;

import com.hotelbooking.dto.HotelResponse;
import com.hotelbooking.repository.HotelRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Transactional(readOnly = true)
    public List<HotelResponse> getHotels(String location) {
        var hotels = location == null || location.isBlank()
                ? hotelRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                : hotelRepository.findByLocationContainingIgnoreCaseOrderByNameAsc(location.trim());

        return hotels.stream().map(HotelResponse::from).toList();
    }
}
