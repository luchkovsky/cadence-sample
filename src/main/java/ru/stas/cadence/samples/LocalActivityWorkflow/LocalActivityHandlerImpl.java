package ru.stas.cadence.samples.LocalActivityWorkflow;

import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Functions;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import ru.stas.cadence.samples.LocalActivityWorkflow.utils.ActivityTimeoutException;

public class LocalActivityHandlerImpl implements LocalActivityHandler {

    @Override
    public void executeLocalActivity(Functions.Func activity, Integer time, TimeUnit tu) {
        try {
            Async.function(activity).get(time, tu);
        } catch (TimeoutException e) {
            throw new ActivityTimeoutException(new RuntimeException("BOOM"));
        }
    }
}
