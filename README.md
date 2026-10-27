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
# Kakao consent items (default profile_nickname,profile_image; add account_email after the Kakao app becomes a biz app)
KAKAO_SCOPE=

# App / web ID-token login (comma-separated client IDs accepted as the token audience)
GOOGLE_ID_TOKEN_CLIENT_IDS=   # Google web client ID, iOS client ID
APPLE_CLIENT_IDS=             # net.untitles.app (iOS app), the web Services ID
# Sign in with Apple key (.p8): revokes the Apple link when a user deletes the account (App Store rule)
APPLE_TEAM_ID=
APPLE_KEY_ID=
APPLE_PRIVATE_KEY=            # .p8 content on one line, newlines written as \n

# JWT (prod/test; the dev profile sets jwt.secret in application-dev.yml)
JWT_SECRET=

# Redis (prod only; dev/test use the Spring Boot default localhost:6379)
REDIS_HOST=
REDIS_PASSWORD=

# AWS SES (dev/prod; not used by the test profile)
AWS_ACCESS_KEY=
AWS_SECRET_KEY=

# Mail (password reset, signup verification). MAIL_PROVIDER: log (default, sends nothing) | smtp | ses
MAIL_PROVIDER=
MAIL_FROM=
# SMTP, when MAIL_PROVIDER=smtp (e.g. Gmail: smtp.gmail.com, 587, the Gmail address, an app password)
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=

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

- Frontend: Vercel. It calls the API at `https://api.untitles.net/api/v1`.
- Backend: deployed by hand to one Linux server with Docker Engine and the compose plugin. There is no CI deploy.
- Cloudflare terminates HTTPS and reaches the server through a Cloudflare Tunnel that the server opens outbound, so the server needs no inbound port.
- Email still goes through AWS SES, so the AWS account has to stay active.

### Architecture

```
browser --HTTPS--> Cloudflare (api.untitles.net, TLS ends here)
                       |
                       | Cloudflare Tunnel, opened outbound by cloudflared
                       v
cloudflared --HTTP--> caddy:80 --> api:8070 --> mysql:3306  (named volume, MySQL 8.4)
                                            \-> redis:6379  (cache only, not persisted)
```

- `docker-compose.prod.yml` runs `cloudflared`, `caddy`, `api`, `mysql` and `redis` as the compose project `untitles` on one Docker host. The `api` image (`untitles-api:local`) is built on the server from this repository, for the server's own CPU architecture. No service publishes a host port; they reach each other on the compose network.
- `deploy/Caddyfile` serves plain HTTP on `:80` for cloudflared. It keeps `X-Forwarded-Proto` and `X-Forwarded-For` from private addresses (the compose network), so the `X-Forwarded-Proto: https` set by Cloudflare reaches Spring and the OAuth redirect URIs stay `https://api.untitles.net/login/oauth2/code/{google,kakao,naver}`. `X-Forwarded-Host` is always set to the request's Host, the API hostname of the tunnel route, so a visitor cannot choose the host name Spring puts into absolute URLs. It logs the visitor address from `Cf-Connecting-Ip`, compresses responses (zstd, gzip) and answers 404 for `/actuator*`.

### Server prerequisites

- Docker Engine with the compose plugin, and a user in the `docker` group.
- Outbound HTTPS (GitHub, Docker Hub and Maven Central for the build) and port 7844 to Cloudflare (UDP for QUIC, TCP for the HTTP/2 fallback).
- Only accounts you trust with production. Everyone in the `docker` group or with `sudo` on the server can read every production secret: `docker inspect` and `docker exec` show the container environments, and they can read `.env` and `api.env`. Other accounts cannot: the env files are mode 600, and no secret appears on a command line, where every account could read it in the process list (Redis reads its password from stdin).

### First-time setup

