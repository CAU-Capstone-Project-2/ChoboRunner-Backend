package capstone2.server.services;

import capstone2.server.dto.S3UploadResultDto;
import capstone2.server.dto.S3ObjectLinkDto;
import capstone2.server.dto.S3PresignedUrlDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.core.ResponseInputStream;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile multipartFile) throws IOException {
        return uploadWithViewLink(multipartFile).uploadUrl();
    }

    public DownloadResult download(String key) throws IOException {
        String normalizedKey = normalizeKey(key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(normalizedKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(request)) {
            byte[] content = object.readAllBytes();
            String contentType = object.response().contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return new DownloadResult(
                    normalizedKey,
                    resolveDownloadFileName(normalizedKey),
                    contentType,
                    content
            );
        }
    }

    public S3UploadResultDto uploadWithViewLink(MultipartFile multipartFile) throws IOException {
        String originalName = multipartFile.getOriginalFilename() == null ? "file" : multipartFile.getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + originalName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(multipartFile.getContentType())
                .build();

        // S3에 파일 업로드
        s3Client.putObject(
                putObjectRequest,
                RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize())
        );

        String objectUrl = s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(fileName).build())
                .toExternalForm();

        // 업로드 URL과 조회 URL은 동일한 객체 URL을 사용합니다.
        return new S3UploadResultDto(fileName, objectUrl, objectUrl);
    }

    /**
     * 원본 영상을 원본 파일명 그대로를 S3 key로 사용해 업로드한다.
     * 동일 파일명은 기존 객체를 덮어쓴다(관리 편의 목적).
     *
     * @return 업로드된 S3 객체 key (= 원본 파일명)
     */
    public String uploadOriginalVideo(MultipartFile multipartFile) throws IOException {
        String key = multipartFile.getOriginalFilename();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("원본 영상 파일명이 없습니다.");
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(multipartFile.getContentType())
                .build();

        s3Client.putObject(
                putObjectRequest,
                RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize())
        );

        return key;
    }

    /**
     * S3 key에 대한 기간 한정 presigned GET URL을 발급한다.
     * presignGetObject는 S3를 호출하지 않으므로 객체 존재 여부는 검증하지 않는다.
     * <p>S3에 저장된 객체의 Content-Type이 잘못(octet-stream 등) 잡혀 있어 브라우저가
     * 영상을 재생하지 못하는 문제를 막기 위해, presigned URL이 응답 헤더를 덮어쓰도록
     * {@code response-content-type} / {@code response-content-disposition}을 함께 서명한다.
     */
    public S3PresignedUrlDto presignedGetUrl(String key, long expiresInSeconds) {
        String normalizedKey = normalizeKey(key);

        GetObjectRequest.Builder getRequestBuilder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(normalizedKey)
                .responseContentDisposition("inline");   // 다운로드 대신 브라우저 내 재생

        String contentType = resolveContentTypeByExtension(normalizedKey);
        if (contentType != null) {
            getRequestBuilder.responseContentType(contentType);
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .getObjectRequest(getRequestBuilder.build())
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
        return new S3PresignedUrlDto(normalizedKey, url, expiresInSeconds);
    }

    /** key 확장자로 미디어 Content-Type을 추정한다. 모르면 {@code null}(오버라이드 안 함). */
    private String resolveContentTypeByExtension(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".mov"))  return "video/quicktime";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mkv"))  return "video/x-matroska";
        if (lower.endsWith(".avi"))  return "video/x-msvideo";
        return null;
    }

    public List<S3ObjectLinkDto> listObjectLinks() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .build();

        return s3Client.listObjectsV2Paginator(request)
                .stream()
                .flatMap(page -> page.contents().stream())
                .sorted(Comparator.comparing(S3Object::lastModified).reversed())
                .map(s3Object -> new S3ObjectLinkDto(
                        s3Object.key(),
                        s3Object.size(),
                        s3Object.lastModified(),
                        s3Client.utilities()
                                .getUrl(GetUrlRequest.builder().bucket(bucket).key(s3Object.key()).build())
                                .toExternalForm()
                ))
                .toList();
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("S3 key가 없습니다.");
        }
        return key.trim();
    }

    private String resolveDownloadFileName(String key) {
        String fileName = key;
        int slashIndex = fileName.lastIndexOf('/') + 1;
        if (slashIndex > 0 && slashIndex < fileName.length()) {
            fileName = fileName.substring(slashIndex);
        }

        if (fileName.matches("^[0-9a-fA-F-]{36}_.+$")) {
            return fileName.substring(37);
        }

        return fileName;
    }

    public record DownloadResult(
            String key,
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}