package com.treeeducation.ioas.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** MinIO implementation; keep interface compatible with future OSS provider. */
@Service
@EnableConfigurationProperties(StorageProperties.class)
public class MinioObjectStorageService implements ObjectStorageService {
    private final StorageProperties props;
    private final MinioClient client;

    public MinioObjectStorageService(StorageProperties props) {
        this.props = props;
        this.client = MinioClient.builder().endpoint(props.endpoint()).credentials(props.accessKey(), props.secretKey()).build();
    }

    public StoredObject put(Long packageId, MultipartFile file) {
        return put("packages/" + packageId, file);
    }

    public StoredObject put(String objectPrefix, MultipartFile file) {
        try {
            ensureBucket();
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String prefix = sanitizePrefix(objectPrefix);
            String key = prefix + "/" + UUID.randomUUID() + "-" + sanitizeFileName(original);
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(key)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            String url = props.publicBaseUrl() + "/" + key;
            return new StoredObject(props.bucket(), key, url, url);
        } catch (Exception e) {
            throw new IllegalStateException("对象存储写入失败", e);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(props.bucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储读取失败", e);
        }
    }

    public void remove(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(props.bucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储删除失败", e);
        }
    }

    public String bucket() {
        return props.bucket();
    }

    private String sanitizePrefix(String value) {
        String raw = value == null || value.isBlank() ? "misc" : value.trim();
        return raw.replaceAll("^/+", "").replaceAll("/+$", "").replaceAll("[^a-zA-Z0-9/_\\-\\u4e00-\\u9fa5]", "_");
    }

    private String sanitizeFileName(String value) {
        String raw = value == null || value.isBlank() ? "file" : value.trim();
        return raw.replaceAll("[/\\\\]", "_").replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    private void ensureBucket() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
    }
}
