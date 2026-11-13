# IntervAI — Your Personal AI Interview Coach

A full-stack starter application with:
- Responsive HTML/CSS/JavaScript frontend
- Java 17 + Spring Boot REST backend
- MySQL persistence
- Registration and login (BCrypt password hashing)
- Interview question bank and answer submission
- Simple transparent demo scoring (not an LLM evaluation)

## Requirements
- Java 17+
- Maven 3.9+
- MySQL 8+
- VS Code (or IntelliJ)

## 1. Create the database
Run in MySQL:

```sql
CREATE DATABASE intervai;
```

## 2. Configure the backend
Open `backend/src/main/resources/application.properties` and set your MySQL username/password.

The default configuration expects:
- database: `intervai`
- username: `root`
- password: `your_password`

## 3. Start the backend

From the `backend` folder:

```bash
mvn spring-boot:run
```

Backend URL: `http://localhost:8080`

Health check: `http://localhost:8080/api/health`

## 4. Start the frontend
Open `frontend/index.html` using VS Code Live Server.

The frontend calls the backend at `http://localhost:8080`. If the backend is not running, the landing page still opens, but registration, login and interview submission need the backend.

## Demo flow
1. Create an account using Sign Up.
2. Log in.
3. Open Dashboard and start a mock interview.
4. Select a role and difficulty.
5. Answer the displayed question and submit it.
6. Review the demo feedback.

## Important AI note
This starter includes a working question bank and a basic rule-based demo evaluator. It does **not** claim to provide LLM-based evaluation yet. To enable genuine AI-generated questions and nuanced feedback, connect an LLM provider from the backend only, store the API key in an environment variable, and never expose it in frontend JavaScript.

## API endpoints
- `GET /api/health`
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/interviews/questions?role=Java%20Developer&level=Beginner`
- `POST /api/interviews/evaluate`

## Security / production notes
This is a learning MVP, not production-ready authentication. Login currently returns a simple demo token string; protect all private endpoints with JWT validation before real deployment. Add HTTPS, rate limiting, email verification, password reset, stronger input validation, privacy notices and tests before public release.
