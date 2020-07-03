package ru.stas.cadence.samples.LocalActivityWorkflow;

import com.uber.cadence.activity.ActivityMethod;

public interface GreetingActivities {

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 8)
    String composeGreeting(String greeting);
}