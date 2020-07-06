package com.example.parent.context;

import static lombok.AccessLevel.PRIVATE;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.uber.cadence.internal.sync.WorkflowInternal;
import com.uber.cadence.workflow.WorkflowMethod;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;


@AllArgsConstructor(access = PRIVATE)
public class StreamBreakingProxy {

    private final Queue<WorkflowExecutions> calls;
    private final Queue<WorkflowExecutions> results;
    private final ScheduledExecutorService executorService;

    public StreamBreakingProxy(int capacity) {
        calls = new ArrayBlockingQueue<WorkflowExecutions>(capacity);
        results = new ArrayBlockingQueue<WorkflowExecutions>(capacity);
        executorService = Executors.newScheduledThreadPool(
            1,
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("child-workflow-batch-pool-%d")
                .build());
    }

    private void delayedInvoker(long time, long wait, int blockSize) {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        executorService.schedule(() -> {
            executeBatch(blockSize);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (!calls.isEmpty()) {
                delayedInvoker(time, wait, blockSize);
            }
        }, time, TimeUnit.MILLISECONDS);
    }

    private void executeBatch(int blockSize) {
        while (!calls.isEmpty() && results.size() < blockSize) {
            WorkflowExecutions context = calls.poll();
            try {
                context.setResult(context.getMethod().invoke(context.getProxy(), context.getArgs()));
            } catch (Exception ex) {
                context.setException(ex);
            }
            results.add(context);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<?> workflowInterface,
        long time, int blockSize, long wait) {
        return (T) Proxy.newProxyInstance(
            WorkflowInternal.class.getClassLoader(),
            new Class<?>[]{workflowInterface},
            new InvocationHandler() {

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                    WorkflowMethod workflowMethod = method.getAnnotation(WorkflowMethod.class);
                    if (workflowMethod == null) {
                        return method.invoke(proxy, args);
                    }
                    WorkflowExecutions context = WorkflowExecutions.builder()
                        .proxy(proxy)
                        .method(method)
                        .args(args)
                        .build();
                    calls.add(context);
                    delayedInvoker(time, wait, blockSize);
                    return calls;
                }
            });

    }

    @Data
    @Builder
    public static class WorkflowExecutions {

        private Object proxy;
        private Method method;
        private Object[] args;
        private Object result;
        private Exception exception;
    }

}
