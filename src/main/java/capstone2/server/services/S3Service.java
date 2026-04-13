package capstone2.server.services;

import capstone2.server.dto.S3UploadResultDto;
import capstone2.server.dto.S3ObjectLinkDto;
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
import software.amazon.awssdk.core.ResponseInputStream;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

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