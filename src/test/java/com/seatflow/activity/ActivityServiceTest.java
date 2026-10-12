package com.seatflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.activity.dto.ActivityResponse;

/**
 * Plain unit test: no Spring context, no database.
 *
 * <p>The service is built with a mocked repository, which is only possible
 * because the dependency arrives through the constructor.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void mapsEntityToResponseAndComputesSeatsLeft() {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("AI meetup");
        activity.setLocation("Main hall");
        activity.setCapacity(100);
        activity.setRegisteredCount(37);
        activity.setRegistrationStart(LocalDateTime.of(2026, 11, 1, 9, 0));
        activity.setRegistrationEnd(LocalDateTime.of(2026, 11, 5, 18, 0));
        activity.setStatus(ActivityStatus.PUBLISHED);

        given(activityRepository.findByStatusOrderByRegistrationStartAsc(ActivityStatus.PUBLISHED))
                .willReturn(List.of(activity));

        List<ActivityResponse> result = activityService.listPublishedActivities();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("AI meetup");
        assertThat(result.get(0).seatsLeft()).isEqualTo(63);
        verify(activityRepository).findByStatusOrderByRegistrationStartAsc(ActivityStatus.PUBLISHED);
    }
}
