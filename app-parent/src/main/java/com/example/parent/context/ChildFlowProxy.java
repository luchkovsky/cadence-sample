package com.example.parent.context;

import static lombok.AccessLevel.PRIVATE;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.uber.cadence.internal.sync.WorkflowInternal;
import com.uber.cadence.internal.sync.WorkflowStubMarker;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.WorkflowMethod;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@AllArgsConstructor(access = PRIVATE)
public class ChildFlowProxy {

  private final Queue<Promise<?>> calls;
  private final Queue<WorkflowContext> results;
  private final ScheduledExecutorService executorService;

  private final long time;
  private final int blockSize;
  private final long wait;

  public ChildFlowProxy() {
    this(1, 100, 1000);
  }

  public ChildFlowProxy(long time, int blockSize, long wait) {
    this.time = time;
    this.blockSize = blockSize;
    this.wait = wait;

    calls = new ArrayBlockingQueue<Promise<?>>(blockSize);
    results = new ArrayBlockingQueue<WorkflowContext>(blockSize);
    executorService =
        Executors.newScheduledThreadPool(
            1,
            new ThreadFactoryBuilder()
                .setDaemon(false)
                .setNameFormat("child-workflow-batch-pool-%d")
                .build());
  }

  private void delayInvoke() {
    ((ScheduledThreadPoolExecutor) executorService).purge();
    executorService.schedule(
        () -> {
          executeBatch(blockSize);
          try {
            Thread.sleep(wait);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          if (!calls.isEmpty()) {
            delayInvoke();
          }
        },
        time,
        TimeUnit.MILLISECONDS);
  }

  private void executeBatch(int blockSize) {
    //    while (!calls.isEmpty() && results.size() < blockSize) {
    //      WorkflowContext context = calls.poll();
    //
    //      try {
    //        context.setResult(context.getMethod().invoke(context.getTarget(), context.getArgs()));
    //      } catch (Exception ex) {
    //        context.setException(ex);
    //      }
    //      System.out.println(context);
    //      results.add(context);
    //    }
  }

  @SuppressWarnings("unchecked")
  public <T> T getProxy(Class<?> workflowInterface, T instance) {
    return (T)
        Proxy.newProxyInstance(
            WorkflowInternal.class.getClassLoader(),
            new Class<?>[] {WorkflowStubMarker.class, workflowInterface},
            new InvocationHandler() {

              @Override
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                WorkflowMethod workflowMethod = method.getAnnotation(WorkflowMethod.class);
                if (WorkflowStubMarker.GET_EXECUTION_METHOD_NAME.equals(method.getName())) {
                  Promise<?> executionPromise =
                      ((WorkflowStubMarker) instance).__getWorkflowExecution();
                  calls.add(executionPromise);
                  return new DummyPromise(executionPromise);
                } else if (workflowMethod != null) {

                } else {
                  return method.invoke(instance, args);
                }

                // delayInvoke();

                return null;
              }
            });
  }

  public int getCallSize() {
    return calls.size();
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getResults() {
    //    while (!calls.isEmpty()) {
    //      while (!calls.isEmpty() && results.size() < blockSize) {
    //        WorkflowContext context = calls.poll();
    //
    //        try {
    //          context.setResult(context.getMethod().invoke(context.getTarget(),
    // context.getArgs()));
    //        } catch (Exception ex) {
    //          context.setException(ex);
    //        }
    //        results.add(context);
    //      }
    //      if (!calls.isEmpty()) {
    //        try {
    //          Thread.sleep(wait);
    //        } catch (InterruptedException e) {
    //          throw new RuntimeException(e);
    //        }
    //      }
    //    }
    return (List<T>) results.stream().map(WorkflowContext::getResult).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getErrors() {
    return (List<T>)
        results.stream().map(WorkflowContext::getException).collect(Collectors.toList());
  }

  public void clearResults() {
    results.clear();
  }

  @Data
  @Builder
  @ToString
  public static class WorkflowContext {
    private Object target;
    private Method method;
    private Object[] args;
    private Object result;
    private Exception exception;
  }
}
