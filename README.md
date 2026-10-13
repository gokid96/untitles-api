# Untitles

Minimal collaborative note-taking app.

## Tech Stack

Java 21, Spring Boot 3.5, Spring Security, OAuth2 (Google, Kakao, Naver), JWT (jjwt), JPA, MySQL, Redis (Spring Cache), AWS SES, Cloudflare R2, OWASP AntiSamy, Bucket4j, Spring Boot Actuator + Micrometer Prometheus

## Run

Requirements:

- JDK 21 (Gradle toolchain)
- MySQL reachable at `DB_HOST:3306`, with the database named in `spring.datasource.url` of `application-dev.yml`
- Redis on `localhost:6379` (the default profile is `dev`, set by `spring.profiles.active` in `application.yml`; it has no Redis settings, so the Spring Boot default is used)

The server listens on port 8070.

Spring Boot does not read `.env` files. Copy `.env.example` to `.env`, fill in the values, and load them into the process environment before starting the app.

PowerShell:

```powershell
Copy-Item .env.example .env   # then edit .env
Get-Content .env | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item -Path "env:$($k.Trim())" -Value $v.Trim() }
.\gradlew.bat bootRun
```

bash:

```bash
cp .env.example .env   # then edit .env
set -a; . ./.env; set +a
./gradlew bootRun
```

Alternatively, set the same variables in your IDE run configuration.

Unit tests (Mockito only, no MySQL/Redis needed): `./gradlew test`

## Environment Variables

All variables are listed in `.env.example` (values left empty). Never commit real values; `.env` is git-ignored.

```
# Database (all profiles; host only, the JDBC URL fixes port 3306)
DB_HOST=
DB_USERNAME=
DB_PASSWORD=

# OAuth2 (all profiles)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=

# JWT (prod/test; the dev profile sets jwt.secret in application-dev.yml)
JWT_SECRET=

# Redis (prod only; dev/test use the Spring Boot default localhost:6379)
REDIS_HOST=
REDIS_PASSWORD=

# AWS SES (dev/prod; not used by the test profile)
AWS_ACCESS_KEY=
AWS_SECRET_KEY=

# Cloudflare R2 (all profiles)
R2_ACCESS_KEY_ID=
R2_SECRET_ACCESS_KEY=
R2_ENDPOINT=
R2_BUCKET_NAME=
R2_PUBLIC_URL=
```

## Project Structure
```
src/main/java/com/untitles/
├── domain/
│   ├── auth/        # Authentication
│   ├── email/       # Email service
│   ├── folder/      # Folder management
│   ├── image/       # Image upload
│   ├── post/        # Post CRUD
│   ├── publish/     # Workspace publishing & public pages
│   ├── user/        # User
│   └── workspace/   # Workspace
└── global/          # Common config, exception handling
```

## Features

- OAuth2 social login
- Workspace member permission management
- Folder/Post CRUD
- Image upload (R2)
- Email service (SES)
- Optimistic locking for concurrent edit detection

## Deployment

- Backend: Docker
- Frontend: Vercel