package com.seatflow.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the registration window rule and for the business rule that
 * decides whether registration is possible right now.
 *
 * <p>中文批注：测试分两组。第一组钉住纯时间窗语义 isWithinRegistrationWindow 的六个位置
 * （开始前、正好开始、窗口内、结束前、正好结束、结束后），左闭右开；
 * 第二组钉住业务判断 isOpenForRegistration：已发布、在窗口内、有余票，三个条件缺一不可。
 * 之所以拆成两组：如果只测合并后的业务方法，"正好开始""正好结束"这些边界会被状态与余票条件掩盖，
 * 窗口语义将来被改错也不会有人发现。
 * setUp 默认构造一个"已发布、容量 100、无人报名、窗口 START 到 END"的活动，
 * 每个用例只改它关心的那一个条件，这样测试失败时原因是唯一的。
 * 不需要 Spring、不需要数据库、也不需要 mock，因为时间是被测方法的入参——测试自己扮演了时钟。
 * 类不写 public 是 JUnit 5 的惯例（要求 public 的是 JUnit 4）。
 */
class ActivityTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 5, 18, 0);

    private Activity activity;

    @BeforeEach
    void setUp() {
        activity = new Activity();
        activity.setRegistrationStart(START);
        activity.setRegistrationEnd(END);
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setCapacity(100);
        activity.setRegisteredCount(0);
    }

    // ---------- 第一组：纯时间窗，左闭右开 ----------

    @Test
    void closedBeforeWindowStarts() {
        assertThat(activity.isWithinRegistrationWindow(START.minusSeconds(1))).isFalse();
    }

    @Test
    void openExactlyAtStart() {                      // 左闭
        assertThat(activity.isWithinRegistrationWindow(START)).isTrue();
    }

    @Test
    void openInsideWindow() {
        assertThat(activity.isWithinRegistrationWindow(START.plusDays(1))).isTrue();
    }

    @Test
    void stillOpenJustBeforeEnd() {
        assertThat(activity.isWithinRegistrationWindow(END.minusSeconds(1))).isTrue();
    }

    @Test
    void closedExactlyAtEnd() {                      // 右开
        assertThat(activity.isWithinRegistrationWindow(END)).isFalse();
    }

    @Test
    void closedAfterWindowEnds() {
        assertThat(activity.isWithinRegistrationWindow(END.plusSeconds(1))).isFalse();
    }

    // ---------- 第二组：能否报名 = 已发布 + 在窗口内 + 有余票 ----------

    @Test
    void openForRegistrationWhenPublishedInsideWindowWithSeats() {
        assertThat(activity.isOpenForRegistration(START.plusDays(1))).isTrue();
    }

    @Test
    void closedForRegistrationWhenDraft() {
        activity.setStatus(ActivityStatus.DRAFT);

        assertThat(activity.isOpenForRegistration(START.plusDays(1))).isFalse();
    }

    @Test
    void closedForRegistrationWhenCancelled() {
        activity.setStatus(ActivityStatus.CANCELLED);

        assertThat(activity.isOpenForRegistration(START.plusDays(1))).isFalse();
    }

    @Test
    void closedForRegistrationWhenFinished() {
        activity.setStatus(ActivityStatus.FINISHED);

        assertThat(activity.isOpenForRegistration(START.plusDays(1))).isFalse();
    }

    @Test
    void closedForRegistrationWhenFullAlthoughWindowIsOpen() {
        activity.setRegisteredCount(100);

        assertThat(activity.isWithinRegistrationWindow(START.plusDays(1))).isTrue();
        assertThat(activity.isOpenForRegistration(START.plusDays(1))).isFalse();
    }

    @Test
    void closedForRegistrationBeforeWindowStarts() {
        assertThat(activity.isOpenForRegistration(START.minusSeconds(1))).isFalse();
    }

    @Test
    void closedForRegistrationAfterWindowEnds() {
        assertThat(activity.isOpenForRegistration(END)).isFalse();
    }
}
