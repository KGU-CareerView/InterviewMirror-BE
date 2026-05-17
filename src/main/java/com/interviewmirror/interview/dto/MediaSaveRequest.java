package com.interviewmirror.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaSaveRequest {
  private String type; // video, text, image, mp3 등
  private String content; // 텍스트 내용
  private String contentUrl; // 저장할 컨텐츠의 S3 URL
}
