package com.intervai.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="app_users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false,unique=true) private String email;
    @Column(nullable=false) private String passwordHash;
    private LocalDateTime createdAt=LocalDateTime.now();
    public Long getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
