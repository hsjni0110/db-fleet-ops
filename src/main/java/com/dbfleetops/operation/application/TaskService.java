package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.AgentTasks;
import com.dbfleetops.operation.application.required.AgentReader;
import com.dbfleetops.operation.application.required.TaskStore;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.dto.CreateOperationTaskRequest;
import com.dbfleetops.operation.dto.OperationTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리 요청을 Agent가 실행할 Task로 만드는 서비스입니다. */
@Service
public class TaskService implements AgentTasks {
    private final AgentReader agents;
    private final TaskStore tasks;
    public TaskService(AgentReader agents, TaskStore tasks) { this.agents = agents; this.tasks = tasks; }

    @Transactional
    public OperationTaskResponse createTask(CreateOperationTaskRequest request) {
        agents.findAgent(request.agentId()).orElseThrow(() -> new IllegalArgumentException(
                "Agent not found. agentId=" + request.agentId()));
        OperationTask task = request.operationJobId() == null
                ? OperationTask.create(request.agentId(), request.taskType(), request.parametersJson())
                : OperationTask.createForJob(request.agentId(), request.operationJobId(),
                        request.taskType(), request.parametersJson());
        return OperationTaskResponse.from(tasks.save(task));
    }
}
