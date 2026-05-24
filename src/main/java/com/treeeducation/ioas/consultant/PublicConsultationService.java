package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.lead.Lead;
import com.treeeducation.ioas.lead.LeadRepository;
import com.treeeducation.ioas.lead.LeadStatus;
import com.treeeducation.ioas.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service("consultantModulePublicConsultationService")
public class PublicConsultationService {
    private final ConsultantRegionRepository regions;
    private final ConsultantAssignmentService assignmentService;
    private final LeadRepository leads;
    private final NotificationService notifications;

    public PublicConsultationService(ConsultantRegionRepository regions,
                                     ConsultantAssignmentService assignmentService,
                                     LeadRepository leads,
                                     NotificationService notifications) {
        this.regions = regions;
        this.assignmentService = assignmentService;
        this.leads = leads;
        this.notifications = notifications;
    }

    public ConsultationDtos.OptionsResponse options() {
        List<ConsultationDtos.RegionOption> options = regions.activeOptions().stream()
                .map(r -> new ConsultationDtos.RegionOption(r.getId(), r.getRegionCode(), r.getRegionName(), r.getRegionType()))
                .toList();
        return new ConsultationDtos.OptionsResponse(options);
    }

    @Transactional
    public ConsultationDtos.CreateResponse create(ConsultationDtos.CreateRequest request) {
        ConsultantAssignmentService.AssignmentResult assignment = assignmentService.assign(request.intentionRegionCode());
        Lead lead = new Lead();
        lead.setLeadNo("LD" + System.currentTimeMillis());
        lead.setSourceType("official_website_consultation");
        lead.setStudentName(request.studentName());
        lead.setPhone(request.phone());
        lead.setWechat(request.wechat());
        lead.setSourceChannel(request.sourceChannel() == null ? "official_website" : request.sourceChannel());
        lead.setSourcePage(request.sourcePage());
        lead.setTargetCountry(assignment.region().getRegionName());
        lead.setTargetMajor(request.targetMajor());
        lead.setBudget(request.budget());
        lead.setDegreeLevel(request.degreeLevel());
        lead.setIntentionRegionId(assignment.region().getId());
        lead.setIntentionRegionCode(assignment.region().getRegionCode());
        lead.setIntentionRegionName(assignment.region().getRegionName());
        lead.setAssignMode(assignment.assignMode());
        lead.setAssignReason(assignment.assignReason());
        lead.setAssignedAt(assignment.consultant() == null ? null : Instant.now());
        lead.setNotifyStatus("PENDING");
        lead.setRemark(request.message());
        if (assignment.consultant() == null) {
            lead.setStatus(LeadStatus.unassigned);
        } else {
            lead.setStatus(LeadStatus.assigned);
            lead.setAssignedTo(assignment.consultant().getUserId());
            lead.setAssignedToName(assignment.consultant().getConsultantName());
        }
        lead = leads.save(lead);

        if (assignment.consultant() != null) {
            notifications.create(
                    assignment.consultant().getUserId(),
                    "CONSULTANT",
                    "你有一条新的官网咨询线索",
                    "客户：" + request.studentName() + "，意向区域：" + assignment.region().getRegionName() + "，请尽快跟进。",
                    "LEAD_ASSIGNED",
                    lead.getId(),
                    "INFO",
                    10
            );
            lead.setNotifyStatus("SENT");
            lead = leads.save(lead);
        }

        return new ConsultationDtos.CreateResponse(lead.getId(), lead.getLeadNo(), lead.getStatus().name(),
                lead.getAssignedToName(), lead.getAssignMode(), lead.getAssignReason());
    }
}