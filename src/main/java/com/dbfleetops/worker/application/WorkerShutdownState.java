package com.dbfleetops.worker.application;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkerShutdownState {

    private static final Logger log = LoggerFactory.getLogger(WorkerShutdownState.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("worker_shutdown_started stop_new_job_claim=true");
        }
    }

    public void markShuttingDown() {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("worker_shutdown_marked stop_new_job_claim=true");
        }
    }
}
