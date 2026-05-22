package com.treeeducation.ioas.official.lead;

import org.springframework.stereotype.Service;

@Service
public class OfficialLeadService {

    private final OfficialLeadRepository repository;

    public OfficialLeadService(OfficialLeadRepository repository) {
        this.repository = repository;
    }

    public OfficialLeadDtos.LeadResponse create(OfficialLeadDtos.CreateLeadRequest request) {
        OfficialLead lead = new OfficialLead();
        lead.setName(request.name());
        lead.setAge(request.age());
        lead.setEducation(request.education());
        lead.setCity(request.city());
        lead.setPhone(request.phone());
        lead.setWechat(request.wechat());
        lead.setDestination(request.destination());
        lead.setBudget(request.budget());
        lead.setRemark(request.remark());
        lead.setSource(request.source());

        OfficialLead saved = repository.save(lead);

        return new OfficialLeadDtos.LeadResponse(
                saved.getId(),
                saved.getName(),
                saved.getPhone(),
                saved.getWechat(),
                saved.getDestination(),
                saved.getBudget(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }
}
