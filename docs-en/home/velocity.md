# Velocity Setup

KaGuilds network mode stores persistent data in a shared MySQL database. The KaProxy Guilds module forwards chat, invitations, notifications, and cache synchronization messages.

## Topology

Every backend server must:

- Run the same KaGuilds version
- Connect to the same MySQL database
- Set `proxy: true`
- Use a unique and stable `server-id`

Install KaProxy only on Velocity. Do not install the backend KaGuilds JAR on the proxy. Enable the Guilds module in KaProxy so it can exchange cross-server messages with KaGuilds on each backend.

## Install the Proxy Plugin

1. Place `KaProxy.jar` in Velocity's `plugins` directory.
2. Restart Velocity.
3. Open `plugins/kaproxy/config.yml`.
4. Ensure both `modules.guilds.enabled` and `legacy-channel-enabled` are `true`.

## Configure Backend Servers

Use identical database settings in every backend `plugins/KaGuilds/config.yml`, but assign a different `server-id` to each server:

```yaml
proxy: true
server-id: survival

database:
  type: "MySQL"
  host: "127.0.0.1"
  port: 3306
  db: "kaguilds"
  user: "kaguilds"
  password: "replace_with_a_strong_password"
```

For example, use `survival`, `resource`, and `lobby` for three different backends. Restart every backend normally after changing these values.

## Verification

1. Connect test players to two different backends.
2. Create or join the same guild.
3. Verify guild chat, invitations, join request notifications, and membership changes across servers.
4. Open a guild vault on one backend and confirm that the same vault cannot be opened on another backend.
5. Test leave, kick, and guild deletion, then confirm caches are cleared on the other backend.
6. Check Velocity and every backend console for plugin messaging and database errors.

## Security and Operations

- Backend servers must accept connections only from Velocity and must not be directly exposed to players.
- Use a least-privilege MySQL account, never `root`, and restrict allowed database source addresses.
- Keep the KaGuilds version, menus, and language configuration aligned across all backends.
- Do not change `server-id` or the database while the network is running.
- Plugin messages generally require an online player as a transport; some immediate notifications may be delayed while a network is empty.
- Back up the shared database and each server configuration before proxy or backend upgrades.
