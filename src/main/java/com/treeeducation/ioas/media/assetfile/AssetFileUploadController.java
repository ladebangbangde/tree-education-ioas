package com.treeeducation.ioas.media.assetfile;

import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.media.assetfile.dto.AssetFileUploadResponse;
import com.treeeducation.ioas.media.contentpackage.ContentPackage;
import com.treeeducation.ioas.media.contentpackage.ContentPackageRepository;
import com.treeeducation.ioas.media.contentpackage.ContentPackageStatus;
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
    private final ContentPackageRepository contentPackageRepository;

    @Value("${ioas.storage.bucket}")
    private String bucketName;

    @Value("${ioas.storage.public-base-url}")
    private String publicBaseUrl;

    public AssetFileUploadController(MinioClient minioClient,
                                     AssetFileRepository assetFileRepository,
                                     ContentPackageRepository contentPackageRepository) {
        this.minioClient = minioClient;
        this.assetFileRepository = assetFileRepository;
        this.contentPackageRepository = contentPackageRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件到 MinIO")
    public ApiResponse<AssetFileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") Long packageId,
            @RequestParam(defaultValue = "image") AssetFileType fileType
    ) throws Exception {

        Long actualPackageId = resolvePackageId(packageId);

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
        assetFile.setPackageId(actualPackageId);
        assetFile.setFileName(originalFilename);
        assetFile.setOriginalName(originalFilename);
        assetFile.setType(fileType.name());
        assetFile.setFileType(fileType);
        assetFile.setMimeType(file.getContentType());
        assetFile.setFileSize(file.getSize());
        assetFile.setBucketName(bucketName);
        assetFile.setObjectKey(objectKey);
        assetFile.setPreviewUrl(publicUrl);
        assetFile.setThumbnailUrl(publicUrl);
        assetFile.setUploadStatus(UploadStatus.success);
        assetFile.setUploadedBy(0L);
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

    private Long resolvePackageId(Long packageId) {
        if (packageId != null && packageId > 0 && contentPackageRepository.existsById(packageId)) {
            return packageId;
        }

        ContentPackage contentPackage = new ContentPackage();
        contentPackage.setPackageNo("PKG" + System.currentTimeMillis());
        contentPackage.setTopicName("默认上传资源包");
        contentPackage.setOperatorId(0L);
        contentPackage.setOperatorName("system");
        contentPackage.setFullPath("/default-upload");
        contentPackage.setUploadStatus(ContentPackageStatus.completed);
        contentPackage.setCreatedBy(0L);
        contentPackage.setCreatedByName("system");

        ContentPackage saved = contentPackageRepository.save(contentPackage);
        return saved.getId();
    }
}
