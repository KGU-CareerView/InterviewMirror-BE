package com.interviewmirror.infrastructure;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

  private final S3Presigner s3Presigner;

  @Value("${cloud.aws.s3.bucket}") // application.yml에서 버킷명 가져오기
  private String bucket;

  public String generatePresignedUrl(Long sessionId, String fileType) {

    String contentType = "application/octet-stream"; // 기본값
    if ("mp4".equalsIgnoreCase(fileType) || "webm".equalsIgnoreCase(fileType)) {
      contentType = "video/" + fileType;
    } else if ("png".equalsIgnoreCase(fileType)
        || "jpeg".equalsIgnoreCase(fileType)
        || "jpg".equalsIgnoreCase(fileType)) {
      contentType = "image/" + fileType;
    }
    String fileName = "user/session/" + sessionId + "/" + UUID.randomUUID() + "." + fileType;

    // S3에 올릴 파일에 대한 기본 정보(버킷, 파일경로) 설정
    PutObjectRequest objectRequest =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(fileName)
            // 만약 프론트엔드에서 Content-Type 오류가 난다면 아래 주석을 풀고 사용하세요!
            // .contentType("video/" + fileType)
            .build();

    // Presigned URL 발급 요청서 작성 (유효기간 10분 설정)
    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(objectRequest)
            .build();

    // 실제 URL 생성
    PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

    // 문자열 형태의 URL 반환
    return presignedRequest.url().toString();
  }
}
