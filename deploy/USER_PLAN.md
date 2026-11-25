# User content plans

FREE keeps the existing 20-folder / 50-post limit per workspace. PRO removes only
those limits for workspaces owned by that user. A PRO member of a FREE-owned
workspace does not unlock it. Membership and write permissions remain required.
Workspace count, members, uploads and rate limits are unchanged.

The workspace response adds unlimitedContent (owner entitlement) and keeps
folderLimit=20 and postLimit=50 for compatibility with released clients.
Updated clients display infinity when unlimitedContent is true; old servers
without the field keep the normal count/limit display. No public API can change
a user's plan. No payment UI or purchase flow is included.

## Deployment

1. Back up the production database.
2. Pull this commit on the server (`git pull --ff-only`).
3. Run `deploy/sql/20261125-user-plan.sql` once against the production database
   before rebuilding the API. If the column already exists, inspect it with
   `SHOW COLUMNS FROM users LIKE 'plan';` instead of running ALTER again.
4. Run `docker compose -f docker-compose.prod.yml up -d --build --no-deps api`.
5. Verify internal `/actuator/health` is UP and inspect API startup logs.
6. Verify the identity of account 2, then grant its plan using SQL:

```sql
SELECT user_id, nickname, plan FROM users WHERE user_id = 2;
UPDATE users SET plan = 'PRO' WHERE user_id = 2 AND plan = 'FREE';
SELECT user_id, nickname, plan FROM users WHERE user_id = 2;
```

New requests read the owner's plan from the database, so granting or revoking it
requires no further API restart. Keep plan assignments outside Git.

## Reverting

`UPDATE users SET plan = 'FREE' WHERE user_id = 2;` restores creation limits.
Existing content is retained and may be edited/deleted. Old server code can run
with the extra database column; do not drop it while the new code is running.
