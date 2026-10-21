package com.seatflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.seatflow.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.activity.dto.ActivityResponse;
import com.seatflow.common.BusinessException;

/**
 * Plain unit test: no Spring context, no database.
 *
 * <p>The service is built with a mocked repository, which is only possible
 * because the dependency arrives through the constructor.
 *
 * <p>中文批注：服务层测试。之所以能确定性地断言 registrationOpen，靠的是 setUp 里把时钟固定成
 * 2026-11-03 12:00，再把假仓储与假时钟一起手工注入被测对象（依赖是构造器注入，所以能直接 new）。
 * 注意 Clock.fixed(...) 是 JDK 自带的真实对象、只是时间被冻结，不是 Mockito 的 mock；
 * 本测试里唯一的 mock 是 Repository。
 * 四个用例分别覆盖：窗口内 → 开放；窗口已结束 → 关闭且余票下限截断为 0；
 * 按 id 查到 → 正常返回；按 id 查不到 → 抛出业务异常（对应接口的 404）。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Mock
    private ActivityRepository activityRepository;

    private ActivityService activityService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        activityService = new ActivityService(activityRepository, fixedClock, currentUserProvider);
    }

    @Test
    void mapsEntityAndMarksRegistrationOpenInsideWindow() {
        Activity activity = activity(100, 37, NOW.minusDays(2), NOW.plusDays(2));

        given(activityRepository.findByStatusOrderByRegistrationStartAsc(ActivityStatus.PUBLISHED))
                .willReturn(List.of(activity));

        List<ActivityResponse> result = activityService.listPublishedActivities();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).seatsLeft()).isEqualTo(63);
        assertThat(result.get(0).registrationOpen()).isTrue();
    }

    @Test
    void marksRegistrationClosedAfterWindowAndClampsSeatsAtZero() {
        Activity activity = activity(50, 50, NOW.minusDays(10), NOW.minusDays(1));

        given(activityRepository.findByStatusOrderByRegistrationStartAsc(ActivityStatus.PUBLISHED))
                .willReturn(List.of(activity));

        List<ActivityResponse> result = activityService.listPublishedActivities();

        assertThat(result.get(0).registrationOpen()).isFalse();
        assertThat(result.get(0).seatsLeft()).isZero();
    }

    @Test
    void returnsActivityWhenActivityExists() {
        Activity activity = activity(100, 37, NOW.minusDays(2), NOW.plusDays(2));

        given(activityRepository.findById(1L)).willReturn(Optional.of(activity));

        ActivityResponse result = activityService.getActivity(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("AI meetup");
        assertThat(result.seatsLeft()).isEqualTo(63);
        assertThat(result.registrationOpen()).isTrue();
    }

    @Test
    void throwsWhenActivityDoesNotExist() {
        given(activityRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getActivity(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("activity not found: 99");
    }

    private Activity activity(int capacity, int registeredCount,
                              LocalDateTime start, LocalDateTime end) {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("AI meetup");
        activity.setLocation("Main hall");
        activity.setCapacity(capacity);
        activity.setRegisteredCount(registeredCount);
        activity.setRegistrationStart(start);
        activity.setRegistrationEnd(end);
        activity.setStatus(ActivityStatus.PUBLISHED);
        return activity;
    }
}
