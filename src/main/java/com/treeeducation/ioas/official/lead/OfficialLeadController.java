package com.treeeducation.ioas.official.lead;

/**
 * Legacy official lead controller kept only for source compatibility.
 *
 * The active official website lead endpoint is provided by
 * com.treeeducation.ioas.lead.OfficialLeadController, which writes into the main
 * OA lead center and triggers assignment, task creation, and notifications.
 * Keeping this class as a Spring MVC controller would create duplicate bean names
 * and duplicate request mappings for POST /api/official/leads.
 */
@Deprecated
public class OfficialLeadController {

    private final OfficialLeadService service;

    public OfficialLeadController(OfficialLeadService service) {
        this.service = service;
    }

    public OfficialLeadDtos.LeadResponse create(OfficialLeadDtos.CreateLeadRequest request) {
        return service.create(request);
    }
}
