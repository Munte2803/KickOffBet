# KickOffBet

A sports betting platform built with Spring Boot, featuring KYC verification,
JWT authentication, real-time odds generation and match data sync.

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Security + JWT
- PostgreSQL
- MinIO (document storage)
- Docker
- MapStruct
- Bucket4j (rate limiting)

## Features

- User registration with KYC (ID card upload & admin verification)
- JWT authentication with brute force protection
- Admin dashboard — user management, match management, odds management
- Automated match data sync from Football Data API
- Dynamic odds generation using Poisson distribution
- Rate limiting per IP

## Getting Started

### Prerequisites
- Java 21
- Docker & Docker Compose

### Setup

1. Clone the repository
```bash
   git clone https://github.com/Munte2803/KickOffBet.git
   cd KickOffBet
```

2. Create your environment file
```bash
   cp .env.example .env
```
   Fill in the values in `.env`

3. Start infrastructure
```bash
   docker-compose up -d
```

4. Run the application
```bash
   ./mvnw spring-boot:run
```

5. API docs available at
```
   http://localhost:8080/swagger-ui/index.html
```

## Environment Variables

See `.env.example` for all required variables.

## API Overview

| Group | Base Path | Access |
|---|---|---|
| Auth | `/api/auth/**` | Public |
| User | `/api/users/**` | Authenticated |
| Admin | `/api/admin/**` | Admin only |
| Matches | `/api/matches/**` | Authenticated |
| Teams | `/api/teams/**` | Authenticated |
| Leagues | `/api/leagues/**` | Authenticated |

