package com.treeeducation.ioas.lead;

import com.treeeducation.ioas.audit.*;
import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.notification.NotificationDtos;
import com.treeeducation.ioas.notification.NotificationService;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfile;
import com.treeeducation.ioas.system.operatorprofile.OperatorProfileRepository;
import com.treeeducation.ioas.system.user.UserRepository;
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
    private final UserRepository users;
    private final TaskService tasks;
    private final AuditLogRepository audits;
    private final NotificationService notifications;

    public LeadService(LeadRepository repo, ContentPackageRepository packages, OperatorProfileRepository operators,
                       UserRepository users, TaskService tasks, AuditLogRepository audits, NotificationService notifications) {
        this.repo = repo;
        this.packages = packages;
        this.operators = operators;
        this.users = users;
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
        String rawDestination = clean(r.destination());
        String regionCode = resolveRegionCode(rawDestination);
        String regionName = resolveRegionName(regionCode);
        OperatorProfile advisor = pickAdvisor(rawDestination).orElse(null);

        Lead lead = new Lead();
        lead.setLeadNo("WEB" + System.currentTimeMillis());
        lead.setSourceType("official_website");
        lead.setStudentName(clean(r.name()));
        lead.setPhone(clean(r.phone()));
        lead.setWechat(clean(r.wechat()));
        lead.setSourceChannel(clean(r.source()) == null ? "official_website_one_minute_consultation" : clean(r.source()));
        lead.setSourcePage(clean(sourcePage));
        lead.setTargetCountry(rawDestination);
        lead.setIntentionRegionCode(regionCode);
        lead.setIntentionRegionName(regionName);
        lead.setBudget(clean(r.budget()));
        lead.setDegreeLevel(clean(r.education()));
        lead.setRemark(buildOfficialWebsiteRemark(r, userAgent, regionName));
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
            lead.setAssignMode(matchAdvisor(advisor, regionName) ? "region_match" : "fallback_enabled_consultant");
            lead.setAssignReason(buildAssignReason(advisor, regionName, rawDestination));
            lead.setAssignedAt(Instant.now());
            lead.setNotifyStatus("notified");
        }

        lead = repo.save(lead);

        if (advisor != null) {
            tasks.createOfficialWebsiteLeadNotificationTask(
                    lead.getId(), lead.getStudentName(), lead.getIntentionRegionName(), advisor.getUserId(), advisor.getName()
            );
            notifications.sendToUser(new NotificationDtos.SendRequest(
                    advisor.getUserId(),
                    "CONSULTANT",
                    "官网1分钟咨询新线索",
                    "你收到一条官网1分钟咨询线索：" + safe(lead.getStudentName())
                            + "，意向区域：" + safe(lead.getIntentionRegionName())
                            + "，电话：" + safe(lead.getPhone())
                            + "。请尽快进入线索中心跟进。",
                    "lead",
                    lead.getId(),
                    "/leads/detail/" + lead.getId(),
                    "LEAD_ASSIGNED",
                    10
            ));
        }

        audit(AuditAction.create_lead, "lead", lead.getId(), 0L,
                "官网1分钟咨询提交：" + lead.getStudentName() + "，意向区域=" + safe(lead.getIntentionRegionName()) + "，顾问=" + safe(lead.getAssignedToName()));
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
        assertCanRead(l, p);
        return LeadDtos.of(l, packageName(l.getRelatedPackageId()));
    }

    @Transactional
    public Lead updateStatus(Long id, LeadStatus status, UserPrincipal p) {
        Lead l = get(id);
        assertCanMutate(l, p);
        l.setStatus(status);
        l.setUpdatedAt(Instant.now());
        audit(AuditAction.update_lead, "lead", id, p.id(), l.getLeadNo() + ":" + status);
        return l;
    }

    @Transactional
    public Lead update(Long id, LeadDtos.UpdateRequest r, UserPrincipal p) {
        Lead l = get(id);
        assertCanMutate(l, p);
        if (r.studentName() != null) l.setStudentName(r.studentName());
        if (r.phone() != null) l.setPhone(r.phone());
        if (r.wechat() != null) l.setWechat(r.wechat());
        if (r.targetCountry() != null) l.setTargetCountry(r.targetCountry());
        if (r.targetMajor() != null) l.setTargetMajor(r.targetMajor());
        if (r.budget() != null) l.setBudget(r.budget());
        if (r.degreeLevel() != null) l.setDegreeLevel(r.degreeLevel());
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

    @Transactional
    public void delete(Long id, UserPrincipal p) {
        Lead lead = get(id);
        assertCanMutate(lead, p);
        repo.delete(lead);
        audit(AuditAction.update_lead, "lead", id, p.id(), "delete:" + safe(lead.getLeadNo()));
    }

    private void assertCanRead(Lead lead, UserPrincipal p) {
        if (p == null) throw BusinessException.forbidden("请先登录");
        if ("SUPER_ADMIN".equalsIgnoreCase(p.role())) return;
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && p.id().equals(lead.getAssignedTo())) return;
        if ("OPERATOR".equalsIgnoreCase(p.role()) && (p.id().equals(lead.getOperatorId()) || p.id().equals(lead.getAssignedTo()))) return;
        if ("MEDIA".equalsIgnoreCase(p.role())) return;
        throw BusinessException.forbidden("无权查看该线索");
    }

    private void assertCanMutate(Lead lead, UserPrincipal p) {
        if (p == null) throw BusinessException.forbidden("请先登录");
        if ("SUPER_ADMIN".equalsIgnoreCase(p.role())) return;
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && p.id().equals(lead.getAssignedTo())) return;
        if ("OPERATOR".equalsIgnoreCase(p.role()) && (p.id().equals(lead.getOperatorId()) || p.id().equals(lead.getAssignedTo()))) return;
        throw BusinessException.forbidden("无权操作该线索");
    }

    private Optional<OperatorProfile> pickAdvisor(String destination) {
        String regionName = resolveRegionName(resolveRegionCode(destination));
        List<OperatorProfile> enabledConsultants = operators.findByEnabledTrueOrderByNameAsc().stream()
                .filter(this::isActiveConsultantProfile)
                .toList();
        if (enabledConsultants.isEmpty()) return Optional.empty();
        return enabledConsultants.stream()
                .filter(operator -> matchAdvisor(operator, regionName) || matchAdvisor(operator, destination))
                .findFirst()
                .or(() -> enabledConsultants.stream().findFirst());
    }

    private boolean isActiveConsultantProfile(OperatorProfile profile) {
        if (profile == null || profile.getUserId() == null || !Boolean.TRUE.equals(profile.getEnabled())) return false;
        return users.findById(profile.getUserId())
                .filter(user -> "CONSULTANT".equalsIgnoreCase(user.getRoleCode()))
                .filter(user -> user.getStatus() != null && "ACTIVE".equalsIgnoreCase(user.getStatus().name()))
                .isPresent();
    }

    private boolean matchAdvisor(OperatorProfile operator, String destination) {
        if (operator == null || destination == null || destination.isBlank()) return false;
        String keyword = destination.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(operator.getTeamName(), keyword) || containsIgnoreCase(operator.getName(), keyword);
    }

    private String resolveRegionCode(String destination) {
        String value = clean(destination);
        if (value == null) return "OTHER";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("英国") || lower.contains("uk") || lower.contains("伦敦") || lower.contains("曼彻斯特")) return "UK";
        if (lower.contains("美国") || lower.contains("usa") || lower.contains("us") || lower.contains("纽约") || lower.contains("加州") || lower.contains("波士顿")) return "US";
        if (lower.contains("澳洲") || lower.contains("澳大利亚") || lower.contains("新西兰") || lower.contains("悉尼") || lower.contains("墨尔本")) return "AUSTRALIA";
        if (lower.contains("欧洲") || lower.contains("法国") || lower.contains("德国") || lower.contains("荷兰") || lower.contains("瑞士") || lower.contains("爱尔兰") || lower.contains("西班牙") || lower.contains("意大利") || lower.contains("北欧")) return "EUROPE";
        return "OTHER";
    }

    private String resolveRegionName(String regionCode) {
        if ("UK".equalsIgnoreCase(regionCode)) return "英国";
        if ("US".equalsIgnoreCase(regionCode)) return "美国";
        if ("AUSTRALIA".equalsIgnoreCase(regionCode)) return "澳洲";
        if ("EUROPE".equalsIgnoreCase(regionCode)) return "欧洲";
        return "其他";
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String buildAssignReason(OperatorProfile advisor, String regionName, String rawDestination) {
        if (matchAdvisor(advisor, regionName)) {
            return "按官网1分钟咨询意向区域[" + safe(regionName) + "]匹配顾问[" + safe(advisor.getName()) + "]";
        }
        return "官网1分钟咨询意向[" + safe(rawDestination) + "]归类为[" + safe(regionName) + "]，未找到明确地区匹配顾问，自动分配给启用顾问[" + safe(advisor.getName()) + "]，请后台确认";
    }

    private String buildOfficialWebsiteRemark(LeadDtos.OfficialWebsiteRequest r, String userAgent, String regionName) {
        StringBuilder remark = new StringBuilder();
        appendLine(remark, "年龄", clean(r.age()));
        appendLine(remark, "所在城市", clean(r.city()));
        appendLine(remark, "学历", clean(r.education()));
        appendLine(remark, "原始意向", clean(r.destination()));
        appendLine(remark, "意向区域", clean(regionName));
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
