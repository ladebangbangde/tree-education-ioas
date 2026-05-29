package com.treeeducation.ioas.consultant;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/consultants")
public class ConsultantAdminController {
    private final ConsultantAdminService service;

    public ConsultantAdminController(ConsultantAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ConsultantAdminDtos.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<ConsultantAdminDtos.Response> create(@RequestBody ConsultantAdminDtos.CreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConsultantAdminDtos.Response> update(@PathVariable Long id,
                                                            @RequestBody ConsultantAdminDtos.UpdateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ConsultantAdminDtos.AvatarResponse> uploadAvatar(@PathVariable Long id,
                                                                         @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(service.uploadAvatar(id, file));
    }
}
