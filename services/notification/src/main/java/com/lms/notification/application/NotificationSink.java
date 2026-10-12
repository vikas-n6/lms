package com.lms.notification.application;

/** Where a formatted notification goes (the SMS simulator in Phase 1; real channels in Phase 4). */
public interface NotificationSink {
    void deliver(String line);
}
