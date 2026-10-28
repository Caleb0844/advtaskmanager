package com.caleb.advtaskmanager.Config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskSchedulingConfiguration {

    @Bean
    public Clock taskClock(@Value("${tasks.time-zone:Africa/Nairobi}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
