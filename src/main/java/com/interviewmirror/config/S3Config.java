package com.interviewmirror.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

  @Value("${cloud.aws.credentials.access-key}")
  private String accessKey;

  @Value("${cloud.aws.credentials.secret-key}")
  private String secretKey;

  @Value("${cloud.aws.region.static}")
  private String region;

  @Bean
  public S3Presigner s3Presigner() {
    // 환경변수에서 가져온 키값으로 인증 객체 생성
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

    // S3Presigner 빌더를 통해 지역(Region)과 인증 정보를 설정하여 반환
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .build();
  }
}
