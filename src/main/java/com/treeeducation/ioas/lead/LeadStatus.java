package com.treeeducation.ioas.lead;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "线索状态")
public enum LeadStatus {
    unassigned, assigned, following, completed, invalid
}
