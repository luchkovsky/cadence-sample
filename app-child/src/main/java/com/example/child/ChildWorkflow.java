/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.example.child;

import static com.example.parent.SampleConstants.DOMAIN;
import static com.example.parent.SampleConstants.POLL_THREAD_COUNT;
import static com.example.parent.SampleConstants.getTaskListChild;

import com.example.child.ChildWorkflow.GreetingActivities;
import com.example.parent.SampleConstants;
import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.internal.worker.PollerOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.Worker.FactoryOptions;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.worker.WorkerOptions.Builder;
import com.uber.cadence.workflow.ActivityException;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Saga;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChildWorkflow implements ApplicationRunner {

  @Override
  public void run(ApplicationArguments args) {

    PollerOptions pollerOptions =
        new PollerOptions.Builder().setPollThreadCount(POLL_THREAD_COUNT).build();

    FactoryOptions factoryOptions =
        new FactoryOptions.Builder()
            .setStickyWorkflowPollerOptions(pollerOptions)
            .build();

    Worker.Factory factory = new Worker.Factory("127.0.0.1", 7933, DOMAIN, factoryOptions);

    String taskList = SampleConstants.getTaskListChild();
    WorkerOptions workerOptions =
        new Builder()
            .setActivityPollerOptions(pollerOptions)
            .setWorkflowPollerOptions(pollerOptions)
            .build();

    Worker workerChild = factory.newWorker(taskList, workerOptions);
    workerChild.registerActivitiesImplementations( new GreetingActivitiesImpl() );
    factory.start();
  }


  /** The child workflow interface. */
  public interface GreetingChild {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
    public String composeGreeting(String greeting, String name);
  }


  /** Activity interface is just to call external service and doNotCompleteActivity. */
  public interface GreetingActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public String composeGreeting(String greeting, String name);
  }


  public static class GreetingChildImpl implements GreetingChild {

    public String composeGreeting(String greeting, String name) {
      ActivityOptions ao =
          new ActivityOptions.Builder()
              .setTaskList(getTaskListChild())
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .build();

      GreetingActivities stub =
          Workflow.newActivityStub(GreetingActivities.class, ao);

      Promise<String> function = Async.function(stub::composeGreeting, greeting, name);
      return function.get();
    }

  }

  public static class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public String composeGreeting(String greeting, String name) {

      return greeting + name;
    }
  }


}
