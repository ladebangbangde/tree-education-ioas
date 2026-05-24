package com.treeeducation.ioas.lead;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.notification.NotificationService;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfile;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Lead application service aligned with the frontend lead center. */
@Service
public class LeadService {
    private final LeadRepository repo;
    private final ContentPackageRepository packages;
    private final OperatorProfileRepository operators;
    private final TaskService tasks;
    private final AuditLogRepository audits;
    private final NotificationService notifications;

    public LeadService(LeadRepository repo, ContentPackageRepository packages, OperatorProfileRepository operators,
                       TaskService tasks, AuditLogRepository audits, NotificationService notifications) {
        this.repo = repo;
        this.packages = packages;
        this.operators = operators;
        this.tasks = tasks;
        this.audits = audits;
        this.notifications = notifications;
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
                ? (taskAssignee == null ? null : operators.findById(taskAssignee).map(OperatorProfile::getName).orElse(null))
                : l.getAssignedToName();
        tasks.createOperatorLeadTask(cp.getId(), l.getId(), taskAssignee, taskAssigneeName);
        audit(AuditAction.create_lead, "lead", l.getId(), p.id(), l.getStudentName());
        return l;
    }

    @Transactional
    public Lead createOfficialWebsiteLead(LeadDtos.OfficialWebsiteRequest r, String sourcePage, String userAgent) {
        OperatorProfile advisor = pickAdvisor(clean(r.destination())).orElse(null);

        Lead lead = new Lead();
        lead.setLeadNo("WEB" + System.currentTimeMillis());
        lead.setSourceType("official_website");
        lead.setStudentName(clean(r.name()));
        lead.setPhone(clean(r.phone()));
        lead.setWechat(clean(r.wechat()));
        lead.setSourceChannel(clean(r.source()) == null ? "official_website_one_minute_consultation" : clean(r.source()));
        lead.setSourcePage(clean(sourcePage));
        lead.setTargetCountry(clean(r.destination()));
        lead.setIntentionRegionCode(clean(r.destination()));
        lead.setIntentionRegionName(clean(r.destination()));
        lead.setBudget(clean(r.budget()));
        lead.setDegreeLevel(clean(r.education()));
        lead.setRemark(buildOfficialWebsiteRemark(r, userAgent));
        lead.setUpdatedAt(Instant.now());

        if (advisor == null) {
            lead.setStatus(LeadStatus.unassigned);
            lead.setAssignMode("manual_required");
            lead.setAssignReason("官网1分钟咨询线索已创建，但当前没有启用顾问，需要后台手动分配");
            lead.setNotifyStatus("pending_manual_assign");
        } else {
            lead.setStatus(LeadStatus.assigned);
            lead.setAssignedTo(advisor.getUserId());
            lead.setAssignedToName(advisor.getName());
            lead.setOperatorId(advisor.getId());
            lead.setAssignMode(matchAdvisor(advisor, clean(r.destination())) ? "region_match" : "fallback_enabled_advisor");
            lead.setAssignReason(buildAssignReason(advisor, clean(r.destination())));
            lead.setAssignedAt(Instant.now());
            lead.setNotifyStatus("notified");
        }

        lead = repo.save(lead);

        if (advisor != null) {
            tasks.createOfficialWebsiteLeadNotificationTask(
                    lead.getId(), lead.getStudentName(), lead.getTargetCountry(), advisor.getUserId(), advisor.getName()
            );
            notifications.createLeadAssignedNotification(
                    advisor.getUserId(), advisor.getName(), lead.getId(), lead.getStudentName(), lead.getTargetCountry(), lead.getPhone()
            );
        }

        audit(AuditAction.create_lead, "lead", lead.getId(), 0L,
                "官网1分钟咨询提交：" + lead.getStudentName() + "，意向=" + safe(lead.getTargetCountry()) + "，顾问=" + safe(lead.getAssignedToName()));
        return lead;
    }

    public List<LeadDtos.Response> list(String tab, String keyword, Long relatedPackageId, Long operatorId, UserPrincipal p) {
        boolean consultant = "CONSULTANT".equalsIgnoreCase(p.role());
        return repo.findAll().stream()
                .filter(l -> !consultant || p.id().equals(l.getAssignedTo()))
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

    public LeadDtos.Response detailForUser(Long id, UserPrincipal p) {
        Lead l = get(id);
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && !p.id().equals(l.getAssignedTo())) {
            throw BusinessException.forbidden("无权查看该线索");
        }
        return LeadDtos.of(l, packageName(l.getRelatedPackageId()));
    }

    @Transactional
    public Lead updateStatus(Long id, LeadStatus status, UserPrincipal p) {
        Lead l = get(id);
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && !p.id().equals(l.getAssignedTo())) {
            throw BusinessException.forbidden("无权更新该线索");
        }
        l.setStatus(status);
        l.setUpdatedAt(Instant.now());
        audit(AuditAction.update_lead, "lead", id, p.id(), l.getLeadNo() + ":" + status);
        return l;
    }

    @Transactional
    public Lead update(Long id, LeadDtos.UpdateRequest r, UserPrincipal p) {
        Lead l = get(id);
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && !p.id().equals(l.getAssignedTo())) {
            throw BusinessException.forbidden("无权更新该线索");
        }
        if (r.remark() != null) l.setRemark(r.remark());
        if (!"CONSULTANT".equalsIgnoreCase(p.role())) {
            if (r.assignedTo() != null) l.setAssignedTo(r.assignedTo());
            if (r.assignedToName() != null) l.setAssignedToName(r.assignedToName());
        }
        if (r.status() != null) l.setStatus(r.status());
        l.setUpdatedAt(Instant.now());
        audit(AuditAction.update_lead, "lead", id, p.id(), l.getLeadNo());
        return l;
    }

    private Optional<OperatorProfile> pickAdvisor(String destination) {
        List<OperatorProfile> enabled = operators.findByEnabledTrueOrderByNameAsc();
        if (enabled.isEmpty()) return Optional.empty();
        return enabled.stream()
                .filter(operator -> matchAdvisor(operator, destination))
                .findFirst()
                .or(() -> enabled.stream().findFirst());
    }

    private boolean matchAdvisor(OperatorProfile operator, String destination) {
        if (operator == null || destination == null || destination.isBlank()) return false;
        String keyword = destination.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(operator.getTeamName(), keyword) || containsIgnoreCase(operator.getName(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String buildAssignReason(OperatorProfile advisor, String destination) {
        if (matchAdvisor(advisor, destination)) {
            return "按官网1分钟咨询意向地区[" + safe(destination) + "]匹配顾问[" + safe(advisor.getName()) + "]";
        }
        return "未找到明确地区匹配顾问，自动分配给启用顾问[" + safe(advisor.getName()) + "]，请后台确认";
    }

    private String buildOfficialWebsiteRemark(LeadDtos.OfficialWebsiteRequest r, String userAgent) {
        StringBuilder remark = new StringBuilder();
        appendLine(remark, "年龄", clean(r.age()));
        appendLine(remark, "所在城市", clean(r.city()));
        appendLine(remark, "学历", clean(r.education()));
        appendLine(remark, "意向国家/地区", clean(r.destination()));
        appendLine(remark, "预算", clean(r.budget()));
        appendLine(remark, "官网备注", clean(r.remark()));
        appendLine(remark, "浏览器", clean(userAgent));
        return remark.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) return;
        builder.append(label).append("：").append(value).append("\n");
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
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