1. Create the Cloudflare Tunnel (Cloudflare dashboard, Networking > Tunnels; in the Zero Trust dashboard it is Networks > Tunnels):
   1. Create a tunnel of type cloudflared and copy the token from the shown `cloudflared ... --token <TOKEN>` command. The compose file runs the connector, so do not install it from that page.
   2. In the DNS records of `untitles.net`, delete any existing record for `api`.
   3. In the tunnel, add a published application route (public hostname) `api.untitles.net` with service `http://caddy:80` (type HTTP, URL `caddy:80`). Cloudflare creates the proxied DNS record.
   4. Keep SSL/TLS > Edge Certificates > Always Use HTTPS on, so plain HTTP is redirected at the edge.
2. Clone the repository on the server:

   ```bash
   git clone https://github.com/gokid96/untitles-api.git ~/untitle
   cd ~/untitle
   ```

3. Create `.env` and `api.env` next to `docker-compose.prod.yml`, readable only by you (run `umask 077` before creating them, or `chmod 600` them afterwards). Both files are in `.gitignore` and `.dockerignore`. Write every line as `NAME='value'`; values must not contain a single quote, a backslash or a newline. `openssl rand -hex 32` gives safe random passwords.

   `.env`, read by compose:

   ```
   DB_USERNAME='untitles'
   DB_PASSWORD='<random>'
   REDIS_PASSWORD='<random>'
   MYSQL_ROOT_PASSWORD='<random>'
   CLOUDFLARE_TUNNEL_TOKEN='<token from step 1>'
   ```

   `api.env`, the environment of the api container (same database and Redis values as `.env`; see Environment Variables for the others):

   ```
   JWT_SECRET='<random>'
   DB_HOST='mysql'
   DB_USERNAME='untitles'
   DB_PASSWORD='<same as in .env>'
   REDIS_HOST='redis'
   REDIS_PASSWORD='<same as in .env>'
   AWS_ACCESS_KEY='...'
   AWS_SECRET_KEY='...'
   R2_ACCESS_KEY_ID='...'
   R2_SECRET_ACCESS_KEY='...'
   R2_ENDPOINT='...'
   R2_BUCKET_NAME='...'
   R2_PUBLIC_URL='...'
   GOOGLE_CLIENT_ID='...'
   GOOGLE_CLIENT_SECRET='...'
   KAKAO_CLIENT_ID='...'
   KAKAO_CLIENT_SECRET='...'
   NAVER_CLIENT_ID='...'
   NAVER_CLIENT_SECRET='...'
   ```

   `DB_USERNAME` must not be `root`. MySQL applies `DB_USERNAME`, `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` only when its data volume is first created.
4. Build and start the stack:

   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   ```

### Update

```bash
cd ~/untitle
git pull
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f --filter label=app=untitles-api
```

Only containers whose image or configuration changed are recreated, normally just `api`; the API answers 502 for about 15 seconds while the new container starts. The last command removes only this project's old api images, so other projects on the host keep theirs. If `deploy/Caddyfile` changed, also run `docker compose -f docker-compose.prod.yml exec caddy caddy reload --config /etc/caddy/Caddyfile`.

Check the result (`/actuator` is blocked for the public at Caddy, so ask from inside the Caddy container):

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml exec -T caddy wget -qO- http://api:8070/actuator/health
docker compose -f docker-compose.prod.yml logs -f api
```

### Rollback

Check out an earlier commit and rebuild:

```bash
cd ~/untitle
git checkout <commit>
docker compose -f docker-compose.prod.yml up -d --build
# back to the latest code later
git checkout main && git pull && docker compose -f docker-compose.prod.yml up -d --build
```

### Database access

MySQL has no published port. Open a client inside the container:

```bash
cd ~/untitle
docker compose -f docker-compose.prod.yml exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
```

For a GUI client, tunnel to the container address over SSH and connect to `127.0.0.1:3307` as `DB_USERNAME`:

```bash
# on the server
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' untitles-mysql-1
# on your machine
ssh -N -p <ssh-port> -L 3307:<container-ip>:3306 <user>@<server>
```

MySQL applies `MYSQL_USER`, `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` only when the data volume is created. To change `DB_PASSWORD` or `MYSQL_ROOT_PASSWORD` later, run `ALTER USER` in MySQL first, then update `.env` and `api.env`.