# Untitles

Minimal collaborative note-taking app.

## Tech Stack

Java 21, Spring Boot 3.5, Spring Security, OAuth2, JPA, MySQL, AWS SES, Cloudflare R2

## Run

```bash
 cp .env.example .env
# .env 수정
./gradlew bootRun
```

## Environment Variables (.env)

```
DB_HOST=localhost
DB_USERNAME=root
DB_PASSWORD=

AWS_ACCESS_KEY=
AWS_SECRET_KEY=

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