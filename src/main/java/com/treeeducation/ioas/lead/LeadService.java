package com.treeeducation.ioas.lead;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Lead application service aligned with the frontend lead center. */
@Service
public class LeadService {
    private final LeadRepository repo;
    private final ContentPackageRepository packages;
    private final OperatorProfileRepository operators;
    private final TaskService tasks;
    private final AuditLogRepository audits;

    public LeadService(LeadRepository repo, ContentPackageRepository packages, OperatorProfileRepository operators,
                       TaskService tasks, AuditLogRepository audits) {
        this.repo = repo;
        this.packages = packages;
        this.operators = operators;
        this.tasks = tasks;
        this.audits = audits;
    }

    @Transactional
    public Lead create(LeadDtos.CreateRequest r, UserPrincipal p) {
        ContentPackage cp = packages.findById(r.relatedPackageId()).orElseThrow(() -> BusinessException.notFound("主题包不存在"));
        Lead l = new Lead();
        l.setLeadNo("LD" + System.currentTimeMillis());
        l.setSourceType(r.sourceType() == null ? "content_package" : r.sourceType());
        l.setRelatedPackageId(cp.getId());
        l.setOperatorId(r.operatorId() == null ? cp.getOperatorId() : r.operatorId());
        l.setStudentName(r.studentName());
        l.setPhone(r.phone());
        l.setWechat(r.wechat());
        l.setSourceChannel(r.sourceChannel());
        l.setTargetCountry(r.targetCountry());
        l.setTargetMajor(r.targetMajor());
        l.setBudget(r.budget());
        l.setDegreeLevel(r.degreeLevel());
        l.setAssignedTo(r.assignedTo());
        l.setAssignedToName(r.assignedToName());
        l.setRemark(r.remark());
        l.setStatus(r.status() == null ? LeadStatus.unassigned : r.status());
        l = repo.save(l);
        Long taskAssignee = l.getAssignedTo() == null ? l.getOperatorId() : l.getAssignedTo();
        String taskAssigneeName = l.getAssignedToName() == null
                ? (taskAssignee == null ? null : operators.findById(taskAssignee).map(o -> o.getName()).orElse(null))
                : l.getAssignedToName();
        tasks.createOperatorLeadTask(cp.getId(), l.getId(), taskAssignee, taskAssigneeName);
        audit(AuditAction.create_lead, "lead", l.getId(), p.id(), l.getStudentName());
        return l;
    }

    public List<LeadDtos.Response> list(String tab, String keyword, Long relatedPackageId, Long operatorId, UserPrincipal p) {
        return repo.findAll().stream()
                .filter(l -> relatedPackageId == null || relatedPackageId.equals(l.getRelatedPackageId()))
                .filter(l -> operatorId == null || operatorId.equals(l.getOperatorId()))
                .filter(l -> keyword == null || l.getStudentName().contains(keyword) || (l.getPhone() != null && l.getPhone().contains(keyword)) || (l.getLeadNo() != null && l.getLeadNo().contains(keyword)))
                .filter(l -> !"unassigned".equals(tab) || l.getStatus() == LeadStatus.unassigned)
                .filter(l -> !"assigned".equals(tab) || l.getStatus() == LeadStatus.assigned || l.getStatus() == LeadStatus.following)
                .filter(l -> !"mine".equals(tab) || p.id().equals(l.getAssignedTo()) || p.id().equals(l.getOperatorId()))
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(l -> LeadDtos.of(l, packageName(l.getRelatedPackageId())))
                .toList();
    }

    public Lead get(Long id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("线索不存在"));
    }

    public LeadDtos.Response detail(Long id) {
        Lead l = get(id);
        return LeadDtos.of(l, packageName(l.getRelatedPackageId()));
    }

    @Transactional
    public Lead updateStatus(Long id, LeadStatus status, UserPrincipal p) {
        Lead l = get(id);
        l.setStatus(status);
        l.setUpdatedAt(Instant.now());
        audit(AuditAction.update_lead, "lead", id, p.id(), l.getLeadNo() + ":" + status);
        return l;
    }

    @Transactional
    public Lead update(Long id, LeadDtos.UpdateRequest r, UserPrincipal p) {
        Lead l = get(id);
        if (r.remark() != null) l.setRemark(r.remark());
        if (r.assignedTo() != null) l.setAssignedTo(r.assignedTo());
        if (r.assignedToName() != null) l.setAssignedToName(r.assignedToName());
        if (r.status() != null) l.setStatus(r.status());
        l.setUpdatedAt(Instant.now());
        audit(AuditAction.update_lead, "lead", id, p.id(), l.getLeadNo());
        return l;
    }

    private String packageName(Long packageId) {
        return packageId == null ? null : packages.findById(packageId).map(ContentPackage::getTopicName).orElse(null);
    }

    private void audit(AuditAction a, String t, Long tid, Long actor, String d) {
        AuditLog log = new AuditLog();
        log.setAction(a);
        log.setTargetType(t);
        log.setTargetId(tid);
        log.setActorId(actor);
        log.setDetail(d);
        audits.save(log);
    }
}
