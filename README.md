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

- Frontend: Vercel. It calls the API at `https://api.untitles.net/api/v1`.
- Backend: one self-hosted Linux server with Docker Engine and the compose plugin.
- Cloudflare terminates HTTPS and reaches the server through a Cloudflare Tunnel, so the server opens no inbound port for the API. The only inbound port is SSH, for the deploy.
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

- `docker-compose.prod.yml` runs `cloudflared`, `caddy`, `api`, `mysql` and `redis` as the compose project `untitles` on one Docker host. No service publishes a host port; they reach each other on the compose network.
- `deploy/Caddyfile` serves plain HTTP on `:80` for cloudflared. It keeps `X-Forwarded-Proto` and `X-Forwarded-For` from private addresses (the compose network), so the `X-Forwarded-Proto: https` set by Cloudflare reaches Spring and the OAuth redirect URIs stay `https://api.untitles.net/login/oauth2/code/{google,kakao,naver}`. `X-Forwarded-Host` is always set to the request's Host, the API hostname of the tunnel route, so a visitor cannot choose the host name Spring puts into absolute URLs. It logs the visitor address from `Cf-Connecting-Ip`, compresses responses (zstd, gzip) and answers 404 for `/actuator*`.
- `.github/workflows/deploy.yml` builds a `linux/amd64` image on every push to `main` (or a manual run), pushes `ghcr.io/gokid96/untitles-api:<commit sha>` (the full 40-character sha) and, on `main`, `:latest`, then deploys that sha to the server over SSH. Only `main` is deployed: a manual run from another branch builds and pushes its image and skips the deploy job.

### GitHub secrets and variables

Secrets:

| Secret | Purpose |
|---|---|
| `SERVER_HOST` | Public IP or host name that reaches the server's SSH port from the internet (GitHub-hosted runners connect from outside). While it is empty, the deploy job is skipped with a warning. |
| `SERVER_USER` | SSH user on the server, member of the `docker` group |
| `SERVER_SSH_KEY` | Private key of the deploy key (OpenSSH format, no passphrase) |
| `SERVER_SSH_FINGERPRINT` | SHA256 fingerprint of the server's ECDSA host key: `SHA256:` followed by 43 characters. The deploy job fails when it is missing or malformed, and the SSH steps fail when it does not match. |
| `GHCR_TOKEN` | Token the server uses to pull the image (`read:packages`) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Token of the Cloudflare Tunnel. Only the cloudflared container receives it. |
| `MYSQL_ROOT_PASSWORD` | Root password of the MySQL container |
| `DB_USERNAME`, `DB_PASSWORD` | Application database user, created by MySQL on first start. Must not be `root`. |
| `REDIS_PASSWORD` | Redis password |
| `JWT_SECRET`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_ENDPOINT`, `R2_BUCKET_NAME`, `R2_PUBLIC_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | Application settings (see Environment Variables) |

Repository variables (Settings > Secrets and variables > Actions > Variables):

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `22` | SSH port of the server |
| `SERVER_APP_DIR` | `/home/sungmin/untitle` | App directory on the server (absolute path) |

The workflow sets `DB_HOST=mysql` and `REDIS_HOST=redis`. The deploy refuses secret values that contain a single quote, a backslash or a newline; `openssl rand -hex 32` gives safe random passwords.

### First-time setup

1. Prepare the server:
   - Docker Engine with the compose plugin installed system-wide, as Docker's apt packages do. The deploy runs with its own `DOCKER_CONFIG`, so a plugin that exists only under `~/.docker/cli-plugins` is not found.
   - The SSH user in the `docker` group, and the app directory (`SERVER_APP_DIR`) created and owned by that user.
   - Only accounts you trust with production. Everyone in the `docker` group or with `sudo` on the server can read every production secret: `docker inspect` and `docker exec` show the container environments, and they can read `.env`, `api.env` and `.docker/config.json` in `SERVER_APP_DIR`. Other accounts cannot: the files are mode 600, and no secret appears on a command line, where every account could read it in the process list (the deploy copies secrets in files, and Redis reads its password from stdin).
   - Outbound HTTPS (GHCR, Docker Hub) and port 7844 to Cloudflare (UDP for QUIC, TCP for the HTTP/2 fallback).
   - The SSH port reachable from GitHub-hosted runners. Behind a NAT router, forward a public port to the server's SSH port and use the router's public address as `SERVER_HOST`.
2. Create a deploy key without a passphrase and authorize it on the server with forwarding disabled:

   ```bash
   ssh-keygen -t ed25519 -N "" -C github-actions-untitles-deploy -f untitles_deploy_ed25519
   # one line in ~/.ssh/authorized_keys of the SSH user:
   # no-port-forwarding,no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA... github-actions-untitles-deploy
   ```

   The content of the private key file `untitles_deploy_ed25519` goes into `SERVER_SSH_KEY`.
