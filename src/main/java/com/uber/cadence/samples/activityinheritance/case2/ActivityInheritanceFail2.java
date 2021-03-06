package com.uber.cadence.samples.activityinheritance.case2;

import static com.uber.cadence.samples.activityinheritance.Constants.TASK_LIST;
import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.samples.activityinheritance.ActivityInheritanceWorkflow;
import com.uber.cadence.samples.activityinheritance.dto.BaseRequest;
import com.uber.cadence.samples.activityinheritance.dto.BaseResponse;
import com.uber.cadence.samples.activityinheritance.dto.TypeARequest;
import com.uber.cadence.samples.activityinheritance.dto.TypeAResponse;
import com.uber.cadence.samples.activityinheritance.dto.TypeBRequest;
import com.uber.cadence.samples.activityinheritance.dto.TypeBResponse;
import com.uber.cadence.worker.Worker;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** base generic interface + custom interfaces with explicit types and activity methods */
public class ActivityInheritanceFail2 {

  public static void main(String[] args) {

    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    worker.registerWorkflowImplementationTypes(ActivityInheritanceWorkflowFail2Impl.class);
    worker.registerActivitiesImplementations(new TypeAActivityImpl(), new TypeBActivityImpl());
    factory.start();

    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    ActivityInheritanceWorkflow workflow =
        workflowClient.newWorkflowStub(ActivityInheritanceWorkflow.class);

    List<String> result = workflow.send("request123");
    System.out.println(result);

    System.exit(0);
  }

  public interface BaseActivity<T extends BaseRequest, S extends BaseResponse> {

    S process(T request);
  }

  public interface TypeAActivity extends BaseActivity<TypeARequest, TypeAResponse> {

    @ActivityMethod
    TypeAResponse process(TypeARequest request);
  }

  public interface TypeBActivity extends BaseActivity<TypeBRequest, TypeBResponse> {

    @ActivityMethod
    TypeBResponse process(TypeBRequest request);
  }

  public static class TypeAActivityImpl extends BaseActivityImpl<TypeARequest, TypeAResponse>
      implements TypeAActivity {

    @Override
    protected TypeAResponse send(TypeARequest request) {
      return TypeAResponse.builder()
          .requestId(request.getRequestId())
          .responseA("test response A for request " + request)
          .success(true)
          .build();
    }

    @Override
    protected TypeAResponse createErrorResponse(String requestId, String error) {
      return TypeAResponse.builder().requestId(requestId).success(false).responseA(error).build();
    }
  }

  public static class TypeBActivityImpl extends BaseActivityImpl<TypeBRequest, TypeBResponse>
      implements TypeBActivity {

    @Override
    protected TypeBResponse send(TypeBRequest request) {
      return TypeBResponse.builder()
          .requestId(request.getRequestId())
          .responseB("test response B for request " + request)
          .success(true)
          .build();
    }

    @Override
    protected TypeBResponse createErrorResponse(String requestId, String error) {
      return TypeBResponse.builder().requestId(requestId).success(false).responseB(error).build();
    }
  }

  @Slf4j
  public abstract static class BaseActivityImpl<T extends BaseRequest, S extends BaseResponse>
      implements BaseActivity<T, S> {

    protected abstract S send(T request);

    protected abstract S createErrorResponse(String requestId, String error);

    @Override
    public S process(T request) {
      log.info("processing requestId: {}", request.getRequestId());
      try {
        final S response = send(request);
        if (response.isSuccess()) {
          log.info("received success response: {}", response);
        } else {
          log.error("received error response: {}", response);
        }
        return response;
      } catch (Exception e) {
        log.error("exception while processing request {}", request, e);
      }
      return createErrorResponse(
          request.getRequestId(), "no response for " + this.getClass().getSimpleName());
    }
  }
}
