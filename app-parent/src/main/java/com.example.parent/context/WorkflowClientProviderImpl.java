package com.example.parent.context;

import com.uber.cadence.ClusterInfo;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.Worker.Factory;
import com.uber.cadence.workflow.Functions;
import com.uber.tchannel.api.errors.TChannelError;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WorkflowClientProviderImpl implements WorkflowClientProvider {

    private WorkflowClient workflowClient;

    public WorkflowClientProviderImpl(Worker.Factory factory, String domain) {

        IWorkflowService service = factory.getWorkflowService();
        if (service instanceof WorkflowServiceTimeoutStoredChannel) {
            CallBackOnError callBackOnError = new CallBackOnError(factory, domain, this);
            ((WorkflowServiceTimeoutStoredChannel) service).setCallbackOnError(callBackOnError);
            workflowClient = WorkflowClient.newInstance(service, domain);
        }
    }

    private static class CallBackOnError implements Functions.Func1<TChannelError, Void> {

        private final Worker.Factory factory;
        private final String domain;
        private final WorkflowClientProvider provider;
        private final int sleepSecond = 2;

        public CallBackOnError(Factory factory, String domain, WorkflowClientProvider provider) {
            this.factory = factory;
            this.domain = domain;
            this.provider = provider;
        }

        @Override
        public Void apply(TChannelError e) {
            factory.suspendPolling();
            if (provider.getWorkflowClient() != null) {
                provider.getWorkflowClient().close();
            }

            ExecutorService executor = Executors.newFixedThreadPool(1);
            executor.submit(new Runnable() {

                @Override
                public void run() {
                    while (true) {
                        try {
                            IWorkflowService service = factory.getWorkflowService();
                            ClusterInfo clusterInfo = service.GetClusterInfo();
                            factory.resumePolling();
                            provider.setWorkflowClient(WorkflowClient.newInstance(service, domain));
                            executor.shutdown();
                            break;
                        } catch (Exception ex) {
                            //ok
                        }

                        try {
                            TimeUnit.SECONDS.sleep(sleepSecond);
                        } catch (InterruptedException interruptedException) {
                            //ok
                        }
                    }
                }
            });
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
