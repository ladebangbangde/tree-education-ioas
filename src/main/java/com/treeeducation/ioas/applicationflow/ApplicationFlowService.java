package com.treeeducation.ioas.applicationflow;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.common.PageResponse;
import com.treeeducation.ioas.notification.NotificationService;
import com.treeeducation.ioas.student.StudentProfile;
import com.treeeducation.ioas.student.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApplicationFlowService {
    private final ApplicationFlowRepository flows;
    private final ApplicationFlowStepRepository steps;
    private final ApplicationFlowAttachmentRepository attachments;
    private final StudentProfileRepository students;
    private final ApplicationFlowStorageService storage;
    private final NotificationService notifications;

    public ApplicationFlowService(ApplicationFlowRepository flows, ApplicationFlowStepRepository steps,
                                  ApplicationFlowAttachmentRepository attachments, StudentProfileRepository students,
                                  ApplicationFlowStorageService storage, NotificationService notifications) {
        this.flows = flows;
        this.steps = steps;
        this.attachments = attachments;
        this.students = students;
        this.storage = storage;
        this.notifications = notifications;
    }

    public PageResponse<ApplicationFlowDtos.Response> list(String keyword, int pageNum, int pageSize, UserPrincipal p) {
        List<ApplicationFlow> rows = "CONSULTANT".equalsIgnoreCase(p.role())
                ? flows.findByOwnerConsultantIdOrderByUpdatedAtDesc(p.id())
                : flows.findAllByOrderByUpdatedAtDesc();
        String k = clean(keyword);
        List<ApplicationFlowDtos.Response> data = rows.stream()
                .filter(f -> k == null || contains(f.getStudentName(), k) || contains(f.getStudentNo(), k))
                .map(this::toResponse)
                .toList();
        return PageResponse.of(data, pageNum, pageSize);
    }

    public ApplicationFlowDtos.Response detail(Long flowId, UserPrincipal p) {
        return toResponse(getAndCheckRead(flowId, p));
    }

    public ApplicationFlowDtos.Response detailByStudent(Long studentProfileId, UserPrincipal p) {
        ApplicationFlow flow = flows.findByStudentProfileId(studentProfileId).orElseThrow(() -> BusinessException.notFound("申请流程不存在"));
        assertCanRead(flow, p);
        return toResponse(flow);
    }

    public ApplicationFlowDtos.Response customerProgress(Long studentProfileId) {
        ApplicationFlow flow = flows.findByStudentProfileId(studentProfileId).orElseThrow(() -> BusinessException.notFound("申请流程不存在"));
        return toResponse(flow);
    }

    @Transactional
    public ApplicationFlowDtos.Response start(Long studentProfileId, ApplicationFlowDtos.StartRequest request, UserPrincipal p) {
        StudentProfile student = students.findById(studentProfileId).orElseThrow(() -> BusinessException.notFound("客户档案不存在"));
        if (!"SUPER_ADMIN".equalsIgnoreCase(p.role()) && !("CONSULTANT".equalsIgnoreCase(p.role()) && p.id().equals(student.getOwnerConsultantId()))) {
            throw BusinessException.forbidden("只有负责顾问或超管可以创建申请流程");
        }
        ApplicationFlow existing = flows.findByStudentProfileId(studentProfileId).orElse(null);
        if (existing != null) return toResponse(existing);

        ApplicationFlow flow = new ApplicationFlow();
        flow.setStudentProfileId(student.getId());
        flow.setStudentNo(student.getStudentNo());
        flow.setStudentName(student.getStudentName());
        flow.setOwnerConsultantId(student.getOwnerConsultantId());
        flow.setOwnerConsultantName(student.getOwnerConsultantName());
        flow.setCurrentStep(ApplicationStepCode.PREPARE_MATERIALS);
        flow.setProgressPercent(0);
        flow.setCompleted(false);
        flow.setRemark(clean(request == null ? null : request.remark()));
        flow.setCreatedAt(Instant.now());
        flow.setUpdatedAt(Instant.now());
        flow = flows.save(flow);

        for (ApplicationStepCode code : ApplicationStepCode.ordered()) {
            ApplicationFlowStep step = new ApplicationFlowStep();
            step.setFlowId(flow.getId());
            step.setStudentProfileId(student.getId());
            step.setStepCode(code);
            step.setOrderNo(code.orderNo());
            step.setStepName(code.label());
            step.setStatus(code == ApplicationStepCode.PREPARE_MATERIALS ? ApplicationStepStatus.PENDING : ApplicationStepStatus.LOCKED);
            step.setRequired(true);
            step.setUploadedFileCount(0);
            step.setCreatedAt(Instant.now());
            step.setUpdatedAt(Instant.now());
            steps.save(step);
        }
        notifications.sendToUser(flow.getOwnerConsultantId(), "CONSULTANT", "申请流程已创建", "客户[" + flow.getStudentName() + "]的申请流程已创建，请开始准备申请材料。", "application_flow", flow.getId(), "/application-flows/" + flow.getId(), "APPLICATION_FLOW_CREATED", 20);
        return toResponse(flow);
    }

    @Transactional
    public ApplicationFlowDtos.Response updateStep(Long flowId, ApplicationStepCode stepCode, ApplicationFlowDtos.AdvanceRequest request, UserPrincipal p) {
        ApplicationFlow flow = getAndCheckMutate(flowId, p);
        ApplicationFlowStep step = steps.findByFlowIdAndStepCode(flowId, stepCode).orElseThrow(() -> BusinessException.notFound("流程节点不存在"));
        if (request != null && request.version() != null && !request.version().equals(step.getVersion())) {
            throw BusinessException.badRequest("节点已被其他人更新，请刷新后重试");
        }
        ApplicationStepStatus target = request == null || request.status() == null ? ApplicationStepStatus.IN_PROGRESS : request.status();
        assertStepCanChange(flow, step, target);
        if (target == ApplicationStepStatus.IN_PROGRESS && step.getStartedAt() == null) step.setStartedAt(Instant.now());
        if (target == ApplicationStepStatus.COMPLETED) {
            if (step.getStartedAt() == null) step.setStartedAt(Instant.now());
            step.setCompletedAt(Instant.now());
            step.setCompletedBy(p.id());
        }
        step.setStatus(target);
        if (request != null && request.consultantNote() != null) step.setConsultantNote(clean(request.consultantNote()));
        if (request != null && request.customerVisibleNote() != null) step.setCustomerVisibleNote(clean(request.customerVisibleNote()));
        step.setUpdatedAt(Instant.now());
        steps.save(step);
        refreshFlowProgress(flow);
        unlockNextIfNeeded(flow, step, target);
        notifications.sendToUser(flow.getOwnerConsultantId(), "CONSULTANT", "申请流程节点更新", "客户[" + flow.getStudentName() + "]的节点[" + step.getStepName() + "]已更新为" + target.name(), "application_flow", flow.getId(), "/application-flows/" + flow.getId(), "APPLICATION_FLOW_STEP_UPDATED", 10);
        return toResponse(flows.save(flow));
    }

    @Transactional
    public ApplicationFlowDtos.AttachmentResponse upload(Long flowId, ApplicationStepCode stepCode, ApplicationAttachmentType type, String note, MultipartFile file, UserPrincipal p) {
        ApplicationFlow flow = getAndCheckMutate(flowId, p);
        ApplicationFlowStep step = steps.findByFlowIdAndStepCode(flowId, stepCode).orElseThrow(() -> BusinessException.notFound("流程节点不存在"));
        if (step.getStatus() == ApplicationStepStatus.LOCKED || step.getStatus() == ApplicationStepStatus.COMPLETED) throw BusinessException.badRequest("当前节点不允许上传材料");
        ApplicationFlowStorageService.StoredObject stored = storage.upload(flow.getStudentProfileId(), stepCode, file);
        ApplicationFlowAttachment a = new ApplicationFlowAttachment();
        a.setFlowId(flow.getId());
        a.setStepId(step.getId());
        a.setStudentProfileId(flow.getStudentProfileId());
        a.setStepCode(stepCode);
        a.setAttachmentType(type == null ? ApplicationAttachmentType.OTHER : type);
        a.setOriginalFilename(stored.originalFilename());
        a.setContentType(stored.contentType());
        a.setSizeBytes(stored.sizeBytes());
        a.setObjectKey(stored.objectKey());
        a.setFileUrl(stored.fileUrl());
        a.setNote(clean(note));
        a.setUploadedBy(p.id());
        a.setUploadedByName(p.userName() == null ? p.username() : p.userName());
        a.setCreatedAt(Instant.now());
        a = attachments.save(a);
        step.setUploadedFileCount((int) attachments.countByStepIdAndDeletedFalse(step.getId()));
        if (step.getStatus() == ApplicationStepStatus.PENDING) {
            step.setStatus(ApplicationStepStatus.IN_PROGRESS);
            step.setStartedAt(Instant.now());
        }
        step.setUpdatedAt(Instant.now());
        steps.save(step);
        flow.setUpdatedAt(Instant.now());
        flows.save(flow);
        notifications.sendToUser(flow.getOwnerConsultantId(), "CONSULTANT", "申请材料已上传", "客户[" + flow.getStudentName() + "]在节点[" + step.getStepName() + "]新增材料：" + a.getOriginalFilename(), "application_flow", flow.getId(), "/application-flows/" + flow.getId(), "APPLICATION_ATTACHMENT_UPLOADED", 10);
        return attachmentResponse(a);
    }

    private void assertStepCanChange(ApplicationFlow flow, ApplicationFlowStep step, ApplicationStepStatus target) {
        if (step.getStatus() == ApplicationStepStatus.LOCKED) throw BusinessException.badRequest("请先完成前置流程节点");
        if (target == ApplicationStepStatus.COMPLETED) {
            for (ApplicationFlowStep s : steps.findByFlowIdOrderByOrderNoAsc(flow.getId())) {
                if (s.getOrderNo() < step.getOrderNo() && s.getStatus() != ApplicationStepStatus.COMPLETED) throw BusinessException.badRequest("请按流程顺序完成前置节点");
            }
        }
    }

    private void refreshFlowProgress(ApplicationFlow flow) {
        List<ApplicationFlowStep> all = steps.findByFlowIdOrderByOrderNoAsc(flow.getId());
        long done = all.stream().filter(s -> s.getStatus() == ApplicationStepStatus.COMPLETED).count();
        flow.setProgressPercent((int) Math.round(done * 100.0 / all.size()));
        flow.setCompleted(done == all.size());
        flow.setCurrentStep(all.stream().filter(s -> s.getStatus() != ApplicationStepStatus.COMPLETED).findFirst().map(ApplicationFlowStep::getStepCode).orElse(ApplicationStepCode.VISA_APPROVED_TICKET));
        flow.setUpdatedAt(Instant.now());
    }

    private void unlockNextIfNeeded(ApplicationFlow flow, ApplicationFlowStep step, ApplicationStepStatus target) {
        if (target != ApplicationStepStatus.COMPLETED) return;
        steps.findByFlowIdOrderByOrderNoAsc(flow.getId()).stream()
                .filter(s -> s.getOrderNo().equals(step.getOrderNo() + 1) && s.getStatus() == ApplicationStepStatus.LOCKED)
                .findFirst()
                .ifPresent(next -> { next.setStatus(ApplicationStepStatus.PENDING); next.setUpdatedAt(Instant.now()); steps.save(next); });
    }

    private ApplicationFlow getAndCheckRead(Long flowId, UserPrincipal p) {
        ApplicationFlow flow = flows.findById(flowId).orElseThrow(() -> BusinessException.notFound("申请流程不存在"));
        assertCanRead(flow, p);
        return flow;
    }

    private ApplicationFlow getAndCheckMutate(Long flowId, UserPrincipal p) {
        ApplicationFlow flow = getAndCheckRead(flowId, p);
        if (flow.getCompleted()) throw BusinessException.badRequest("申请流程已完成");
        return flow;
    }

    private void assertCanRead(ApplicationFlow flow, UserPrincipal p) {
        if ("SUPER_ADMIN".equalsIgnoreCase(p.role())) return;
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && p.id().equals(flow.getOwnerConsultantId())) return;
        throw BusinessException.forbidden("无权访问该申请流程");
    }

    private ApplicationFlowDtos.Response toResponse(ApplicationFlow flow) {
        Map<Long, List<ApplicationFlowAttachment>> byStep = attachments.findByFlowIdAndDeletedFalseOrderByCreatedAtDesc(flow.getId()).stream().collect(Collectors.groupingBy(ApplicationFlowAttachment::getStepId));
        List<ApplicationFlowDtos.StepResponse> stepResponses = steps.findByFlowIdOrderByOrderNoAsc(flow.getId()).stream()
                .sorted(Comparator.comparing(ApplicationFlowStep::getOrderNo))
                .map(s -> stepResponse(s, byStep.getOrDefault(s.getId(), List.of())))
                .toList();
        return new ApplicationFlowDtos.Response(flow.getId(), flow.getStudentProfileId(), flow.getStudentNo(), flow.getStudentName(), flow.getOwnerConsultantId(), flow.getOwnerConsultantName(), flow.getCurrentStep(), flow.getProgressPercent(), flow.getCompleted(), flow.getRemark(), flow.getCreatedAt(), flow.getUpdatedAt(), flow.getVersion(), stepResponses);
    }

    private ApplicationFlowDtos.StepResponse stepResponse(ApplicationFlowStep s, List<ApplicationFlowAttachment> files) {
        return new ApplicationFlowDtos.StepResponse(s.getId(), s.getStepCode(), s.getOrderNo(), s.getStepName(), s.getStatus(), s.getRequired(), s.getUploadedFileCount(), s.getConsultantNote(), s.getCustomerVisibleNote(), s.getStartedAt(), s.getCompletedAt(), s.getVersion(), files.stream().map(this::attachmentResponse).toList());
    }

    private ApplicationFlowDtos.AttachmentResponse attachmentResponse(ApplicationFlowAttachment a) {
        return new ApplicationFlowDtos.AttachmentResponse(a.getId(), a.getAttachmentType(), a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(), a.getFileUrl(), a.getNote(), a.getUploadedByName(), a.getCreatedAt());
    }

    private String clean(String value) { if (value == null) return null; String v = value.trim(); return v.isEmpty() ? null : v; }
    private boolean contains(String value, String keyword) { return value != null && value.contains(keyword); }
}
