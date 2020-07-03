package ru.stas.cadence.samples.LocalActivityWorkflow;

import com.uber.cadence.workflow.Functions;
import java.util.concurrent.TimeUnit;

public interface LocalActivityHandler<S> {

    public void executeLocalActivity(Functions.Func<S> activity, Integer time, TimeUnit tu);
}
