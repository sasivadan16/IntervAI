package com.intervai.controller;
import com.intervai.dto.AuthRequest;
import com.intervai.model.AppUser;
import com.intervai.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final UserRepository users; private final PasswordEncoder encoder;
 public AuthController(UserRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}
 @PostMapping("/signup") public ResponseEntity<?> signup(@Valid @RequestBody AuthRequest req){
  String email=req.getEmail().trim().toLowerCase();
  if(users.existsByEmail(email)) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","An account with this email already exists."));
  AppUser u=new AppUser();u.setName(req.getName().trim());u.setEmail(email);u.setPasswordHash(encoder.encode(req.getPassword()));users.save(u);
  return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message","Account created successfully."));
 }
 @PostMapping("/login") public ResponseEntity<?> login(@RequestBody AuthRequest req){
  AppUser u=users.findByEmail(req.getEmail().trim().toLowerCase()).orElse(null);
  if(u==null || !encoder.matches(req.getPassword(),u.getPasswordHash())) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Invalid email or password."));
  // Demo-only marker, not a production JWT. Private API routes are not protected in this starter.
  return ResponseEntity.ok(Map.of("message","Login successful","demoToken","demo-session","name",u.getName(),"email",u.getEmail()));
 }
}
