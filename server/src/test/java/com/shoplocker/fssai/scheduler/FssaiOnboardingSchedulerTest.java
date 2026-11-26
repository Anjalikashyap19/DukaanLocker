package com.shoplocker.fssai.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.service.FssaiOnboardingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FssaiOnboardingScheduler feed parsing")
class FssaiOnboardingSchedulerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("accepts the live feed's bare JSON array")
    void acceptsBareArray() throws Exception {
        JsonNode root = objectMapper.readTree("[\"11518011000297.pdf\",\"22725182000698.pdf\"]");

        JsonNode files = FssaiOnboardingScheduler.feedFiles(root);

        assertThat(files).isNotNull();
        assertThat(files.isArray()).isTrue();
        assertThat(files).hasSize(2);
        assertThat(files.get(0).asText()).isEqualTo("11518011000297.pdf");
    }

    @Test
    @DisplayName("accepts a wrapped new_files array")
    void acceptsWrappedArray() throws Exception {
        JsonNode root = objectMapper.readTree("{\"new_files\":[\"a.pdf\"]}");

        JsonNode files = FssaiOnboardingScheduler.feedFiles(root);

        assertThat(files).isNotNull();
        assertThat(files.isArray()).isTrue();
        assertThat(files).hasSize(1);
    }

    @Test
    @DisplayName("rejects absent or non-array feeds")
    void rejectsOtherShapes() throws Exception {
        assertThat(FssaiOnboardingScheduler.feedFiles(objectMapper.readTree("{}"))).isNull();
        assertThat(FssaiOnboardingScheduler.feedFiles(objectMapper.readTree("{\"new_files\":\"a.pdf\"}"))).isNull();
        assertThat(FssaiOnboardingScheduler.feedFiles(null)).isNull();
    }

    @Test
    @DisplayName("end-of-day sweep retries today's failures and checks the whole feed")
    void endOfDaySweep() {
        FssaiOnboardingService service = mock(FssaiOnboardingService.class);
        when(service.requeueFailedJobsForToday()).thenReturn(List.of("failed.pdf"));
        FssaiOnboardingScheduler scheduler = new FssaiOnboardingScheduler(service, objectMapper) {
            @Override
            List<String> fetchPdfNames() {
                return List.of("a.pdf", "b.pdf");
            }
        };
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        scheduler.endOfDaySweep();

        InOrder order = inOrder(service);
        order.verify(service).requeueFailedJobsForToday();
        order.verify(service).process("failed.pdf");
        order.verify(service).process("a.pdf");
        order.verify(service).process("b.pdf");
    }
}
