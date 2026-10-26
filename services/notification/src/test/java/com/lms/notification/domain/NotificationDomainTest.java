package com.lms.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure notification logic (phase prompt task 5): template rendering and the quiet-hours window
 * (held overnight, released the next morning), evaluated in the business time zone.
 */
class NotificationDomainTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final QuietHours quietHours = new QuietHours(21, 8, IST);

    private static Instant ist(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), IST).toInstant();
    }

    @Test
    void rendersPlaceholdersAndBlanksUnknownOnes() {
        String rendered =
                TemplateRenderer.render(
                        "Hi {name}, EMI {amount} due {date}. {unknown}",
                        Map.of("name", "A", "amount", "4,740.00", "date", "2026-12-13"));
        assertThat(rendered).isEqualTo("Hi A, EMI 4,740.00 due 2026-12-13. ");
    }

    @Test
    void lateEveningIsQuietAndReleasesNextMorningAtEndHour() {
        Instant at = ist(2026, 11, 20, 22, 0);
        assertThat(quietHours.isQuiet(at)).isTrue();
        assertThat(quietHours.releaseAt(at)).isEqualTo(ist(2026, 11, 21, 8, 0));
    }

    @Test
    void afterMidnightIsQuietAndReleasesSameDayAtEndHour() {
        Instant at = ist(2026, 11, 21, 2, 0);
        assertThat(quietHours.isQuiet(at)).isTrue();
        assertThat(quietHours.releaseAt(at)).isEqualTo(ist(2026, 11, 21, 8, 0));
    }

    @Test
    void daytimeIsNotQuietAndReleasesImmediately() {
        Instant at = ist(2026, 11, 21, 10, 0);
        assertThat(quietHours.isQuiet(at)).isFalse();
        assertThat(quietHours.releaseAt(at)).isEqualTo(at);
    }

    @Test
    void boundariesAreInclusiveStartExclusiveEnd() {
        assertThat(quietHours.isQuiet(ist(2026, 11, 20, 21, 0))).isTrue(); // 21:00 quiet
        assertThat(quietHours.isQuiet(ist(2026, 11, 21, 7, 59))).isTrue(); // 07:59 quiet
        assertThat(quietHours.isQuiet(ist(2026, 11, 21, 8, 0))).isFalse(); // 08:00 open
    }
}
