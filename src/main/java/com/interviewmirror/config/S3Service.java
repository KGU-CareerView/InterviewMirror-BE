package com.interviewmirror.config;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import java.net.URL;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class S3Service {
  private final AmazonS3 amazonS3; // AWS 설정 클래스에서 Bean으로 주입받음

  @Value("${cloud.aws.s3.bucket}") // application.yml에서 버킷명 가져오기
  private String bucket;

  public String generatePresignedUrl(Long sessionId, String fileType) {
    String fileName = "user/session/" + sessionId + "/" + UUID.randomUUID() + "." + fileType;

    // URL 유효기간 설정 (예: 발급 후 10분)
    Date expiration = new Date();
    long expTimeMillis = expiration.getTime();
    expTimeMillis += 1000 * 60 * 10;
    expiration.setTime(expTimeMillis);

    // 실제 S3 Presigned URL 발급 요청
    URL url = amazonS3.generatePresignedUrl(bucket, fileName, expiration, HttpMethod.PUT);

    return url.toString();
  }
}
