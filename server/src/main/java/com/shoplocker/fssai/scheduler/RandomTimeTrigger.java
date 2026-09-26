package com.shoplocker.fssai.scheduler;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class RandomTimeTrigger implements Trigger {

    private static final int MIN_HOURS = 6;
    private static final int MAX_HOURS = 22;

    @Override
    public Date nextExecution(TriggerContext triggerContext) {
        long now = System.currentTimeMillis();

        // Calculate random hours within configured range
        int randomHours = (int) (Math.random() * (MAX_HOURS - MIN_HOURS + 1)) + MIN_HOURS;

        // Also ensure we don't schedule too soon - at least 1 hour in future
        long minFuture = now + TimeUnit.HOURS.toMillis(1);
        long randomFuture = now + TimeUnit.HOURS.toMillis(randomHours);

        // Use the later of "at least 1 hour from now" or "random time today"
        long finalFuture = Math.max(minFuture, randomFuture);

        return new Date(finalFuture);
    }
}