package com.lms.collections.config;

import com.lms.collections.adapters.out.temporal.DunningActivitiesImpl;
import com.lms.collections.adapters.out.temporal.DunningWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal client and worker wiring (CLAUDE.md: Temporal embedded in disbursement and collections
 * only). Virtual threads are enabled for both activity and workflow execution per ADR 0008; they
 * are never pooled. Connects to the Temporal dev server at runtime (Docker session).
 */
@Configuration
public class TemporalConfig {

    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs workflowServiceStubs(
            @Value("${lms.temporal.target:127.0.0.1:7233}") String target) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }

    @Bean(destroyMethod = "shutdown")
    public WorkerFactory workerFactory(
            WorkflowClient workflowClient,
            DunningActivitiesImpl activities,
            @Value("${lms.temporal.task-queue:collections}") String taskQueue) {
        WorkerFactory factory =
                WorkerFactory.newInstance(
                        workflowClient,
                        WorkerFactoryOptions.newBuilder()
                                .setUsingVirtualWorkflowThreads(true)
                                .build());
        Worker worker =
                factory.newWorker(
                        taskQueue, WorkerOptions.newBuilder().setUsingVirtualThreads(true).build());
        worker.registerWorkflowImplementationTypes(DunningWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
