package com.intervai.dto;
import jakarta.validation.constraints.*;
public class AuthRequest {
 @NotBlank private String name;
 @NotBlank @Email private String email;
 @NotBlank @Size(min=8) private String password;
 public String getName(){return name;} public void setName(String v){name=v;}
 public String getEmail(){return email;} public void setEmail(String v){email=v;}
 public String getPassword(){return password;} public void setPassword(String v){password=v;}
}
