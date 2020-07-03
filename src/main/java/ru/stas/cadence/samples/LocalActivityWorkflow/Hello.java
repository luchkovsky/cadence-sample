package ru.stas.cadence.samples.LocalActivityWorkflow;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.stas.cadence.samples.LocalActivityWorkflow.utils.TimeoutableProxy;


@Slf4j
@Component
public class Hello {

    /**
     * Hello World Cadence workflow that executes a single activity. Requires a local instance the Cadence service to be
     * running.
     */

    static final String TASK_LIST = "HelloActivity";
    private static final String DOMAIN = "sample";

    /**
     * Workflow interface has to have at least one method annotated with @WorkflowMethod.
     */
    public interface GreetingWorkflow {

        /**
         * @return greeting string
         */
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = 240000, taskList = TASK_LIST)
        String getGreeting(String name);
    }


    /**
     * GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting.
     */
    public static class GreetingWorkflowImpl implements Hello.GreetingWorkflow {

        /**
         * Activity stub implements activity interface and proxies calls to it to Cadence activity invocations. Because
         * activities are reentrant, only a single stub can be used for multiple activity invocations.
         */
        private GreetingActivities activities;

        @Override
        public String getGreeting(String name) {
   /* RetryOptions ro = new RetryOptions.Builder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumAttempts(100000)
          .setDoNotRetry(DoNotRetryOnTimeoutException.class)
          .build();
      LocalActivityOptions lao = new LocalActivityOptions.Builder()
          .setRetryOptions(ro)
          .build();*/
            activities =
                Workflow.newLocalActivityStub(GreetingActivities.class);


 /*     CompletablePromise<String> result = Workflow.newPromise();
      CancellationScope scope =
          Workflow.newCancellationScope(
              () -> {
                result.completeFrom(Async.function(activities::composeGreeting,"composeGreeting"));
              });
      scope.run();*/
            return  activities.composeGreeting("1");

        }
    }


    public static void main(String[] args) {
        // Start a worker that hosts both workflow and activity implementations.
        // Worker.FactoryOptions fo = new Worker.FactoryOptions.Builder().setMaxWorkflowThreadCount(10).setDisableStickyExecution(true).build();

        // Worker.Factory factory = new Worker.Factory(DOMAIN, fo);
        Worker.Factory factory = new Worker.Factory(DOMAIN);
        Worker worker = factory.newWorker(TASK_LIST);

        // Workflows are stateful. So you need a type to create instances.
        worker.registerWorkflowImplementationTypes(Hello.GreetingWorkflowImpl.class);
        // Activities are stateless and thread safe. So a shared instance is used.

        GreetingActivities proxy = TimeoutableProxy
            .getProxy(GreetingActivities.class, new GreetingActivitiesImpl(), 5, TimeUnit.SECONDS);

        worker.registerActivitiesImplementations(proxy);
        // Start listening to the workflow and activity task lists.
        factory.start();

        // Start a workflow execution. Usually this is done from another program.
        WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
        // Get a workflow stub using the same task list the worker uses.
        Hello.GreetingWorkflow workflow = workflowClient.newWorkflowStub(Hello.GreetingWorkflow.class);
        // Execute a workflow waiting for it to complete.
        String greeting = workflow.getGreeting("World");
        System.out.println(greeting);
        System.exit(0);
    }

}
