package com.interviewmirror.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

  @Value("${cloud.aws.credentials.access-key}")
  private String accessKey;

  @Value("${cloud.aws.credentials.secret-key}")
  private String secretKey;

  @Value("${cloud.aws.region.static}")
  private String region;

  @Bean
  public AmazonS3 amazonS3Client() {
    // 1. 환경변수에서 가져온 키값으로 인증 객체 생성
    BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

    // 2. S3 클라이언트 빌더를 통해 지역(Region)과 인증 정보를 설정하여 반환
    return AmazonS3ClientBuilder.standard()
        .withRegion(region)
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
        .build();
  }
}
