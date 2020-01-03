package com.espressif.ui.models;

import com.espressif.AppConstants;

public class UpdateEvent {

    private AppConstants.UpdateEventType eventType;

    public UpdateEvent(AppConstants.UpdateEventType type) {
        eventType = type;
    }

    public AppConstants.UpdateEventType getEventType() {
        return eventType;
    }
}
