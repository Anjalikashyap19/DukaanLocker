package com.shoplocker.fssai.config;

import com.shoplocker.fssai.scheduler.DocumentExpiryScheduler;
import com.shoplocker.fssai.scheduler.DocumentMissingScheduler;
import com.shoplocker.fssai.scheduler.NoBusinessScheduler;
import com.shoplocker.fssai.scheduler.RandomTimeTrigger;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private final DocumentExpiryScheduler documentExpiryScheduler;
    private final DocumentMissingScheduler documentMissingScheduler;
    private final NoBusinessScheduler noBusinessScheduler;
    private final RandomTimeTrigger randomTimeTrigger;

    public SchedulingConfig(DocumentExpiryScheduler documentExpiryScheduler,
                            DocumentMissingScheduler documentMissingScheduler,
                            NoBusinessScheduler noBusinessScheduler,
                            RandomTimeTrigger randomTimeTrigger) {
        this.documentExpiryScheduler = documentExpiryScheduler;
        this.documentMissingScheduler = documentMissingScheduler;
        this.noBusinessScheduler = noBusinessScheduler;
        this.randomTimeTrigger = randomTimeTrigger;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(documentExpiryScheduler::checkExpiringDocuments, randomTimeTrigger);
        registrar.addTriggerTask(documentMissingScheduler::checkMissingDocuments, randomTimeTrigger);
        registrar.addTriggerTask(noBusinessScheduler::checkUsersWithoutBusiness, randomTimeTrigger);
    }
}
