package dev.itsdropper.vantaloader.api;

/**
 * Standard event ordering bands. Lower values execute first.
 * Numeric priorities remain available for fine-grained ordering.
 */
public enum EventPriority {
    HIGHEST(-1_000),
    HIGH(-100),
    NORMAL(0),
    LOW(100),
    LOWEST(1_000);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
