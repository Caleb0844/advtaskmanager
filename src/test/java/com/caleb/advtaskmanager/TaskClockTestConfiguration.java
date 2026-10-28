package com.caleb.advtaskmanager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TaskClockTestConfiguration {

    @Bean
    @Primary
    public Clock fixedTaskClock() {
        return Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneId.of("Africa/Nairobi"));
    }
}
