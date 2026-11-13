package com.intervai.controller;
import com.intervai.dto.EvaluationRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/interviews")
public class InterviewController {
 private record Question(Long id,String role,String level,String question){}
 private final List<Question> bank=List.of(
  new Question(1L,"Java Developer","Beginner","What is the difference between JDK, JRE and JVM?"),
  new Question(2L,"Java Developer","Intermediate","Explain the four pillars of object-oriented programming with examples."),
  new Question(3L,"Java Developer","Advanced","How does HashMap work internally in Java?"),
  new Question(4L,"Python Developer","Beginner","What are Python lists and tuples, and how are they different?"),
  new Question(5L,"Python Developer","Intermediate","Explain list comprehension and when you would use it."),
  new Question(6L,"Frontend Developer","Beginner","What is the difference between HTML, CSS and JavaScript?"),
  new Question(7L,"Data Analyst","Beginner","What is the difference between INNER JOIN and LEFT JOIN in SQL?"),
  new Question(8L,"Machine Learning Engineer","Intermediate","Explain the difference between supervised and unsupervised learning.")
 );
 @GetMapping("/questions") public ResponseEntity<?> question(@RequestParam(defaultValue="Java Developer") String role,@RequestParam(defaultValue="Beginner") String level){
  List<Question> matches=bank.stream().filter(q->q.role().equalsIgnoreCase(role)&&q.level().equalsIgnoreCase(level)).toList();
  if(matches.isEmpty()) matches=bank.stream().filter(q->q.role().equalsIgnoreCase(role)).toList();
  if(matches.isEmpty()) matches=bank;
  Question chosen=matches.get(new Random().nextInt(matches.size()));
  return ResponseEntity.ok(Map.of("id",chosen.id(),"role",chosen.role(),"level",chosen.level(),"question",chosen.question()));
 }
 @PostMapping("/evaluate") public ResponseEntity<?> evaluate(@Valid @RequestBody EvaluationRequest req){
  String a=req.getAnswer().trim(); int words=a.split("\\s+").length;
  int score=Math.min(95,Math.max(25,25+words));
  String feedback= words<35 ? "Your answer is a useful start. Add a clear definition, explain the reasoning and include a practical example." :
   "Good effort. Make your answer stronger by structuring it into a definition, key points and a real-world example. Check technical accuracy before an actual interview.";
  return ResponseEntity.ok(Map.of("score",score,"feedback",feedback,"wordCount",words,"evaluationType","RULE_BASED_DEMO"));
 }
}
