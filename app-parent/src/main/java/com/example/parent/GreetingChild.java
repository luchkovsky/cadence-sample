package com.example.parent;

import com.uber.cadence.workflow.WorkflowMethod;

public interface GreetingChild {

  @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
  String composeGreeting(String greeting, String name);
}
