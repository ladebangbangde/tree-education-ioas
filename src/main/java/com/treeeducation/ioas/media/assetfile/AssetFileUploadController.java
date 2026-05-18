package com.treeeducation.ioas.media.assetfile;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.media.assetfile.dto.AssetFileUploadResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@Tag(name = "文件上传")
public class AssetFileUploadController {

    private final MinioClient minioClient;
    private final AssetFileRepository assetFileRepository;

    @Value("${ioas.storage.bucket}")
    private String bucketName;

    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    public AssetFileUploadController(MinioClient minioClient,
                                     AssetFileRepository assetFileRepository) {
        this.minioClient = minioClient;
        this.assetFileRepository = assetFileRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件到 MinIO")
    public ApiResponse<AssetFileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") Long packageId,
            @RequestParam(defaultValue = "image") AssetFileType fileType
    ) throws Exception {

        String originalFilename = file.getOriginalFilename();
        String suffix = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + UUID.randomUUID() + suffix;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        String publicUrl = publicBaseUrl + "/" + objectKey;

        AssetFile assetFile = new AssetFile();
        assetFile.setFileNo("FILE" + System.currentTimeMillis());
        assetFile.setPackageId(packageId);
        assetFile.setFileName(originalFilename);
        assetFile.setFileType(fileType);
        assetFile.setMimeType(file.getContentType());
        assetFile.setFileSize(file.getSize());
        assetFile.setBucketName(bucketName);
        assetFile.setObjectKey(objectKey);
        assetFile.setPreviewUrl(publicUrl);
        assetFile.setThumbnailUrl(publicUrl);
        assetFile.setUploadStatus(UploadStatus.success);
        assetFile.setCreatedBy(0L);
        assetFile.setCreatedByName("system");

        AssetFile saved = assetFileRepository.save(assetFile);

        return ApiResponse.ok(new AssetFileUploadResponse(
                saved.getId(),
                saved.getFileNo(),
                saved.getPackageId(),
                saved.getFileName(),
                saved.getFileType(),
                saved.getMimeType(),
                saved.getFileSize(),
                saved.getBucketName(),
                saved.getObjectKey(),
                publicUrl
        ));
    }
}
