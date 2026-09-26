package com.shoplocker.fssai.scheduler;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class RandomTimeTrigger implements Trigger {

    private static final int MIN_HOURS = 6;
    private static final int MAX_HOURS = 22;

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant now = Instant.now();

        int randomHours = (int) (Math.random() * (MAX_HOURS - MIN_HOURS + 1)) + MIN_HOURS;

        Instant minFuture = now.plus(1, ChronoUnit.HOURS);
        Instant randomFuture = now.plus(randomHours, ChronoUnit.HOURS);

        return minFuture.isAfter(randomFuture) ? minFuture : randomFuture;
    }
}