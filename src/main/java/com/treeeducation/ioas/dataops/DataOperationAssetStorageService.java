package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DataOperationAssetStorageService {
    @Value("${ioas.storage.endpoint}")
    private String endpoint;

    @Value("${ioas.storage.access-key}")
    private String accessKey;

    @Value("${ioas.storage.secret-key}")
    private String secretKey;

    @Value("${ioas.storage.bucket:ioas-assets}")
    private String bucket;

    @Value("${ioas.storage.public-base-url:}")
    private String publicBaseUrl;

    @Value("${app.upload.base-dir:/app/uploads}")
    private String legacyUploadBaseDir;

    public StoredAsset upload(MultipartFile file, String objectKey, String originalFilename) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件不能为空");
        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        try (InputStream input = file.getInputStream()) {
            MinioClient client = client();
            ensureBucket(client);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(mimeType)
                    .stream(input, file.getSize(), -1)
                    .build());
            return new StoredAsset(bucket, objectKey, publicUrl(objectKey), mimeType, file.getSize(), originalFilename);
        } catch (Exception ex) {
            throw BusinessException.badRequest("上传到MinIO失败：" + ex.getMessage());
        }
    }

    public byte[] readBytes(String bucketName, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) throw BusinessException.badRequest("文件对象路径为空");
        String effectiveBucket = bucketName == null || bucketName.isBlank() ? bucket : bucketName;
        try (InputStream input = client().getObject(GetObjectArgs.builder()
                .bucket(effectiveBucket)
                .object(objectKey)
                .build())) {
            return input.readAllBytes();
        } catch (Exception minioError) {
            return readLegacyLocalBytes(objectKey, minioError);
        }
    }

    public void delete(String bucketName, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        String effectiveBucket = bucketName == null || bucketName.isBlank() ? bucket : bucketName;
        Exception minioError = null;
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(effectiveBucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            minioError = ex;
        }
        deleteLegacyLocalFile(objectKey, minioError);
    }

    private byte[] readLegacyLocalBytes(String objectKey, Exception minioError) {
        try {
            Path base = Path.of(legacyUploadBaseDir).normalize().toAbsolutePath();
            Path target = base.resolve(objectKey).normalize().toAbsolutePath();
            if (!target.startsWith(base)) throw BusinessException.badRequest("非法文件路径");
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                throw BusinessException.notFound("文件不存在，MinIO与本地兼容路径均未找到：" + objectKey);
            }
            return Files.readAllBytes(target);
        } catch (IOException ioException) {
            throw BusinessException.badRequest("读取文件失败：" + ioException.getMessage() + "；MinIO错误：" + minioError.getMessage());
        }
    }

    private void deleteLegacyLocalFile(String objectKey, Exception minioError) {
        try {
            Path base = Path.of(legacyUploadBaseDir).normalize().toAbsolutePath();
            Path target = base.resolve(objectKey).normalize().toAbsolutePath();
            if (!target.startsWith(base)) throw BusinessException.badRequest("非法文件路径");
            if (Files.exists(target) && Files.isRegularFile(target)) Files.delete(target);
        } catch (IOException ioException) {
            String minioMessage = minioError == null ? "" : "；MinIO错误：" + minioError.getMessage();
            throw BusinessException.badRequest("删除文件失败：" + ioException.getMessage() + minioMessage);
        }
        if (minioError != null) {
            // MinIO 的 removeObject 对不存在对象通常不报错；这里保留本地兼容删除，不因历史本地文件不存在而阻断数据库清理。
        }
    }

    private MinioClient client() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    private void ensureBucket(MinioClient client) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    private String publicUrl(String objectKey) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? endpoint + "/" + bucket
                : publicBaseUrl;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + objectKey.replace((char) 92, '/');
    }

    public record StoredAsset(String bucketName, String objectKey, String publicUrl, String mimeType, long fileSize, String originalFilename) {}
}
