package com.example.parent.context;

import com.uber.cadence.PollForActivityTaskRequest;
import com.uber.cadence.PollForActivityTaskResponse;
import com.uber.cadence.PollForDecisionTaskRequest;
import com.uber.cadence.PollForDecisionTaskResponse;
import com.uber.cadence.RespondActivityTaskCompletedRequest;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.workflow.Functions;
import com.uber.tchannel.api.SubChannel;
import com.uber.tchannel.api.errors.TChannelError;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;

@Slf4j
public class WorkflowServiceTimeoutStoredChannel extends WorkflowServiceTChannel {

    boolean timeoutError;
    Functions.Func1<TChannelError, Void> callback;

    public void setCallbackOnError(Functions.Func1<TChannelError, Void> callback) {

    }

    public boolean isChannelTimeoutError() {
        return timeoutError;
    }

    public WorkflowServiceTimeoutStoredChannel() {
    }

    public WorkflowServiceTimeoutStoredChannel(String host, int port) {
        super(host, port);
    }

    public WorkflowServiceTimeoutStoredChannel(String host, int port, ClientOptions options) {
        super(host, port, options);
    }

    public WorkflowServiceTimeoutStoredChannel(SubChannel subChannel, ClientOptions options) {
        super(subChannel, options);
    }

    @Override
    public PollForDecisionTaskResponse PollForDecisionTask(PollForDecisionTaskRequest request)
        throws TException {
        PollForDecisionTaskResponse response = null;
        try {
            response = super.PollForDecisionTask(request);
        } catch (TException e) {
            checkException(e);
            throw e;
        }
        return response;
    }

    private void checkException(TException e) {
        if (e instanceof TTransportException && ((TTransportException) e).getType() == TTransportException.TIMED_OUT) {
            timeoutError = true;
            log.error("Connection timeout", e);
        }
    }

    @Override
    public PollForActivityTaskResponse PollForActivityTask(PollForActivityTaskRequest request) throws TException {
        PollForActivityTaskResponse response = null;
        try {
            response = super.PollForActivityTask(request);
        } catch (TException e) {
            checkException(e);
            throw e;
        }
        return response;
    }

    @Override
    public void RespondActivityTaskCompleted(RespondActivityTaskCompletedRequest request) throws TException {
        try {
            super.RespondActivityTaskCompleted(request);
        } catch (TException e) {
            checkException(e);
            throw e;
        }
    }

}
