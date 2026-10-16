package com.lms.disbursement.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.activity.ActivityInterface;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Proves the Temporal Java SDK 1.40.0 runs a workflow and an activity on JDK 25 using the in-JVM,
 * time-skipping {@link TestWorkflowEnvironment} — no Docker, no external Temporal server (ADR
 * 0001's least-verified item; ADR 0008). A workflow timer is skipped instantly by the test
 * environment, which the assertion relies on.
 */
class TemporalSdkSmokeTest {

    static final String TASK_QUEUE = "smoke";

    @ActivityInterface
    public interface EchoActivities {
        String echo(String in);
    }

    public static final class EchoActivitiesImpl implements EchoActivities {
        @Override
        public String echo(String in) {
            return "pong:" + in;
        }
    }

    @WorkflowInterface
    public interface PingWorkflow {
        @WorkflowMethod
        String ping(String in);
    }

    public static final class PingWorkflowImpl implements PingWorkflow {
        private final EchoActivities activities =
                Workflow.newActivityStub(
                        EchoActivities.class,
                        io.temporal.activity.ActivityOptions.newBuilder()
                                .setStartToCloseTimeout(Duration.ofSeconds(5))
                                .build());

        @Override
        public String ping(String in) {
            Workflow.sleep(Duration.ofMinutes(30)); // skipped instantly by the test environment
            return activities.echo(in);
        }
    }

    @Test
    void workflowAndActivityRunOnJdk25() {
        TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance();
        try {
            Worker worker = env.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PingWorkflowImpl.class);
            worker.registerActivitiesImplementations(new EchoActivitiesImpl());
            env.start();

            WorkflowClient client = env.getWorkflowClient();
            PingWorkflow workflow =
                    client.newWorkflowStub(
                            PingWorkflow.class,
                            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

            assertThat(workflow.ping("hi")).isEqualTo("pong:hi");
        } finally {
            env.close();
        }
    }
}
