package com.treeeducation.ioas.customertracking;

import com.treeeducation.ioas.applicationflow.*;
import com.treeeducation.ioas.lead.Lead;
import com.treeeducation.ioas.lead.LeadRepository;
import com.treeeducation.ioas.student.StudentProfile;
import com.treeeducation.ioas.student.StudentProfileRepository;
import com.treeeducation.ioas.student.StudentProfileStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerTrackingService {
    private final StudentProfileRepository studentRepository;
    private final LeadRepository leadRepository;
    private final ApplicationFlowRepository flowRepository;
    private final ApplicationFlowStepRepository stepRepository;
    private final ApplicationFlowAttachmentRepository attachmentRepository;

    public List<CustomerTrackingDtos.Summary> list(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        return studentRepository.findByProfileStatusNotOrderByCreatedAtDesc(StudentProfileStatus.DELETED).stream()
                .filter(s -> k.isBlank() || contains(s.getStudentName(), k) || contains(s.getStudentNo(), k) || contains(s.getSourceLeadNo(), k))
                .map(this::summary)
                .sorted(Comparator.comparing(CustomerTrackingDtos.Summary::lastActionAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public CustomerTrackingDtos.Detail detail(Long customerId) {
        StudentProfile customer = studentRepository.findById(customerId).orElseThrow();
        Optional<Lead> lead = leadRepository.findById(customer.getSourceLeadId());
        Optional<ApplicationFlow> flowOpt = flowRepository.findByStudentProfileId(customerId);
        List<CustomerTrackingDtos.Event> events = new ArrayList<>();
        List<CustomerTrackingDtos.FlowNode> graph = new ArrayList<>();
        List<CustomerTrackingDtos.ApplicationStepNode> appSteps = new ArrayList<>();

        lead.ifPresent(l -> {
            events.add(new CustomerTrackingDtos.Event("lead-" + l.getId(), "LEAD_CREATED", "线索生成", sourceText(l), l.getAssignedToName(), l.getCreatedAt(), l.getId(), "LEAD"));
            graph.add(new CustomerTrackingDtos.FlowNode("lead", "线索生成", "start", "COMPLETED", "来源：" + blank(l.getSourceChannel(), l.getSourceType()), l.getCreatedAt()));
            if (isTransferred(l)) {
                events.add(new CustomerTrackingDtos.Event("lead-transfer-" + l.getId(), "LEAD_TRANSFER", "线索曾发生转让/分配", transferText(l), l.getAssignedToName(), l.getAssignedAt(), l.getId(), "LEAD"));
                graph.add(new CustomerTrackingDtos.FlowNode("transfer", "转让/分配", "condition", "COMPLETED", transferText(l), l.getAssignedAt()));
            } else {
                graph.add(new CustomerTrackingDtos.FlowNode("transfer", "是否转让", "condition", "SKIPPED", "否，当前没有转让记录", null));
            }
        });

        events.add(new CustomerTrackingDtos.Event("customer-" + customer.getId(), "CUSTOMER_CREATED", "客户档案生成", "线索已转为正式客户档案，可进入申请流程", customer.getOwnerConsultantName(), customer.getCreatedAt(), customer.getId(), "CUSTOMER"));
        graph.add(new CustomerTrackingDtos.FlowNode("customer", "客户档案", "process", "COMPLETED", "客户编号：" + blank(customer.getStudentNo(), "-"), customer.getCreatedAt()));

        flowOpt.ifPresent(flow -> {
            graph.add(new CustomerTrackingDtos.FlowNode("flow", "申请流程", "process", flow.getCompleted() ? "COMPLETED" : "IN_PROGRESS", "进度：" + nvl(flow.getProgressPercent()) + "%", flow.getUpdatedAt()));
            List<ApplicationFlowStep> steps = stepRepository.findByFlowIdOrderByOrderNoAsc(flow.getId());
            for (ApplicationFlowStep step : steps) {
                List<ApplicationFlowAttachment> files = attachmentRepository.findByStepIdAndDeletedFalseOrderByCreatedAtDesc(step.getId());
                String note = blank(step.getCustomerVisibleNote(), step.getConsultantNote());
                appSteps.add(new CustomerTrackingDtos.ApplicationStepNode(step.getStepCode(), step.getStepName(), step.getStatus(), files.size(), note, step.getStartedAt(), step.getCompletedAt()));
                events.add(new CustomerTrackingDtos.Event("step-" + step.getId(), "APPLICATION_STEP", step.getStepName(), stepText(step, files.size()), flow.getOwnerConsultantName(), firstTime(step), step.getId(), "APPLICATION_STEP"));
                graph.add(new CustomerTrackingDtos.FlowNode("step-" + step.getStepCode(), step.getStepName(), "process", step.getStatus().name(), note.isBlank() ? "材料数：" + files.size() : note, firstTime(step)));
                for (ApplicationFlowAttachment file : files) {
                    events.add(new CustomerTrackingDtos.Event("file-" + file.getId(), "ATTACHMENT", "上传材料：" + file.getOriginalFilename(), blank(file.getNote(), file.getAttachmentType().name()), file.getUploadedByName(), file.getCreatedAt(), file.getId(), "ATTACHMENT"));
                }
            }
        });

        events.sort(Comparator.comparing(CustomerTrackingDtos.Event::happenedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new CustomerTrackingDtos.Detail(summary(customer), events, graph, appSteps);
    }

    public CustomerTrackingDtos.Summary summary(StudentProfile customer) {
        Optional<Lead> lead = leadRepository.findById(customer.getSourceLeadId());
        Optional<ApplicationFlow> flowOpt = flowRepository.findByStudentProfileId(customer.getId());
        ApplicationFlow flow = flowOpt.orElse(null);
        String currentStepName = null;
        if (flow != null && flow.getCurrentStep() != null) {
            currentStepName = stepRepository.findByFlowIdAndStepCode(flow.getId(), flow.getCurrentStep()).map(ApplicationFlowStep::getStepName).orElse(flow.getCurrentStep().name());
        }
        Instant lastActionAt = max(customer.getUpdatedAt(), flow == null ? null : flow.getUpdatedAt());
        return new CustomerTrackingDtos.Summary(customer.getId(), customer.getStudentNo(), customer.getStudentName(), customer.getSourceLeadId(), customer.getSourceLeadNo(),
                lead.map(Lead::getCreatedAt).orElse(null), lead.map(this::isTransferred).orElse(false), lead.map(Lead::getAssignedAt).orElse(null), customer.getCreatedAt(),
                flow == null ? null : flow.getId(), flow == null ? 0 : flow.getProgressPercent(), currentStepName, lastActionAt);
    }

    private boolean isTransferred(Lead lead) {
        String mode = lead.getAssignMode() == null ? "" : lead.getAssignMode().toLowerCase(Locale.ROOT);
        String reason = lead.getAssignReason() == null ? "" : lead.getAssignReason();
        return mode.contains("transfer") || reason.contains("转让");
    }

    private String sourceText(Lead lead) {
        return "来源：" + blank(lead.getSourceChannel(), lead.getSourceType()) + "，页面：" + blank(lead.getSourcePage(), "-");
    }

    private String transferText(Lead lead) {
        return "当前顾问：" + blank(lead.getAssignedToName(), "-") + "，原因：" + blank(lead.getAssignReason(), "-");
    }

    private String stepText(ApplicationFlowStep step, int files) {
        String note = blank(step.getCustomerVisibleNote(), step.getConsultantNote());
        return (note.isBlank() ? "当前状态：" + step.getStatus().name() : note) + "，材料数：" + files;
    }

    private Instant firstTime(ApplicationFlowStep step) {
        return step.getCompletedAt() != null ? step.getCompletedAt() : step.getStartedAt();
    }

    private Instant max(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private boolean contains(String s, String keyword) { return s != null && s.contains(keyword); }
    private String blank(String a, String b) { return a == null || a.isBlank() ? (b == null ? "" : b) : a; }
    private Integer nvl(Integer v) { return v == null ? 0 : v; }
}
