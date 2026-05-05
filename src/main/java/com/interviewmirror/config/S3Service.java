package com.interviewmirror.config;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class S3Service {
    // private final AmazonS3 amazonS3; // 실제 구현시 주입

    public String generatePresignedUrl(Long sessionId, String fileType, String ext) {
        String fileName = "user/session/" + sessionId + "/" + UUID.randomUUID() + "." + ext;
        Date expiration = new Date(System.currentTimeMillis() + 1000 * 60 * 10); // 10분 유효

        // 실제 동작:
        // GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest("bucket-name", fileName)
        //         .withMethod(HttpMethod.PUT).withExpiration(expiration);
        // return amazonS3.generatePresignedUrl(request).toString();

        return "https://mock-s3-bucket.s3.ap-northeast-2.amazonaws.com/" + fileName + "?X-Amz-Signature=mock...";
    }
}