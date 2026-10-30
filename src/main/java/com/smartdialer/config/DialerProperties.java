package com.smartdialer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "smartdialer")
public class DialerProperties {

    private int heartbeatTimeoutSeconds = 45;
    private int staleReservationTimeoutSeconds = 30;
    private int inFlightTimeoutSeconds = 60;
    private int defaultCooldownSeconds = 300;
    private int defaultMaxAttempts = 3;
    private boolean schedulerEnabled = true;

    public int getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public int getStaleReservationTimeoutSeconds() {
        return staleReservationTimeoutSeconds;
    }

    public void setStaleReservationTimeoutSeconds(int staleReservationTimeoutSeconds) {
        this.staleReservationTimeoutSeconds = staleReservationTimeoutSeconds;
    }

    public int getInFlightTimeoutSeconds() {
        return inFlightTimeoutSeconds;
    }

    public void setInFlightTimeoutSeconds(int inFlightTimeoutSeconds) {
        this.inFlightTimeoutSeconds = inFlightTimeoutSeconds;
    }

    public int getDefaultCooldownSeconds() {
        return defaultCooldownSeconds;
    }

    public void setDefaultCooldownSeconds(int defaultCooldownSeconds) {
        this.defaultCooldownSeconds = defaultCooldownSeconds;
    }

    public int getDefaultMaxAttempts() {
        return defaultMaxAttempts;
    }

    public void setDefaultMaxAttempts(int defaultMaxAttempts) {
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }
}
