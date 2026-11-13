package com.hotelbooking.service;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }
}
