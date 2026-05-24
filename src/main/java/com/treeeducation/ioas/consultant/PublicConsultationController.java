package com.treeeducation.ioas.consultant;

/**
 * Legacy public consultation controller kept only for source compatibility.
 *
 * The active public consultation API is provided by
 * com.treeeducation.ioas.consultation.PublicConsultationController.
 * Keeping this class as a Spring MVC controller would create duplicate bean names
 * and duplicate request mappings for /api/v1/public/consultation-options and
 * /api/v1/public/consultations.
 */
@Deprecated
public class PublicConsultationController {
    private final PublicConsultationService service;

    public PublicConsultationController(PublicConsultationService service) {
        this.service = service;
    }

    public ConsultationDtos.OptionsResponse options() {
        return service.options();
    }

    public ConsultationDtos.CreateResponse create(ConsultationDtos.CreateRequest request) {
        return service.create(request);
    }
}
