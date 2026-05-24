package com.treeeducation.ioas.consultation;

import com.treeeducation.ioas.consultant.ConsultantProfile;
import com.treeeducation.ioas.consultant.ConsultantRegion;
import com.treeeducation.ioas.lead.*;
import com.treeeducation.ioas.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service("consultationModulePublicConsultationService")
public class PublicConsultationService {
    private final LeadRepository leads;
    private final ConsultantAssignmentService assignmentService;
    private final NotificationService notifications;

    public PublicConsultationService(LeadRepository leads, ConsultantAssignmentService assignmentService,
                                     NotificationService notifications) {
        this.leads = leads;
        this.assignmentService = assignmentService;
        this.notifications = notifications;
    }

    @Transactional
    public PublicConsultationDtos.SubmitResponse submit(PublicConsultationDtos.SubmitRequest request) {
        ConsultantAssignmentService.AssignmentResult assignment = assignmentService.assign(request.intentionRegionCode());
        ConsultantRegion region = assignment.region();
        ConsultantProfile consultant = assignment.consultant();

        Lead lead = new Lead();
        lead.setLeadNo("LD" + System.currentTimeMillis());
        lead.setSourceType("official_website_consultation");
        lead.setStudentName(request.studentName());
        lead.setPhone(request.phone());
        lead.setWechat(request.wechat());
        lead.setSourceChannel(request.sourceChannel() == null ? "official_website" : request.sourceChannel());
        lead.setSourcePage(request.sourcePage());
        lead.setTargetCountry(region.getRegionName());
        lead.setTargetMajor(request.targetMajor());
        lead.setBudget(request.budget());
        lead.setDegreeLevel(request.degreeLevel());
        lead.setRemark(request.message());
        lead.setIntentionRegionId(region.getId());
        lead.setIntentionRegionCode(region.getRegionCode());
        lead.setIntentionRegionName(region.getRegionName());
        lead.setAssignMode(assignment.mode());
        lead.setAssignReason(assignment.reason());
        lead.setAssignedAt(Instant.now());
        lead.setAssignedTo(consultant.getUserId());
        lead.setAssignedToName(consultant.getConsultantName());
        lead.setStatus(LeadStatus.assigned);
        lead.setNotifyStatus("PENDING");
        lead = leads.save(lead);

        try {
            notifications.create(consultant.getUserId(), "CONSULTANT", "你有一条新的官网咨询线索",
                    "客户：" + lead.getStudentName() + "；意向区域：" + region.getRegionName() + "；请尽快跟进。",
                    "LEAD_ASSIGNED", lead.getId(), "INFO", 10);
            lead.setNotifyStatus("SENT");
        } catch (RuntimeException ex) {
            lead.setNotifyStatus("FAILED");
        }
        lead = leads.save(lead);

        return new PublicConsultationDtos.SubmitResponse(lead.getId(), lead.getLeadNo(), lead.getStatus().name(),
                lead.getAssignedTo(), lead.getAssignedToName(), lead.getIntentionRegionCode(), lead.getIntentionRegionName(),
                lead.getAssignMode(), lead.getAssignReason(), lead.getNotifyStatus(), lead.getCreatedAt());
    }
}