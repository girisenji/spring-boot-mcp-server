package io.github.girisenji.ai.model;

/**
 * Configuration for request/response size limits to prevent memory exhaustion.
 * 
 * @param maxRequestBodyBytes  Maximum allowed request body size in bytes
 * @param maxResponseBodyBytes Maximum allowed response body size in bytes
 */
public record SizeLimit(long maxRequestBodyBytes, long maxResponseBodyBytes) {

    /**
     * Default maximum request body size (10 MB).
     */
    public static final long DEFAULT_MAX_REQUEST_BYTES = 10 * 1024 * 1024;

    /**
     * Default maximum response body size (10 MB).
     */
    public static final long DEFAULT_MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

    public SizeLimit {
        if (maxRequestBodyBytes <= 0) {
            throw new IllegalArgumentException("Max request body size must be positive");
        }
        if (maxResponseBodyBytes <= 0) {
            throw new IllegalArgumentException("Max response body size must be positive");
        }
    }

    /**
     * Parse size limit from human-readable format.
     * 
     * @param maxRequestSize  Maximum request size (e.g., "10MB", "1GB", "512KB")
     *                        or null for default
     * @param maxResponseSize Maximum response size (e.g., "10MB", "1GB", "512KB")
     *                        or null for default
     * @return parsed SizeLimit
     * @throws IllegalArgumentException if size format is invalid
     */
    public static SizeLimit parse(String maxRequestSize, String maxResponseSize) {
        long requestBytes = parseSizeToBytes(maxRequestSize, "request", DEFAULT_MAX_REQUEST_BYTES);
        long responseBytes = parseSizeToBytes(maxResponseSize, "response", DEFAULT_MAX_RESPONSE_BYTES);
        return new SizeLimit(requestBytes, responseBytes);
    }

    private static long parseSizeToBytes(String size, String context, long defaultValue) {
        if (size == null || size.isBlank()) {
            return defaultValue;
        }

        String upperSize = size.trim().toUpperCase();

        try {
            // Handle suffixes: KB, MB, GB
            if (upperSize.endsWith("GB")) {
                long gb = Long.parseLong(upperSize.substring(0, upperSize.length() - 2).trim());
                return gb * 1024 * 1024 * 1024;
            } else if (upperSize.endsWith("MB")) {
                long mb = Long.parseLong(upperSize.substring(0, upperSize.length() - 2).trim());
                return mb * 1024 * 1024;
            } else if (upperSize.endsWith("KB")) {
                long kb = Long.parseLong(upperSize.substring(0, upperSize.length() - 2).trim());
                return kb * 1024;
            } else if (upperSize.endsWith("B")) {
                return Long.parseLong(upperSize.substring(0, upperSize.length() - 1).trim());
            } else {
                // Try to parse as raw bytes
                return Long.parseLong(upperSize);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid size format for " + context + ": '" + size +
                            "'. Examples: 10MB, 1GB, 512KB, 1048576",
                    e);
        }
    }

    /**
     * Format bytes as human-readable size.
     * 
     * @param bytes size in bytes
     * @return formatted string (e.g., "10.00 MB", "1.00 GB")
     */
    public String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return bytes + " bytes";
        }
    }
}
