package com.treeeducation.ioas.student;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.common.PageResponse;
import com.treeeducation.ioas.lead.Lead;
import com.treeeducation.ioas.lead.LeadRepository;
import com.treeeducation.ioas.lead.LeadStatus;
import com.treeeducation.ioas.lead.transfer.LeadTransferRequestRepository;
import com.treeeducation.ioas.lead.transfer.LeadTransferStatus;
import com.treeeducation.ioas.notification.NotificationDtos;
import com.treeeducation.ioas.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StudentProfileService {
    private final StudentProfileRepository students;
    private final LeadRepository leads;
    private final LeadTransferRequestRepository transfers;
    private final NotificationService notifications;

    public StudentProfileService(StudentProfileRepository students, LeadRepository leads,
                                 LeadTransferRequestRepository transfers, NotificationService notifications) {
        this.students = students;
        this.leads = leads;
        this.transfers = transfers;
        this.notifications = notifications;
    }

    public PageResponse<StudentProfileDtos.Response> list(String keyword, Long ownerConsultantId, String intentionRegionCode,
                                                          StudentProfileStatus profileStatus, int pageNum, int pageSize, UserPrincipal p) {
        boolean consultant = "CONSULTANT".equalsIgnoreCase(p.role());
        List<StudentProfile> all = consultant
                ? students.findByOwnerConsultantIdAndProfileStatusNotOrderByCreatedAtDesc(p.id(), StudentProfileStatus.DELETED)
                : students.findByProfileStatusNotOrderByCreatedAtDesc(StudentProfileStatus.DELETED);
        return PageResponse.of(all.stream()
                .filter(s -> ownerConsultantId == null || ownerConsultantId.equals(s.getOwnerConsultantId()))
                .filter(s -> profileStatus == null || profileStatus == s.getProfileStatus())
                .filter(s -> clean(intentionRegionCode) == null || clean(intentionRegionCode).equalsIgnoreCase(s.getIntentionRegionCode()))
                .filter(s -> matchesKeyword(s, keyword))
                .map(StudentProfileDtos.Response::of)
                .toList(), pageNum, pageSize);
    }

    public StudentProfileDtos.Response detail(Long id, UserPrincipal p) {
        return StudentProfileDtos.Response.of(getAndCheckRead(id, p));
    }

    @Transactional
    public StudentProfileDtos.Response convertFromLead(Long leadId, StudentProfileDtos.ConvertRequest request, UserPrincipal p) {
        Lead lead = leads.findById(leadId).orElseThrow(() -> BusinessException.notFound("线索不存在"));
        assertCanConvert(lead, p);
        if (transfers.existsByLeadIdAndStatus(leadId, LeadTransferStatus.PENDING)) {
            throw BusinessException.badRequest("该线索存在待确认转让申请，请先处理转让后再生成学生档案");
        }
        if (isBlank(lead.getPhone()) && isBlank(lead.getWechat())) {
            throw BusinessException.badRequest("手机号和微信至少需要填写一个，才能生成学生档案");
        }
        StudentProfile existing = students.findBySourceLeadId(leadId).orElse(null);
        if (existing != null) {
            markLeadConverted(lead, existing, p.id());
            return StudentProfileDtos.Response.of(existing);
        }
        StudentProfile student = buildFromLead(lead, request);
        student = students.save(student);
        markLeadConverted(lead, student, p.id());
        notifications.sendToUser(new NotificationDtos.SendRequest(
                lead.getAssignedTo(), "CONSULTANT", "学生档案已生成",
                "你已成功将线索[" + safe(lead.getStudentName()) + "]转为学生档案[" + student.getStudentNo() + "]。",
                "student_profile", student.getId(), "/students", "STUDENT_PROFILE_CREATED", 20));
        return StudentProfileDtos.Response.of(student);
    }

    @Transactional
    public StudentProfileDtos.Response update(Long id, StudentProfileDtos.UpdateRequest request, UserPrincipal p) {
        StudentProfile student = getAndCheckMutate(id, p);
        if (request.studentName() != null) student.setStudentName(request.studentName());
        if (request.phone() != null) student.setPhone(request.phone());
        if (request.wechat() != null) student.setWechat(request.wechat());
        if (request.age() != null) student.setAge(request.age());
        if (request.educationLevel() != null) student.setEducationLevel(request.educationLevel());
        if (request.province() != null) student.setProvince(request.province());
        if (request.city() != null) student.setCity(request.city());
        if (request.locationText() != null) student.setLocationText(request.locationText());
        if (request.targetCountry() != null) student.setTargetCountry(request.targetCountry());
        if (request.targetMajor() != null) student.setTargetMajor(request.targetMajor());
        if (request.budget() != null) student.setBudget(request.budget());
        if (request.profileStatus() != null) student.setProfileStatus(request.profileStatus());
        if (request.remark() != null) student.setRemark(request.remark());
        student.setUpdatedAt(Instant.now());
        return StudentProfileDtos.Response.of(students.save(student));
    }

    @Transactional
    public void remove(Long id, UserPrincipal p) {
        StudentProfile student = getAndCheckMutate(id, p);
        student.setProfileStatus(StudentProfileStatus.DELETED);
        student.setUpdatedAt(Instant.now());
        students.save(student);
    }

    private StudentProfile buildFromLead(Lead lead, StudentProfileDtos.ConvertRequest request) {
        StudentProfile s = new StudentProfile();
        s.setStudentNo(nextStudentNo());
        s.setSourceLeadId(lead.getId());
        s.setSourceLeadNo(lead.getLeadNo());
        s.setOwnerConsultantId(lead.getAssignedTo());
        s.setOwnerConsultantName(safe(lead.getAssignedToName()));
        s.setStudentName(lead.getStudentName());
        s.setPhone(lead.getPhone());
        s.setWechat(lead.getWechat());
        s.setAge(clean(request == null ? null : request.age()));
        s.setEducationLevel(firstNonBlank(request == null ? null : request.educationLevel(), lead.getDegreeLevel()));
        s.setProvince(clean(request == null ? null : request.province()));
        s.setCity(clean(request == null ? null : request.city()));
        s.setLocationText(clean(request == null ? null : request.locationText()));
        s.setIntentionRegionId(lead.getIntentionRegionId());
        s.setIntentionRegionCode(lead.getIntentionRegionCode());
        s.setIntentionRegionName(lead.getIntentionRegionName());
        s.setTargetCountry(lead.getTargetCountry());
        s.setTargetMajor(firstNonBlank(request == null ? null : request.targetMajor(), lead.getTargetMajor()));
        s.setBudget(firstNonBlank(request == null ? null : request.budget(), lead.getBudget()));
        s.setProfileStatus(StudentProfileStatus.ACTIVE);
        s.setRemark(buildRemark(lead, request));
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }

    private void markLeadConverted(Lead lead, StudentProfile student, Long actorId) {
        lead.setStatus(LeadStatus.converted);
        lead.setConvertedStudentId(student.getId());
        lead.setConvertedAt(Instant.now());
        lead.setConvertedBy(actorId);
        lead.setUpdatedAt(Instant.now());
        leads.save(lead);
    }

    private StudentProfile getAndCheckRead(Long id, UserPrincipal p) {
        StudentProfile student = students.findById(id).orElseThrow(() -> BusinessException.notFound("学生档案不存在"));
        if ("SUPER_ADMIN".equalsIgnoreCase(p.role())) return student;
        if ("CONSULTANT".equalsIgnoreCase(p.role()) && p.id().equals(student.getOwnerConsultantId())) return student;
        throw BusinessException.forbidden("无权查看该学生档案");
    }

    private StudentProfile getAndCheckMutate(Long id, UserPrincipal p) {
        StudentProfile student = getAndCheckRead(id, p);
        if (student.getProfileStatus() == StudentProfileStatus.DELETED) throw BusinessException.badRequest("学生档案已删除");
        return student;
    }

    private void assertCanConvert(Lead lead, UserPrincipal p) {
        if (!"SUPER_ADMIN".equalsIgnoreCase(p.role()) && !("CONSULTANT".equalsIgnoreCase(p.role()) && p.id().equals(lead.getAssignedTo()))) throw BusinessException.forbidden("只有当前负责顾问或超管可以生成学生档案");
        if (lead.getAssignedTo() == null) throw BusinessException.badRequest("线索尚未分配顾问，不能生成学生档案");
        if (lead.getStatus() == LeadStatus.invalid || lead.getStatus() == LeadStatus.closed) throw BusinessException.badRequest("无效或已关闭线索不能生成学生档案");
    }

    private boolean matchesKeyword(StudentProfile s, String keyword) {
        String k = clean(keyword);
        if (k == null) return true;
        return contains(s.getStudentName(), k) || contains(s.getPhone(), k) || contains(s.getWechat(), k) || contains(s.getStudentNo(), k);
    }

    private boolean contains(String value, String keyword) { return value != null && value.contains(keyword); }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private String clean(String value) { if (value == null) return null; String v = value.trim(); return v.isEmpty() ? null : v; }
    private String safe(String value) { return isBlank(value) ? "未填写" : value.trim(); }
    private String firstNonBlank(String a, String b) { return clean(a) == null ? clean(b) : clean(a); }
    private String buildRemark(Lead lead, StudentProfileDtos.ConvertRequest request) { return firstNonBlank(request == null ? null : request.confirmRemark(), lead.getRemark()); }
    private String nextStudentNo() { return "STU" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + System.currentTimeMillis(); }
}
