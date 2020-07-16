package com.example.parent.context;

import com.uber.cadence.client.WorkflowException;
import com.uber.cadence.workflow.Functions.Func1;
import com.uber.cadence.workflow.Functions.Func2;
import com.uber.cadence.workflow.Promise;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DummyPromise implements Promise<WorkflowException> {

  private final Promise<?> delegate;

  public DummyPromise(Promise<?> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isCompleted() {
    return false;
  }

  @Override
  public WorkflowException get() {
    return null;
  }

  @Override
  public WorkflowException get(WorkflowException defaultValue) {
    return null;
  }

  @Override
  public WorkflowException get(long timeout, TimeUnit unit) throws TimeoutException {
    return null;
  }

  @Override
  public WorkflowException get(long timeout, TimeUnit unit, WorkflowException defaultValue) {
    return null;
  }

  @Override
  public RuntimeException getFailure() {
    return null;
  }

  @Override
  public <U> Promise<U> thenApply(Func1<? super WorkflowException, ? extends U> fn) {
    return null;
  }

  @Override
  public <U> Promise<U> handle(Func2<? super WorkflowException, RuntimeException, ? extends U> fn) {
    return null;
  }

  @Override
  public <U> Promise<U> thenCompose(Func1<? super WorkflowException, ? extends Promise<U>> fn) {
    return null;
  }

  @Override
  public Promise<WorkflowException> exceptionally(
      Func1<Throwable, ? extends WorkflowException> fn) {
    return null;
  }
}
