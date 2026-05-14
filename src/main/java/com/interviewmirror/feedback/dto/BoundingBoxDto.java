package com.interviewmirror.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoundingBoxDto {

  private int x1;
  private int y1;
  private int x2;
  private int y2;
}
