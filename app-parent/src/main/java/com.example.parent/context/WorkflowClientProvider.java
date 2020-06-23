package com.example.parent.context;

import com.uber.cadence.client.WorkflowClient;

public interface WorkflowClientProvider {

    WorkflowClient getWorkflowClient();
    void setWorkflowClient(WorkflowClient client);

}
