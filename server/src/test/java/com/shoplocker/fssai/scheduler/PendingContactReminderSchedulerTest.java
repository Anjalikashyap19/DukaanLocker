package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PendingContactReminderScheduler")
class PendingContactReminderSchedulerTest {

    @Test
    @DisplayName("sends one deduped in-app reminder per pending user")
    void sendsReminders() {
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        User user = new User();
        user.setId(42L);
        user.setContactEmailPending(true);
        user.setContactMobilePending(true);
        when(users.findByContactEmailPendingTrueOrContactMobilePendingTrue()).thenReturn(List.of(user));
        when(notifications.sendPushAndCreateNotification(
                eq(42L), anyString(), anyString(), anyString(), isNull(), anyMap(), anyString()))
                .thenReturn(true);

        new PendingContactReminderScheduler(users, notifications).sendReminders();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendPushAndCreateNotification(
                eq(42L), anyString(), anyString(), eq("CONTACT_DETAILS_PENDING"),
                isNull(), anyMap(), key.capture());
        assertThat(key.getValue()).matches("CONTACT_PENDING_\\d{8}_(0|6|12|18)");
    }
}
