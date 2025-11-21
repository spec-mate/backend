# SpecMate Backend

컴퓨터 부품 추천 및 견적 플랫폼 서버입니다.

## Tech Stack

- **Framework:** Spring Boot 3.4.6, Java 17
- **Database:** PostgreSQL, Redis
- **Security:** Spring Security, JWT
- **AI:** Spring AI, OpenAI API, Qdrant Vector DB
- **Docs:** SpringDoc OpenAPI (Swagger)

## Project Structure

```
src/main/java/specmate/backend/
├── config/          # Security, JWT, Redis, WebClient 설정
├── controller/      # REST API Controllers
│   ├── auth/        # 인증 API
│   ├── chat/        # AI 채팅 API
│   ├── estimate/    # 견적 관리 API
│   ├── product/     # 상품 API
│   └── user/        # 사용자 API
├── service/         # 비즈니스 로직
├── repository/      # JPA Repository
├── entity/          # JPA Entity
└── dto/             # Request/Response DTO
```

## API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/signup` | 회원가입 | - |
| POST | `/api/auth/login` | 로그인 | - |
| POST | `/api/auth/refresh` | 토큰 갱신 | - |
| GET | `/api/product` | 상품 목록 조회 | - |
| GET | `/api/product/category/{category}` | 카테고리별 조회 | - |
| POST | `/api/chat/room` | 채팅방 생성 | USER |
| POST | `/api/chat/room/{roomId}/message` | 메시지 전송 | USER |
| GET | `/api/chat/room/{roomId}/messages` | 대화 내역 조회 | USER |
| GET | `/api/estimate` | 내 견적 목록 | USER |
| POST | `/api/estimate` | 견적 생성 | USER |

## Environment Variables

```env
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/specmate
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=your-secret-key

# Email (Gmail SMTP)
GMAIL_USERNAME=your-email@gmail.com
GMAIL_PASSWORD=app-password

# OpenAI
OPENAI_API_KEY=sk-xxx

# AI Server
AI_SERVER_URL=http://localhost:8000
```

## Run

```bash
# 개발 서버 실행
./gradlew bootRun

# 빌드
./gradlew build -x test

# JAR 실행
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

## Swagger

서버 실행 후 접속: `http://localhost:8080/swagger-ui/index.html`