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

package com.example.parent;

import static com.example.parent.SampleConstants.DOMAIN;
import static com.example.parent.SampleConstants.POLL_THREAD_COUNT;

import com.example.parent.context.WorkflowClientProviderImpl;
import com.example.parent.context.WorkflowServiceTimeoutStoredChannel;
import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.internal.worker.PollerOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.Worker.Factory;
import com.uber.cadence.worker.Worker.FactoryOptions;
import com.uber.cadence.worker.Worker.FactoryOptions.Builder;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.rmi.server.UID;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParentApplicationWorkflow implements ApplicationRunner {

  private WorkflowClientProviderImpl provider;

  @Override
  public void run(ApplicationArguments args) {
    registerDomain();
    startFactory();
    startFlow();
  }

  private void startFactory() {
    // Start a worker that hosts both parent and child workflow implementations.

    PollerOptions pollerOptions =
        new PollerOptions.Builder().setPollThreadCount(POLL_THREAD_COUNT).build();

    FactoryOptions factoryOptions =
        new Builder().setStickyWorkflowPollerOptions(pollerOptions).build();

    IWorkflowService service = new WorkflowServiceTChannel("127.0.0.1", 7933);
    Worker.Factory factory = new Factory(service, DOMAIN, factoryOptions);

    WorkerOptions workerOptions =
        new WorkerOptions.Builder()
            .setWorkflowPollerOptions(pollerOptions)
            .setActivityPollerOptions(pollerOptions)
            .build();

    Worker workerParent = factory.newWorker(SampleConstants.getTaskListParent(), workerOptions);

    workerParent.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    workerParent.registerActivitiesImplementations(new ParentActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();
  }

  private void registerDomain() {
    IWorkflowService cadenceService = new WorkflowServiceTChannel();
    RegisterDomainRequest request = new RegisterDomainRequest();
    request.setDescription("Java Samples");
    request.setEmitMetric(false);
    request.setName(DOMAIN);

    int retentionPeriodInDays = 1;
    request.setWorkflowExecutionRetentionPeriodInDays(retentionPeriodInDays);

    try {
      cadenceService.RegisterDomain(request);
      System.out.println(
          "Successfully registered domain \""
              + DOMAIN
              + "\" with retentionDays="
              + retentionPeriodInDays);

    } catch (DomainAlreadyExistsError e) {
      log.error("Domain \"" + DOMAIN + "\" is already registered");
    } catch (TException e) {
      log.error("Error occurred", e);
    }
  }

  private void startFlow() {

    IWorkflowService service = new WorkflowServiceTimeoutStoredChannel("127.0.0.1", 7933);
    WorkflowClient workflowClient = WorkflowClient.newInstance(service, DOMAIN);

    while (true) {
      try {
        doStartClient(workflowClient);
      } catch (Exception e) {
        log.error("Exception:", e);
      }
    }
  }

  private void doStartClient(WorkflowClient workflowClient) {

    for (int i = 0; i < 100; i++) {
      WorkflowOptions options =
          new WorkflowOptions.Builder()
              .setTaskList(SampleConstants.getTaskListParent())
              .setWorkflowId(new UID().toString())
              .build();

      GreetingWorkflow parentWorkflow =
          workflowClient.newWorkflowStub(GreetingWorkflow.class, options);
      WorkflowClient.start(parentWorkflow::getGreeting, "World");

      System.out.print(".");
    }

    System.out.println("+ 100 workflows");
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      log.error("Interrupted Exception", e);
    }
  }

  /** The parent workflow interface. */
  public interface GreetingWorkflow {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
    String getGreeting(String name);
  }

  public interface ParentActivities {

    @ActivityMethod
    public String composeParentGreeting();
  }

  static class ParentActivitiesImpl implements ParentActivities {

    @Override
    public String composeParentGreeting() {
      try {
        Thread.sleep(15000);
      } catch (InterruptedException e) {
        throw Activity.wrap(new RuntimeException("interrupted"));
      }
      return String.format(
          "Finished parent activity: activity id: [%s]", Activity.getTask().getActivityId());
    }
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    private static final Random random = new Random();

    @Override
    public String getGreeting(String name) {
      //      LocalActivityOptions ao =
      //          new LocalActivityOptions.Builder()
      //              .setScheduleToCloseTimeout(Duration.ofSeconds(10))
      //              .build();

      ParentActivities activity = Workflow.newLocalActivityStub(ParentActivities.class);
      if (random.nextBoolean()) {
        Async.function(activity::composeParentGreeting).get();
      } else {
        activity.composeParentGreeting();
      }

      return "OK";
    }
  }
}
