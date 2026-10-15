package com.seatflow.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary tests for the registration window rule.
 *
 * <p>中文批注：报名窗口的边界测试。不需要 Spring、不需要数据库、也不需要 mock，
 * 因为被测方法 isRegistrationOpen(LocalDateTime) 把时间当作参数接收——测试自己扮演了时钟的角色。
 * 六个用例分别钉住"开始前、正好开始、窗口内、结束前、正好结束、结束后"六个位置；
 * 其中"正好开始"和"正好结束"这两个，只有在时间能作为输入传进来时才写得出来。
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
    }

    @Test
    void closedBeforeWindowStarts() {
        assertThat(activity.isRegistrationOpen(START.minusSeconds(1))).isFalse();
    }

    @Test
    void openExactlyAtStart() {                      // 左闭
        assertThat(activity.isRegistrationOpen(START)).isTrue();
    }

    @Test
    void openInsideWindow() {
        assertThat(activity.isRegistrationOpen(START.plusDays(1))).isTrue();
    }

    @Test
    void stillOpenJustBeforeEnd() {
        assertThat(activity.isRegistrationOpen(END.minusSeconds(1))).isTrue();
    }

    @Test
    void closedExactlyAtEnd() {                      // 右开
        assertThat(activity.isRegistrationOpen(END)).isFalse();
    }

    @Test
    void closedAfterWindowEnds() {
        assertThat(activity.isRegistrationOpen(END.plusSeconds(1))).isFalse();
    }
}
