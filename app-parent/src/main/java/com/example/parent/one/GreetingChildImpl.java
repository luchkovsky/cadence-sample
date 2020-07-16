package com.example.parent.one;

import com.example.parent.GreetingChild;
import com.example.parent.SampleConstants;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import java.time.Duration;

public class GreetingChildImpl implements GreetingChild {

  public static interface GreetingActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public String composeGreeting(String greeting, String name);
  }

  public static class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public String composeGreeting(String greeting, String name) {
      System.out.println("Child workflow finished:" + greeting + name);
      return greeting + name;
    }
  }

  public String composeGreeting(String greeting, String name) {
    ActivityOptions options =
        new ActivityOptions.Builder()
            .setTaskList(SampleConstants.getTaskListChild())
            .setStartToCloseTimeout(Duration.ofSeconds(300))
            .build();

    GreetingActivities stub = Workflow.newActivityStub(GreetingActivities.class, options);
    Promise<String> function = Async.function(stub::composeGreeting, greeting, name);
    return function.get();
  }
}
