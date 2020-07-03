package ru.stas.cadence.samples.LocalActivityWorkflow.utils;

import java.util.concurrent.TimeoutException;


public class ActivityTimeoutException extends RuntimeException {

    public ActivityTimeoutException(String message) {
        super(message);
    }

    public ActivityTimeoutException(TimeoutException e) {
        super(e);
    }

    public ActivityTimeoutException(RuntimeException e) {
        super(e);
    }

}
