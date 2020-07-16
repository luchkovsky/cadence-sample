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

package com.example.parent.two;

import static com.example.parent.SampleConstants.DOMAIN;

import com.example.parent.GreetingChild;
import com.example.parent.SampleConstants;
import com.example.parent.context.ChildFlowProxy;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.ChildWorkflowOptions;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.rmi.server.UID;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
@Slf4j
@Component
public class ParentApplicationWorkflow implements ApplicationRunner {

  private static Worker.Factory factory;

  @Override
  public void run(ApplicationArguments args) {
    startFactory();
    startClient();
  }

  private void startFactory() {

    IWorkflowService service = new WorkflowServiceTChannel("127.0.0.1", 7933);
    factory = new Worker.Factory(service, DOMAIN);

    String taskList = SampleConstants.getTaskListParent(3);

    Worker workerParent = factory.newWorker(taskList);

    workerParent.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    workerParent.registerActivitiesImplementations(new ParentActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();
  }

  private void startClient() {
    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance("127.0.0.1", 7933, DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    // Execute a workflow waiting for it to complete.
    GreetingWorkflow parentWorkflow;

    while (true) {
      String taskList = SampleConstants.getTaskListParent();
      WorkflowOptions options =
          new WorkflowOptions.Builder()
              .setTaskList(taskList)
              .setWorkflowId(new UID().toString())
              .build();
      parentWorkflow = workflowClient.newWorkflowStub(GreetingWorkflow.class, options);
      WorkflowClient.start(parentWorkflow::getGreeting, "World");
      System.out.println("Start new workflow:" + options.getWorkflowId());
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        log.error("Error occurred", e);
      }
      break;
    }
    // System.exit(0);
  }

  /** The parent workflow interface. */
  public interface GreetingWorkflow {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
    List<String> getGreeting(String name);
  }

  public interface ParentActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public String composeParentGreeting();
  }

  static class ParentActivitiesImpl implements ParentActivities {

    @Override
    public String composeParentGreeting() {
      return String.format(
          "Finished parent activity: activity id: [%s], task: [%s]",
          Activity.getTask().getActivityId(), new String(Activity.getTaskToken()));
    }
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public List<String> getGreeting(String name) {

      return doGetGreeting(name);
    }

    private List<String> doGetGreeting(String name) {

      ActivityOptions ao =
          new ActivityOptions.Builder()
              .setTaskList(SampleConstants.getTaskListParent())
              .setStartToCloseTimeout(Duration.ofSeconds(300)) // 30 sec for Parent
              .build();

      ParentActivities activity = Workflow.newActivityStub(ParentActivities.class, ao);
      Async.function(activity::composeParentGreeting).get();

      ChildFlowProxy proxy = new ChildFlowProxy();
      for (int i = 0; i < 20; i++) {

        ChildWorkflowOptions options =
            new ChildWorkflowOptions.Builder()
                .setTaskList(SampleConstants.getTaskListChild())
                .setWorkflowId(new UID().toString())
                .build();

        GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class, options);

        // child = new ChildFlowProxy().getProxy(GreetingChild.class, child);

        Promise<String> hello = Async.function(child::composeGreeting, "Hello", name + " " + i);
        hello.get();
        System.out.println(proxy.getCallSize());
      }
      return proxy.getResults();
    }
  }
}
