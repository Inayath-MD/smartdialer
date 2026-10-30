package com.smartdialer.enums;

public enum CallStatus {
    QUEUED,
    RESERVED,
    INITIATED,
    RINGING,
    ANSWERED,
    CONNECTED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
