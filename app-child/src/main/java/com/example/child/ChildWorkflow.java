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
    registerDomain();
    startFactory();
  }

  private void startFactory() {
    // Start a worker that hosts both parent and child workflow implementations.
    Scope scope =
        new RootScopeBuilder()
            .reporter(new CustomCadenceClientStatsReporter())
            .reportEvery(com.uber.m3.util.Duration.ofSeconds(1));

    PollerOptions pollerOptions =
        new PollerOptions.Builder().setPollThreadCount(POLL_THREAD_COUNT).build();

    FactoryOptions factoryOptions =
        new FactoryOptions.Builder()
            .setStickyWorkflowPollerOptions(pollerOptions)
            .setMetricScope(scope)
            .build();

    Worker.Factory factory = new Worker.Factory("127.0.0.1", 7933, DOMAIN, factoryOptions);

    String taskList = SampleConstants.getTaskListChild();
    WorkerOptions workerOptions =
        new Builder()
            .setMetricsScope(scope)
            .setActivityPollerOptions(pollerOptions)
            .setWorkflowPollerOptions(pollerOptions)
            .build();

    Worker workerChild = factory.newWorker(taskList, workerOptions);

    workerChild.registerWorkflowImplementationTypes(
        GreetingChildImpl.class, CompensationChildImpl.class);

    workerChild.registerActivitiesImplementations(
        new GreetingActivitiesImpl(),
        new CompensationGreetingActivitiesImpl(),
        new OtherActivitiesImpl(),
        new CompensationOtherActivitiesImpl());

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

  /** The child workflow interface. */
  public interface GreetingChild {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
    public String composeGreeting(String greeting, String name);
  }

  /** The child workflow interface. */
  public interface CompensationGreetingChild {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
    public void compensate(String greeting, String name);
  }

  /** Activity interface is just to call external service and doNotCompleteActivity. */
  public interface GreetingActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public String composeGreeting(String greeting, String name);
  }

  public interface CompensationGreetingActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public void compensate(String greeting, String name);
  }

  public interface CompensationOtherActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public void compensate(String name);
  }

  public interface OtherActivities {

    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000)
    public void otherActivity(String name);
  }

  public static class CompensationChildImpl implements CompensationGreetingChild {

    public void compensate(String greeting, String name) {
      System.out.println("Compensation Child Flow" + greeting + name);
    }
  }

  public static class CompensationGreetingActivitiesImpl implements CompensationGreetingActivities {

    public void compensate(String greeting, String name) {
      System.out.println("Compensation Greeting Activity " + greeting + name);
    }
  }

  public static class CompensationOtherActivitiesImpl
      implements CompensationOtherActivities {

    public void compensate(String name) {
      System.out.println("Compensation After Greeting Activity " + name);
    }
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildImpl implements GreetingChild {

    public String composeGreeting(String greeting, String name) {
      Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());
      try {
        return doComposeGreeting(greeting, name, saga);
      } catch (ActivityException ex) {
        saga.compensate();
        throw ex;
      }
    }

    private String doComposeGreeting(String greeting, String name, Saga saga) {
      ActivityOptions ao =
          new ActivityOptions.Builder()
              .setTaskList(getTaskListChild())
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .build();

      GreetingActivities greetingActivities =
          Workflow.newActivityStub(GreetingActivities.class, ao);

      OtherActivities otherActivities =
          Workflow.newActivityStub(OtherActivities.class, ao);

      CompensationGreetingActivities greetingActivitiesCompensation =
          Workflow.newActivityStub(CompensationGreetingActivities.class, ao);

      CompensationOtherActivities otherActivityCompensation =
          Workflow.newActivityStub(CompensationOtherActivities.class, ao);

      otherActivities.otherActivity("Before");
      saga.addCompensation(otherActivityCompensation::compensate, "Before");

      String result = Async.function(greetingActivities::composeGreeting, greeting, name).get();
      saga.addCompensation(greetingActivitiesCompensation::compensate, greeting, name);

      otherActivities.otherActivity("After");
      saga.addCompensation(otherActivityCompensation::compensate, "After");

      return result;
    }

  }

  static class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public String composeGreeting(String greeting, String name) {
      byte[] taskToken = Activity.getTaskToken();
      sendRestRequest(taskToken);
      Activity.doNotCompleteOnReturn();

      return "Activity doNotCompleteOnReturn";
    }
  }

  static class OtherActivitiesImpl implements OtherActivities {

    @Override
    public void otherActivity(String name) {
      System.out.println("Other Activities is running " + name);
    }
  }

  private static void sendRestRequest(byte[] taskToken) {
    try {
      URL url = new URL("http://127.0.0.1:8090/api/cadence/async");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("PUT");
      connection.setRequestProperty("Content-Type", "application/octet-stream");

      OutputStream os = connection.getOutputStream();
      os.write(taskToken);
      os.flush();

      connection.getResponseCode();
      connection.disconnect();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
