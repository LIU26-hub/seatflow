package com.seatflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.seatflow.security.CurrentUser;
import com.seatflow.security.CurrentUserProvider;
import com.seatflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.activity.dto.CreateActivityRequest;
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
    private static final Long ORGANIZER_ID = 7L;
    private static final CurrentUser ORGANIZER =
            new CurrentUser(ORGANIZER_ID, "organizer", UserRole.ORGANIZER);
    private static final CurrentUser PARTICIPANT =
            new CurrentUser(9L, "participant", UserRole.PARTICIPANT);

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

    // ---------- 创建：角色校验与服务端赋值 ----------

    @Test
    void createIsRejectedForParticipant() {
        given(currentUserProvider.require()).willReturn(PARTICIPANT);

        assertThatThrownBy(() -> activityService.createActivity(request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(403));

        then(activityRepository).shouldHaveNoInteractions();
    }

    @Test
    void createStoresOwnerDraftAndZeroCount() {
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.save(any(Activity.class))).willAnswer(invocation -> {
            Activity saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        ActivityResponse result = activityService.createActivity(request());

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        then(activityRepository).should().save(captor.capture());
        Activity saved = captor.getValue();
        assertThat(saved.getOrganizerId()).isEqualTo(ORGANIZER_ID);
        assertThat(saved.getStatus()).isEqualTo(ActivityStatus.DRAFT);
        assertThat(saved.getRegisteredCount()).isZero();
        assertThat(saved.getTitle()).isEqualTo("AI meetup");
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.registrationOpen()).isFalse();
    }

    // ---------- 归属校验 ----------

    @Test
    void updateOfMissingActivityReturnsNotFound() {
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.updateActivity(99L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("activity not found: 99");

        then(activityRepository).should(never()).save(any());
    }

    @Test
    void updateByAnotherOrganizerIsRejectedAndWritesNothing() {
        Activity otherPeoplesActivity = activityOwnedBy(999L, ActivityStatus.DRAFT);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(otherPeoplesActivity));

        assertThatThrownBy(() -> activityService.updateActivity(1L, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(403));

        then(activityRepository).should(never()).save(any());
        assertThat(otherPeoplesActivity.getTitle()).isEqualTo("AI meetup");
    }

    @Test
    void updateAppliesFieldsForOwnerOnDraft() {
        Activity draft = activityOwnedBy(ORGANIZER_ID, ActivityStatus.DRAFT);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(draft));
        given(activityRepository.save(any(Activity.class))).willAnswer(invocation -> invocation.getArgument(0));
        CreateActivityRequest changed = new CreateActivityRequest(
                "Updated title", "new description", "New hall", 80,
                NOW.plusDays(2), NOW.plusDays(4));

        ActivityResponse result = activityService.updateActivity(1L, changed);

        assertThat(result.title()).isEqualTo("Updated title");
        assertThat(result.location()).isEqualTo("New hall");
        assertThat(result.capacity()).isEqualTo(80);
    }

    // ---------- 发布与取消 ----------

    @Test
    void publishMovesDraftToPublished() {
        Activity draft = activityOwnedBy(ORGANIZER_ID, ActivityStatus.DRAFT);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(draft));
        given(activityRepository.save(any(Activity.class))).willAnswer(invocation -> invocation.getArgument(0));

        ActivityResponse result = activityService.publishActivity(1L);

        assertThat(result.status()).isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    void cancelMovesPublishedActivityToCancelled() {
        Activity published = activityOwnedBy(ORGANIZER_ID, ActivityStatus.PUBLISHED);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(published));
        given(activityRepository.save(any(Activity.class))).willAnswer(invocation -> invocation.getArgument(0));

        ActivityResponse result = activityService.cancelActivity(1L);

        assertThat(result.status()).isEqualTo(ActivityStatus.CANCELLED);
    }

    // ---------- 我的活动 ----------

    @Test
    void myActivitiesAreRejectedForParticipant() {
        given(currentUserProvider.require()).willReturn(PARTICIPANT);

        assertThatThrownBy(() -> activityService.listMyUnpublishedActivities())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(403));

        then(activityRepository).shouldHaveNoInteractions();
    }

    @Test
    void myActivitiesReturnsOwnNonPublishedOnes() {
        Activity draft = activityOwnedBy(ORGANIZER_ID, ActivityStatus.DRAFT);
        Activity cancelled = activityOwnedBy(ORGANIZER_ID, ActivityStatus.CANCELLED);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findByOrganizerIdAndStatusNotOrderByCreatedAtDesc(ORGANIZER_ID, ActivityStatus.PUBLISHED))
                .willReturn(List.of(draft, cancelled));

        List<ActivityResponse> result = activityService.listMyUnpublishedActivities();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ActivityResponse::status)
                .containsExactly(ActivityStatus.DRAFT, ActivityStatus.CANCELLED);
    }

    // ---------- BR-11 与状态机 ----------

    @Test
    void publishedActivityCannotChangeCapacity() {
        Activity published = activityOwnedBy(ORGANIZER_ID, ActivityStatus.PUBLISHED);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(published));
        CreateActivityRequest resized = new CreateActivityRequest(
                published.getTitle(), published.getDescription(), published.getLocation(), 5,
                published.getRegistrationStart(), published.getRegistrationEnd());

        assertThatThrownBy(() -> activityService.updateActivity(1L, resized))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void draftActivityCanChangeCapacityBeforePublishing() {
        Activity draft = activityOwnedBy(ORGANIZER_ID, ActivityStatus.DRAFT);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(draft));
        given(activityRepository.save(any(Activity.class))).willAnswer(invocation -> invocation.getArgument(0));
        CreateActivityRequest resized = new CreateActivityRequest(
                "AI meetup", "desc", "Main hall", 80, NOW.plusDays(1), NOW.plusDays(3));

        ActivityResponse result = activityService.updateActivity(1L, resized);

        assertThat(result.capacity()).isEqualTo(80);
        assertThat(result.registrationEnd()).isEqualTo(NOW.plusDays(3));
    }

    @Test
    void cancelledActivityCannotBePublishedAgain() {
        Activity cancelled = activityOwnedBy(ORGANIZER_ID, ActivityStatus.CANCELLED);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> activityService.publishActivity(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finishedActivityCannotBeCancelled() {
        Activity finished = activityOwnedBy(ORGANIZER_ID, ActivityStatus.FINISHED);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(finished));

        assertThatThrownBy(() -> activityService.cancelActivity(1L))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- FR-03 详情可见性 ----------

    @Test
    void draftDetailIsHiddenFromOtherUsers() {
        Activity someoneElsesDraft = activityOwnedBy(999L, ActivityStatus.DRAFT);
        given(currentUserProvider.require()).willReturn(ORGANIZER);
        given(activityRepository.findById(1L)).willReturn(Optional.of(someoneElsesDraft));

        assertThatThrownBy(() -> activityService.getActivity(1L))
                .isInstanceOf(BusinessException.class);
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

    private Activity activityOwnedBy(Long organizerId, ActivityStatus status) {
        Activity activity = activity(100, 0, NOW.minusDays(1), NOW.plusDays(1));
        activity.setOrganizerId(organizerId);
        activity.setStatus(status);
        return activity;
    }

    private CreateActivityRequest request() {
        return new CreateActivityRequest("AI meetup", "desc", "Main hall", 100,
                NOW.plusDays(1), NOW.plusDays(3));
    }
}
