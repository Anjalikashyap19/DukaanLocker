package com.shoplocker.fssai.config;

import com.shoplocker.fssai.scheduler.DocumentExpiryScheduler;
import com.shoplocker.fssai.scheduler.DocumentMissingScheduler;
import com.shoplocker.fssai.scheduler.NoBusinessScheduler;
import com.shoplocker.fssai.scheduler.RandomTimeTrigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private final DocumentExpiryScheduler documentExpiryScheduler;
    private final DocumentMissingScheduler documentMissingScheduler;
    private final NoBusinessScheduler noBusinessScheduler;
    private final RandomTimeTrigger randomTimeTrigger;

    /**
     * Missing-doc drip: evaluated every 15 minutes; the scheduler itself decides
     * which configured time slots (with per-owner jitter) are due and unserved.
     */
    @Value("${notification.missing-doc.cron:0 */15 * * * *}")
    private String missingDocCron;

    /**
     * Expiry alerts: hourly during waking hours (08:00-22:15) so daily escalation
     * stages fire promptly but never during the night.
     */
    @Value("${notification.alert.cron:0 15 8-22 * * *}")
    private String alertCron;

    /**
     * Catch-up pass: runs around the clock and reports only documents that are
     * already past their expiry date, so a document uploaded as already-expired
     * surfaces within minutes instead of waiting for the daytime window. Reuses
     * the same dedupe keys, so it can never double-send.
     */
    @Value("${notification.alert.catchup-cron:0 */30 * * * *}")
    private String catchupCron;

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
        registrar.addTriggerTask(() -> documentExpiryScheduler.checkExpiringDocuments(false),
                new CronTrigger(alertCron));
        registrar.addTriggerTask(() -> documentExpiryScheduler.checkExpiringDocuments(true),
                new CronTrigger(catchupCron));
        registrar.addTriggerTask(documentMissingScheduler::checkMissingDocuments,
                new CronTrigger(missingDocCron));
        registrar.addTriggerTask(noBusinessScheduler::checkUsersWithoutBusiness, randomTimeTrigger);
    }
}
