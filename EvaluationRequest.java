package com.intervai.dto;
import jakarta.validation.constraints.*;
public class EvaluationRequest {
 @NotNull private Long questionId;
 @NotBlank @Size(min=15,max=10000) private String answer;
 public Long getQuestionId(){return questionId;} public void setQuestionId(Long v){questionId=v;}
 public String getAnswer(){return answer;} public void setAnswer(String v){answer=v;}
}
