package com.treeeducation.ioas.applicationflow;

import com.treeeducation.ioas.common.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class ApplicationFlowStorageService {
    private final MinioClient minioClient;
    private final String bucket;
    private final String publicBaseUrl;

    public ApplicationFlowStorageService(@Value("${ioas.storage.endpoint}") String endpoint,
                                         @Value("${ioas.storage.access-key}") String accessKey,
                                         @Value("${ioas.storage.secret-key}") String secretKey,
                                         @Value("${ioas.storage.bucket}") String bucket,
                                         @Value("${ioas.storage.public-base-url}") String publicBaseUrl) {
        this.minioClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public StoredObject upload(Long studentProfileId, ApplicationStepCode stepCode, MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件不能为空");
        try {
            ensureBucket();
            String original = cleanFilename(file.getOriginalFilename());
            String suffix = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
            String objectKey = "application-flow/" + studentProfileId + "/" + stepCode.name().toLowerCase() + "/" + UUID.randomUUID() + suffix;
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new StoredObject(objectKey, publicBaseUrl.replaceAll("/$", "") + "/" + objectKey, original, contentType, file.getSize());
        } catch (Exception e) {
            throw BusinessException.badRequest("文件上传失败：" + e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        return filename.replace("\\", "_").replace("/", "_").trim();
    }

    public record StoredObject(String objectKey, String fileUrl, String originalFilename, String contentType, Long sizeBytes) {}
}
