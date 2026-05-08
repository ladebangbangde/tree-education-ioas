package com.treeeducation.ioas;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {
    @Test
    void staticOpenApiContractsAreCommittedAndUseFrontendEnums() throws IOException {
        Path yaml = Path.of("docs/openapi/ioas-openapi.yaml");
        Path json = Path.of("docs/openapi/ioas-openapi.json");
        assertTrue(Files.exists(yaml));
        assertTrue(Files.exists(json));
        String contract = Files.readString(yaml) + Files.readString(json);
        assertTrue(contract.contains("SUPER_ADMIN"));
        assertTrue(contract.contains("pending_upload"));
        assertTrue(contract.contains("media_upload"));
        assertFalse(contract.contains("new_lead"));
        assertFalse(contract.contains("in_progress"));
        assertFalse(contract.contains("archived"));
    }
}
