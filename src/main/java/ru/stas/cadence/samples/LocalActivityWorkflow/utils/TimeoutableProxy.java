package ru.stas.cadence.samples.LocalActivityWorkflow.utils;

import static lombok.AccessLevel.PRIVATE;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.internal.sync.WorkflowInternal;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;


@AllArgsConstructor(access = PRIVATE)
public class TimeoutableProxy {

    public static CompletableFuture<?> runWithTimeout(
        Runnable runnable, long timeout, TimeUnit unit) {

        CompletableFuture<Void> other = new CompletableFuture<>();
        Executors.newScheduledThreadPool(
            1,
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("local-activity-thread-pool-%d")
                .build())
            .schedule(() -> {
                ActivityTimeoutException ex = new ActivityTimeoutException(
                    "Activity timeout after " + timeout);
                return other.completeExceptionally(ex);
            }, timeout, unit);
        return CompletableFuture.runAsync(runnable).applyToEither(other, a -> a);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<?> activityInterface, T activity, long timeout, TimeUnit unit){
        return (T) Proxy.newProxyInstance(
            WorkflowInternal.class.getClassLoader(),
            new Class<?>[]{ activityInterface }, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    ActivityMethod activityMethod = method.getAnnotation(ActivityMethod.class);
                    if (activityMethod == null){
                       return method.invoke(activity, args);
                    }
                    Object[] result = new Object[1];
                    CompletableFuture<?> future = runWithTimeout(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                result[0]=method.invoke(activity, args);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }, timeout, unit);
                    try {
                        future.get();
                        return result[0];
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
    }

}
