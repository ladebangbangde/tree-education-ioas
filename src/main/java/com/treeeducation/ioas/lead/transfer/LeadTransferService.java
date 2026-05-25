package com.treeeducation.ioas.lead.transfer;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.lead.Lead;
import com.treeeducation.ioas.lead.LeadRepository;
import com.treeeducation.ioas.notification.NotificationDtos;
import com.treeeducation.ioas.notification.NotificationService;
import com.treeeducation.ioas.system.user.User;
import com.treeeducation.ioas.system.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LeadTransferService {
    private final LeadTransferRequestRepository transfers;
    private final LeadRepository leads;
    private final UserRepository users;
    private final NotificationService notifications;

    public LeadTransferService(LeadTransferRequestRepository transfers, LeadRepository leads,
                               UserRepository users, NotificationService notifications) {
        this.transfers = transfers;
        this.leads = leads;
        this.users = users;
        this.notifications = notifications;
    }

    public List<LeadTransferDtos.ConsultantOption> consultants(UserPrincipal p) {
        boolean admin = "SUPER_ADMIN".equalsIgnoreCase(p.role());
        boolean consultant = "CONSULTANT".equalsIgnoreCase(p.role());
        if (!admin && !consultant) throw BusinessException.forbidden("无权查看顾问列表");
        return users.findAll().stream()
                .filter(this::isActiveConsultant)
                .filter(user -> !consultant || !p.id().equals(user.getId()))
                .map(user -> new LeadTransferDtos.ConsultantOption(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
    }

    public List<LeadTransferDtos.Response> mine(String scope, UserPrincipal p) {
        if (!"CONSULTANT".equalsIgnoreCase(p.role()) && !"SUPER_ADMIN".equalsIgnoreCase(p.role())) {
            throw BusinessException.forbidden("无权查看转让申请");
        }
        List<LeadTransferRequest> rows;
        if ("sent".equalsIgnoreCase(scope)) rows = transfers.findByFromConsultantIdOrderByRequestedAtDesc(p.id());
        else rows = transfers.findByToConsultantIdOrderByRequestedAtDesc(p.id());
        return rows.stream().map(LeadTransferDtos.Response::of).toList();
    }

    @Transactional
    public LeadTransferDtos.Response create(Long leadId, LeadTransferDtos.CreateRequest request, UserPrincipal p) {
        if (!"CONSULTANT".equalsIgnoreCase(p.role())) throw BusinessException.forbidden("只有顾问可以发起线索转让");
        Lead lead = leads.findById(leadId).orElseThrow(() -> BusinessException.notFound("线索不存在"));
        if (!p.id().equals(lead.getAssignedTo())) throw BusinessException.forbidden("只能转让自己名下的线索");
        if (request.toConsultantId() == null || request.toConsultantId().equals(p.id())) throw BusinessException.badRequest("请选择其他顾问");
        User target = users.findById(request.toConsultantId()).filter(this::isActiveConsultant)
                .orElseThrow(() -> BusinessException.badRequest("目标顾问不存在或已停用"));
        if (transfers.existsByLeadIdAndStatus(leadId, LeadTransferStatus.PENDING)) {
            throw BusinessException.badRequest("该线索已有待确认的转让申请");
        }

        LeadTransferRequest row = new LeadTransferRequest();
        row.setLeadId(lead.getId());
        row.setLeadNo(lead.getLeadNo());
        row.setStudentName(lead.getStudentName());
        row.setFromConsultantId(p.id());
        row.setFromConsultantName(p.userName());
        row.setToConsultantId(target.getId());
        row.setToConsultantName(target.getDisplayName());
        row.setReason(clean(request.reason()));
        row.setStatus(LeadTransferStatus.PENDING);
        row.setRequestedAt(Instant.now());
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        row = transfers.save(row);

        notifications.sendToUser(new NotificationDtos.SendRequest(
                target.getId(), "CONSULTANT", "线索转让待确认",
                "顾问[" + safe(p.userName()) + "]请求将线索[" + safe(lead.getStudentName()) + "]转让给你，请确认是否接收。",
                "lead_transfer", row.getId(), "/leads/list", "LEAD_TRANSFER_PENDING", 20));
        return LeadTransferDtos.Response.of(row);
    }

    @Transactional
    public LeadTransferDtos.Response accept(Long id, LeadTransferDtos.RespondRequest request, UserPrincipal p) {
        LeadTransferRequest row = getPendingForTarget(id, p);
        Lead lead = leads.findById(row.getLeadId()).orElseThrow(() -> BusinessException.notFound("线索不存在"));
        if (!row.getFromConsultantId().equals(lead.getAssignedTo())) {
            row.setStatus(LeadTransferStatus.CANCELLED);
            row.setRespondedAt(Instant.now());
            row.setResponseRemark("线索归属已变化，系统自动取消该转让申请");
            row.setUpdatedAt(Instant.now());
            transfers.save(row);
            throw BusinessException.badRequest("线索当前已不在原顾问名下，转让申请已自动取消");
        }
        lead.setAssignedTo(row.getToConsultantId());
        lead.setAssignedToName(row.getToConsultantName());
        lead.setAssignedAt(Instant.now());
        lead.setAssignMode("consultant_transfer_accepted");
        lead.setAssignReason("顾问转让已被接收：" + safe(row.getFromConsultantName()) + " -> " + safe(row.getToConsultantName()));
        lead.setUpdatedAt(Instant.now());
        leads.save(lead);

        row.setStatus(LeadTransferStatus.ACCEPTED);
        row.setRespondedAt(Instant.now());
        row.setResponseRemark(clean(request == null ? null : request.remark()));
        row.setUpdatedAt(Instant.now());
        row = transfers.save(row);

        notifications.sendToUser(new NotificationDtos.SendRequest(
                row.getFromConsultantId(), "CONSULTANT", "线索转让已同意",
                "顾问[" + safe(row.getToConsultantName()) + "]已接收线索[" + safe(row.getStudentName()) + "]。",
                "lead_transfer", row.getId(), "/leads/list", "LEAD_TRANSFER_ACCEPTED", 20));
        return LeadTransferDtos.Response.of(row);
    }

    @Transactional
    public LeadTransferDtos.Response reject(Long id, LeadTransferDtos.RespondRequest request, UserPrincipal p) {
        LeadTransferRequest row = getPendingForTarget(id, p);
        row.setStatus(LeadTransferStatus.REJECTED);
        row.setRespondedAt(Instant.now());
        row.setResponseRemark(clean(request == null ? null : request.remark()));
        row.setUpdatedAt(Instant.now());
        row = transfers.save(row);
        notifications.sendToUser(new NotificationDtos.SendRequest(
                row.getFromConsultantId(), "CONSULTANT", "线索转让已拒绝",
                "顾问[" + safe(row.getToConsultantName()) + "]拒绝接收线索[" + safe(row.getStudentName()) + "]，线索仍保留在你名下。",
                "lead_transfer", row.getId(), "/leads/list", "LEAD_TRANSFER_REJECTED", 20));
        return LeadTransferDtos.Response.of(row);
    }

    private LeadTransferRequest getPendingForTarget(Long id, UserPrincipal p) {
        if (!"CONSULTANT".equalsIgnoreCase(p.role())) throw BusinessException.forbidden("只有目标顾问可以确认转让");
        LeadTransferRequest row = transfers.findById(id).orElseThrow(() -> BusinessException.notFound("转让申请不存在"));
        if (!p.id().equals(row.getToConsultantId())) throw BusinessException.forbidden("无权处理该转让申请");
        if (row.getStatus() != LeadTransferStatus.PENDING) throw BusinessException.badRequest("该转让申请已处理");
        return row;
    }

    private boolean isActiveConsultant(User user) {
        return user != null && "CONSULTANT".equalsIgnoreCase(user.getRoleCode()) && user.getStatus() != null && "ACTIVE".equalsIgnoreCase(user.getStatus().name());
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String safe(String value) { return value == null || value.isBlank() ? "未填写" : value.trim(); }
}
