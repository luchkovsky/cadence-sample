package com.example.parent.context;

import static lombok.AccessLevel.PRIVATE;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.uber.cadence.internal.sync.WorkflowInternal;
import com.uber.cadence.workflow.Async;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;



@AllArgsConstructor(access = PRIVATE)
public class ChildFlowProxy {

    private final Queue<WorkflowExecutions> calls;
    private final Queue<WorkflowExecutions> results;
    private final ScheduledExecutorService executorService;

    private final long time;
    private final int  blockSize;
    private final long  wait;

    public ChildFlowProxy() {
       this(1, 100, 1000);
    }

    public ChildFlowProxy(long time, int blockSize, long wait) {
        this.time = time;
        this.blockSize = blockSize;
        this.wait = wait;


        calls = new ArrayBlockingQueue<WorkflowExecutions>(blockSize);
        results = new ArrayBlockingQueue<WorkflowExecutions>(blockSize);
        executorService = Executors.newScheduledThreadPool(
            1,
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("child-workflow-batch-pool-%d")
                .build());
    }

    private void delayInvoke() {
        executorService.shutdown();
        executorService.schedule(() -> {
            executeBatch(blockSize);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (!calls.isEmpty()) {
                delayInvoke();
            }
        }, time, TimeUnit.MILLISECONDS);
    }

    private void executeBatch(int blockSize) {
        while (!calls.isEmpty() && results.size() < blockSize) {
            WorkflowExecutions context = calls.poll();
            try {
                Promise<?> childFlow = Async.function( ()-> {
                    try {
                        context.getMethod().invoke(context.getTarget(), context.getArgs());
                    } catch (Exception ex) {
                        context.setException(ex);
                    }
                    return null;
                });
                context.setResult( childFlow.get() );
            } catch (Exception ex) {
                context.setException(ex);
            }
            results.add(context);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<?> workflowInterface, T instance) {
        return (T) Proxy.newProxyInstance(
            WorkflowInternal.class.getClassLoader(),
            new Class<?>[]{workflowInterface},
            new InvocationHandler() {

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                    WorkflowMethod workflowMethod = method.getAnnotation(WorkflowMethod.class);
                    if (workflowMethod == null) {
                       return method.invoke(instance, args);
                    }
                    calls.add(WorkflowExecutions.builder()
                        .target(instance)
                        .method(method)
                        .args(args)
                        .build());
                    delayInvoke();

                    return results;
                }
            });
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getResults() {
        return (List<T>)results.stream().map(WorkflowExecutions::getResult).collect(Collectors.toList());
    }

    public void clearResults(){
        results.clear();
    }

    @Data
    @Builder
    public static class WorkflowExecutions {
        private Object target;
        private Method method;
        private Object[] args;
        private Object result;
        private Exception exception;
    }

}
