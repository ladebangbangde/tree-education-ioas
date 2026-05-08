package com.treeeducation.ioas;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {
    @Test
    void staticOpenApiContractsAreCommitted() {
        assertTrue(Files.exists(Path.of("docs/openapi/ioas-openapi.yaml")));
        assertTrue(Files.exists(Path.of("docs/openapi/ioas-openapi.json")));
    }
}
