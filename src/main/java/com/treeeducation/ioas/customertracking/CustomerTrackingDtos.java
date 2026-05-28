package com.treeeducation.ioas.customertracking;

import com.treeeducation.ioas.applicationflow.ApplicationStepCode;
import com.treeeducation.ioas.applicationflow.ApplicationStepStatus;
import java.time.Instant;
import java.util.List;

public class CustomerTrackingDtos {
    public record Summary(Long customerId, String customerNo, String customerName, Long sourceLeadId, String sourceLeadNo,
                          Instant leadCreatedAt, Boolean transferred, Instant transferredAt, Instant customerCreatedAt,
                          Long flowId, Integer progressPercent, String currentStepName, Instant lastActionAt) {}

    public record Event(String id, String type, String title, String content, String operatorName, Instant happenedAt,
                        Long relatedId, String relatedType) {}

    public record FlowNode(String id, String label, String nodeType, String status, String description, Instant happenedAt) {}

    public record ApplicationStepNode(ApplicationStepCode stepCode, String stepName, ApplicationStepStatus status,
                                      Integer uploadedFileCount, String note, Instant startedAt, Instant completedAt) {}

    public record Detail(Summary summary, List<Event> events, List<FlowNode> graphNodes,
                         List<ApplicationStepNode> applicationSteps) {}
}
