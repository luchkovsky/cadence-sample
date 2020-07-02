package com.example.parent.context;

import com.uber.cadence.ClusterInfo;
import com.uber.cadence.ListOpenWorkflowExecutionsRequest;
import com.uber.cadence.ListOpenWorkflowExecutionsResponse;
import com.uber.cadence.StartTimeFilter;
import com.uber.cadence.WorkflowTypeFilter;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.Worker.Factory;
import com.uber.cadence.workflow.Functions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TException;

public class WorkflowClientProviderImpl implements WorkflowClientProvider {

  private WorkflowClient workflowClient;

  public WorkflowClientProviderImpl(Worker.Factory factory, String domain) {

    IWorkflowService service = factory.getWorkflowService();
    if (service instanceof WorkflowServiceTimeoutStoredChannel) {
      CallBackOnError callBackOnError = new CallBackOnError(factory, domain, this);
      ((WorkflowServiceTimeoutStoredChannel) service).setCallbackOnError(callBackOnError);
      workflowClient = WorkflowClient.newInstance(service, domain);

      try {
        testRequest(factory, domain);
      } catch (TException e) {
        e.printStackTrace();
      }
    }
  }

  private void testRequest(Factory factory, String domain) throws TException {

    IWorkflowService service = factory.getWorkflowService();

    ListOpenWorkflowExecutionsRequest request = new ListOpenWorkflowExecutionsRequest();
    request.setDomain(domain);
    //    WorkflowExecutionFilter filter = new WorkflowExecutionFilter();
    //    request.setExecutionFilter(filter);
    WorkflowTypeFilter typeFilter = new WorkflowTypeFilter();
    typeFilter.setName("GreetingWorkflow");
    request.setTypeFilter(typeFilter);
    StartTimeFilter startTimeFilter = new StartTimeFilter();
    startTimeFilter.setEarliestTime(System.currentTimeMillis() - 1000 * 60 * 10);
    startTimeFilter.setLatestTime(System.currentTimeMillis() + 1000 * 60 * 10);
    request.setStartTimeFilter(startTimeFilter);
    ListOpenWorkflowExecutionsResponse response = service.ListOpenWorkflowExecutions(request);
    System.out.println();
  }

  private static class CallBackOnError implements Functions.Func1<TException, Void> {

    private final Worker.Factory factory;
    private final String domain;
    private boolean pollingSuspended;
    private final WorkflowClientProvider provider;
    private final int sleepSecond = 2;

    public CallBackOnError(Factory factory, String domain, WorkflowClientProvider provider) {
      this.factory = factory;
      this.domain = domain;
      this.provider = provider;
    }

    @Override
    public Void apply(TException e) {
      if (!pollingSuspended) {
        factory.suspendPolling();
        pollingSuspended = true;

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(
            new Runnable() {

              @Override
              public void run() {
                while (true) {
                  try {
                    IWorkflowService service = factory.getWorkflowService();
                    ClusterInfo clusterInfo = service.GetClusterInfo();

                    factory.resumePolling();
                    pollingSuspended = false;
                    provider.setWorkflowClient(WorkflowClient.newInstance(service, domain));
                    executor.shutdown();
                    break;
                  } catch (Exception ex) {
                    // ok
                  }

                  try {
                    TimeUnit.SECONDS.sleep(sleepSecond);
                  } catch (InterruptedException ie) {
                    // ok
                  }
                }
              }
            });
      }
      return null;
    }
  }

  @Override
  public void setWorkflowClient(WorkflowClient workflowClient) {
    this.workflowClient = workflowClient;
  }

  @Override
  public WorkflowClient getWorkflowClient() {
    return workflowClient;
  }
}
