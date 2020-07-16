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

package com.example.parent.one;

import static com.example.parent.SampleConstants.DOMAIN;

import com.example.parent.GreetingChild;
import com.example.parent.SampleConstants;
import com.example.parent.one.GreetingChildImpl.GreetingActivitiesImpl;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.worker.Worker;
import java.rmi.server.UID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChildWorkflow implements ApplicationRunner {

  @Override
  public void run(ApplicationArguments args) {
    Worker.Factory factory = new Worker.Factory("127.0.0.1", 7933, DOMAIN);

    String taskList = SampleConstants.getTaskListChild();
    Worker worker = factory.newWorker(taskList);
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    worker.registerWorkflowImplementationTypes(GreetingChildImpl.class);

    factory.start();

    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);

    WorkflowOptions options =
        new WorkflowOptions.Builder()
            .setTaskList(SampleConstants.getTaskListChild())
            .setWorkflowId(new UID().toString())
            .build();

    GreetingChild workflow = workflowClient.newWorkflowStub(GreetingChild.class, options);
    workflow.composeGreeting("Hello", " test!");
  }
}
