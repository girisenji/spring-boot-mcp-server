package io.github.girisenji.ai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SizeLimit}.
 */
class SizeLimitTest {

    // ========== Valid Parsing Tests ==========

    @Test
    void shouldParseDefaultSizes() {
        SizeLimit limit = SizeLimit.parse(null, null);

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(10 * 1024 * 1024); // 10MB
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(10 * 1024 * 1024);
    }

    @Test
    void shouldParseMegabyteFormat() {
        SizeLimit limit = SizeLimit.parse("5MB", "15MB");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(5 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(15 * 1024 * 1024);
    }

    @Test
    void shouldParseGigabyteFormat() {
        SizeLimit limit = SizeLimit.parse("2GB", "3GB");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(3L * 1024 * 1024 * 1024);
    }

    @Test
    void shouldParseKilobyteFormat() {
        SizeLimit limit = SizeLimit.parse("512KB", "1024KB");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(512 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(1024 * 1024);
    }

    @Test
    void shouldParseByteFormat() {
        SizeLimit limit = SizeLimit.parse("1024", "2048");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(2048);
    }

    @Test
    void shouldParseMixedFormats() {
        SizeLimit limit = SizeLimit.parse("1GB", "512KB");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(1024 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(512 * 1024);
    }

    @Test
    void shouldParseCaseInsensitive() {
        SizeLimit limit = SizeLimit.parse("10mb", "20Mb");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(20 * 1024 * 1024);
    }

    @Test
    void shouldParseWithWhitespace() {
        SizeLimit limit = SizeLimit.parse("  10MB  ", "  20MB  ");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(20 * 1024 * 1024);
    }

    // ========== Invalid Format Tests ==========

    @Test
    void shouldRejectInvalidRequestSizeFormat() {
        assertThatThrownBy(() -> SizeLimit.parse("invalid", "10MB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid size format");
    }

    @Test
    void shouldRejectInvalidResponseSizeFormat() {
        assertThatThrownBy(() -> SizeLimit.parse("10MB", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid size format");
    }

    @Test
    void shouldRejectInvalidSuffix() {
        assertThatThrownBy(() -> SizeLimit.parse("10TB", "10MB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid size format");
    }

    @Test
    void shouldRejectNegativeRequestSize() {
        assertThatThrownBy(() -> SizeLimit.parse("-10MB", "10MB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldRejectNegativeResponseSize() {
        assertThatThrownBy(() -> SizeLimit.parse("10MB", "-10MB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldRejectZeroRequestSize() {
        assertThatThrownBy(() -> SizeLimit.parse("0MB", "10MB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldRejectZeroResponseSize() {
        assertThatThrownBy(() -> SizeLimit.parse("10MB", "0MB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // ========== Edge Cases ==========

    @Test
    void shouldHandleOnlyRequestSizeSpecified() {
        SizeLimit limit = SizeLimit.parse("20MB", null);

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(20 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(10 * 1024 * 1024); // Default
    }

    @Test
    void shouldHandleOnlyResponseSizeSpecified() {
        SizeLimit limit = SizeLimit.parse(null, "30MB");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(10 * 1024 * 1024); // Default
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(30 * 1024 * 1024);
    }

    @Test
    void shouldHandleBlankRequestSize() {
        SizeLimit limit = SizeLimit.parse("", "15MB");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(10 * 1024 * 1024); // Default
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(15 * 1024 * 1024);
    }

    @Test
    void shouldHandleBlankResponseSize() {
        SizeLimit limit = SizeLimit.parse("15MB", "");

        assertThat(limit.maxRequestBodyBytes()).isEqualTo(15 * 1024 * 1024);
        assertThat(limit.maxResponseBodyBytes()).isEqualTo(10 * 1024 * 1024); // Default
    }

    // ========== Format Bytes Tests ==========

    @Test
    void shouldFormatBytes() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(1024)).isEqualTo("1.00 KB");
    }

    @Test
    void shouldFormatKilobytes() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(512 * 1024)).isEqualTo("512.00 KB");
    }

    @Test
    void shouldFormatMegabytes() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(10 * 1024 * 1024)).isEqualTo("10.00 MB");
    }

    @Test
    void shouldFormatGigabytes() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(2L * 1024 * 1024 * 1024)).isEqualTo("2.00 GB");
    }

    @Test
    void shouldFormatZeroBytes() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(0)).isEqualTo("0 bytes");
    }

    @Test
    void shouldFormatSingleByte() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(1)).isEqualTo("1 bytes");
    }

    @Test
    void shouldFormatSmallByteCount() {
        SizeLimit limit = SizeLimit.parse("10MB", "10MB");

        assertThat(limit.formatBytes(512)).isEqualTo("512 bytes");
    }
}
