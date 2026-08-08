package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.required.WorkerState;
import com.dbfleetops.worker.application.WorkerShutdownState;
import org.springframework.stereotype.Component;

@Component
public class WorkerContextAdapter implements WorkerState {
    private final WorkerShutdownState state;
    public WorkerContextAdapter(WorkerShutdownState state) { this.state = state; }
    public boolean isShuttingDown() { return state.isShuttingDown(); }
}
