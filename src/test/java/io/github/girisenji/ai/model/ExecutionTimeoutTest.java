package io.github.girisenji.ai.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ExecutionTimeout}.
 */
class ExecutionTimeoutTest {

    @Test
    void shouldParseValidIso8601Duration() {
        // When
        ExecutionTimeout timeout = ExecutionTimeout.parse("PT30S");

        // Then
        assertThat(timeout.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(timeout.toMillis()).isEqualTo(30_000);
    }

    @Test
    void shouldParseShortDurations() {
        // Given/When
        ExecutionTimeout oneSecond = ExecutionTimeout.parse("PT1S");
        ExecutionTimeout fiveSeconds = ExecutionTimeout.parse("PT5S");

        // Then
        assertThat(oneSecond.toMillis()).isEqualTo(1_000);
        assertThat(fiveSeconds.toMillis()).isEqualTo(5_000);
    }

    @Test
    void shouldParseLongDurations() {
        // Given/When
        ExecutionTimeout oneMinute = ExecutionTimeout.parse("PT1M");
        ExecutionTimeout oneHour = ExecutionTimeout.parse("PT1H");
        ExecutionTimeout twentyFourHours = ExecutionTimeout.parse("PT24H");

        // Then
        assertThat(oneMinute.toMillis()).isEqualTo(60_000);
        assertThat(oneHour.toMillis()).isEqualTo(3_600_000);
        assertThat(twentyFourHours.toMillis()).isEqualTo(86_400_000);
    }

    @Test
    void shouldParseComplexDurations() {
        // Given/When
        ExecutionTimeout complex1 = ExecutionTimeout.parse("PT1M30S"); // 1 minute 30 seconds
        ExecutionTimeout complex2 = ExecutionTimeout.parse("PT2H30M"); // 2 hours 30 minutes
        ExecutionTimeout complex3 = ExecutionTimeout.parse("PT1H15M30S"); // 1 hour 15 min 30 sec

        // Then
        assertThat(complex1.toMillis()).isEqualTo(90_000); // 90 seconds
        assertThat(complex2.toMillis()).isEqualTo(9_000_000); // 150 minutes
        assertThat(complex3.toMillis()).isEqualTo(4_530_000); // 4530 seconds
    }

    @Test
    void shouldRejectNullDuration() {
        // When/Then
        assertThatThrownBy(() -> ExecutionTimeout.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution timeout must not be null or blank");
    }

    @Test
    void shouldRejectBlankDuration() {
        // When/Then
        assertThatThrownBy(() -> ExecutionTimeout.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution timeout must not be null or blank");

        assertThatThrownBy(() -> ExecutionTimeout.parse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution timeout must not be null or blank");
    }

    @Test
    void shouldRejectInvalidDurationFormat() {
        // When/Then
        assertThatThrownBy(() -> ExecutionTimeout.parse("30s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ISO-8601 duration format");

        assertThatThrownBy(() -> ExecutionTimeout.parse("30 seconds"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ISO-8601 duration format");

        assertThatThrownBy(() -> ExecutionTimeout.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ISO-8601 duration format");
    }

    @Test
    void shouldRejectNegativeDuration() {
        // When/Then - Duration.parse() accepts PT-5S but validation rejects it
        assertThatThrownBy(() -> ExecutionTimeout.parse("PT-5S"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution timeout must be a positive duration");
    }

    @Test
    void shouldRejectZeroDuration() {
        // When/Then
        assertThatThrownBy(() -> ExecutionTimeout.parse("PT0S"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution timeout must be a positive duration");
    }

    @Test
    void shouldConvertToMillisecondsCorrectly() {
        // Given
        ExecutionTimeout timeout1 = ExecutionTimeout.parse("PT5S");
        ExecutionTimeout timeout2 = ExecutionTimeout.parse("PT30S");
        ExecutionTimeout timeout3 = ExecutionTimeout.parse("PT5M");

        // When/Then
        assertThat(timeout1.toMillis()).isEqualTo(5_000);
        assertThat(timeout2.toMillis()).isEqualTo(30_000);
        assertThat(timeout3.toMillis()).isEqualTo(300_000);
    }

    @Test
    void shouldHandleConstructorValidation() {
        // Given
        Duration validTimeout = Duration.ofSeconds(30);

        // When
        ExecutionTimeout timeout = new ExecutionTimeout(validTimeout);

        // Then
        assertThat(timeout.timeout()).isEqualTo(validTimeout);
        assertThat(timeout.toMillis()).isEqualTo(30_000);
    }

    @Test
    void shouldRejectConstructorWithZeroDuration() {
        // When/Then
        assertThatThrownBy(() -> new ExecutionTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution timeout must be a positive duration");
    }

    @Test
    void shouldPreserveOriginalDurationString() {
        // Given
        String originalDuration = "PT1M30S";

        // When
        ExecutionTimeout timeout = ExecutionTimeout.parse(originalDuration);

        // Then - Duration is preserved as Duration object
        assertThat(timeout.timeout()).isEqualTo(Duration.parse(originalDuration));
    }

    @Test
    void shouldHandleMillisecondPrecision() {
        // Given/When
        ExecutionTimeout timeout = ExecutionTimeout.parse("PT0.5S"); // 500ms

        // Then
        assertThat(timeout.toMillis()).isEqualTo(500);
    }

    @Test
    void shouldHandleVeryLongDurations() {
        // Given/When - 10 hours
        ExecutionTimeout timeout = ExecutionTimeout.parse("PT10H");

        // Then
        assertThat(timeout.toMillis()).isEqualTo(36_000_000);
    }
}
