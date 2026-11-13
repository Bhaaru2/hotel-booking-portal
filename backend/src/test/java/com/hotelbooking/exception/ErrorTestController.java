package com.hotelbooking.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/test-errors")
class ErrorTestController {

    @PostMapping("/body")
    void body(@Valid @RequestBody BodyRequest body) {
    }

    @GetMapping("/constraint")
    String constraint(@RequestParam @Min(1) int count) {
        return "ok";
    }

    @GetMapping("/required")
    String required(@RequestParam String email) {
        return "ok";
    }

    @GetMapping("/date")
    String date(@RequestParam java.time.LocalDate checkIn) {
        return "ok";
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException("Hotel was not found.");
    }

    @GetMapping("/bad-request")
    void badRequest() {
        throw new BadRequestException("Invalid booking request.");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("No rooms are available.");
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("private details");
    }

    record BodyRequest(@NotBlank String guestName, Integer amount) {
    }
}