3. Get the host key fingerprint for `SERVER_SSH_FINGERPRINT`. The SSH client inside the actions (Go, `appleboy/drone-ssh` and `drone-scp`) negotiates the ECDSA host key when the server offers ECDSA, Ed25519 and RSA keys, as stock Ubuntu does, and compares the SHA256 fingerprint of that key. On the server, take the second field of:

   ```bash
   ssh-keygen -lf /etc/ssh/ssh_host_ecdsa_key.pub
   ```

   From a trusted network, `ssh-keyscan -p <port> -t ecdsa <host> | ssh-keygen -lf -` prints the same value. A wrong value fails the SSH steps with `ssh: host key fingerprint mismatch`. Update the secret when the host keys change.
4. Create the Cloudflare Tunnel (Cloudflare dashboard, Networking > Tunnels; in the Zero Trust dashboard it is Networks > Tunnels):
   1. Create a tunnel of type cloudflared. On the setup page, copy the token from the shown `cloudflared ... --token <TOKEN>` command into the `CLOUDFLARE_TUNNEL_TOKEN` secret. The compose file runs the connector, so do not install it from that page.
   2. In the DNS records of `untitles.net`, delete any existing record for `api`.
   3. In the tunnel, add a published application route (public hostname) `api.untitles.net` with service `http://caddy:80` (type HTTP, URL `caddy:80`). Cloudflare creates the proxied DNS record.
   4. Keep SSL/TLS > Edge Certificates > Always Use HTTPS on, so plain HTTP is redirected at the edge.
5. Set the secrets and variables above, then push to `main` or run the Deploy workflow.

### Deploy

The deploy job runs for `main` only: pushes, and manual runs (Actions > Deploy > Run workflow) of `main`. It:

1. checks that `SERVER_SSH_FINGERPRINT` has the `SHA256:` form (the SSH actions skip host key verification when it is empty),
2. checks the secrets on the runner and writes `.env` (compose variables, including the tunnel token), `api.env` (api environment) and `.registry-token` (the GHCR token) with mode 600,
3. copies them with `docker-compose.prod.yml` and `deploy/Caddyfile` to `SERVER_APP_DIR`. The archive waits in `SERVER_APP_DIR/.upload` (mode 700) until it is unpacked, and only public values (image name and tag, app directory, registry user) are passed on the SSH command line,
4. logs in to GHCR with `DOCKER_CONFIG=SERVER_APP_DIR/.docker`, so the SSH user's `~/.docker/config.json` is never read or written, and deletes `.registry-token`,
5. pulls the api image of the commit and runs `docker compose up -d --remove-orphans` (never `down`). Only containers whose configuration changed are recreated, normally just `api`,
6. waits up to 5 minutes for `/actuator/health` to report `UP` (checked from the Caddy container), otherwise prints the api logs and fails,
7. reloads Caddy,
8. removes older tags of this app's api image only (no `docker image prune`, so other projects on the host keep their images),
9. waits up to 2 minutes for the cloudflared healthcheck (`cloudflared tunnel ready`, which passes once a connection to Cloudflare is registered), otherwise prints the cloudflared logs and fails with `Tunnel not connected`.

```bash
cd /home/sungmin/untitle
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f api
docker compose -f docker-compose.prod.yml logs cloudflared
```

### Rollback

Set `IMAGE_TAG` in `.env` to the full 40-character sha of an earlier commit and recreate the api container. The image tags are full shas: the 7-character short sha that GitHub shows fails with `manifest unknown`. Older tags are removed from the server after each deploy, so pull the image first, using the deploy's registry login. The next deploy writes its own sha back.

```bash
cd /home/sungmin/untitle
export DOCKER_CONFIG="$PWD/.docker"
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG='<full-40-character-commit-sha>'/" .env
docker compose -f docker-compose.prod.yml pull api
docker compose -f docker-compose.prod.yml up -d api
```

### Database access

MySQL has no published port. Open a client inside the container:

```bash
cd /home/sungmin/untitle
docker compose -f docker-compose.prod.yml exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
```

For a GUI client, tunnel to the container address with your own SSH key (the deploy key does not allow port forwarding) and connect to `127.0.0.1:3307` as `DB_USERNAME`:

```bash
# on the server
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' untitles-mysql-1
# on your machine
ssh -N -p <ssh-port> -L 3307:<container-ip>:3306 <user>@<server>
```

MySQL applies `MYSQL_USER`, `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` only when the data volume is created. To change `DB_PASSWORD` or `MYSQL_ROOT_PASSWORD` later, run `ALTER USER` in MySQL first, then update the secret.

### Building for arm64

The Dockerfile works for any target platform: the Gradle stage runs once on the build machine's own platform (`--platform=$BUILDPLATFORM`) and the run stage only copies the jar, so no emulation (QEMU) is needed. For an arm64 server, change one line of the build step in `.github/workflows/deploy.yml`:

```yaml
platforms: linux/amd64,linux/arm64
```

The images in `docker-compose.prod.yml` and the `eclipse-temurin:21-jre` base image are published for arm64 as well.