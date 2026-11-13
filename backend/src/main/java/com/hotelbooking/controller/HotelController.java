package com.hotelbooking.controller;

import com.hotelbooking.dto.HotelResponse;
import com.hotelbooking.service.HotelService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping
    public List<HotelResponse> getHotels(@RequestParam(required = false) String location) {
        return hotelService.getHotels(location);
    }
}
