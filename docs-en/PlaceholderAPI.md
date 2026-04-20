# 🔖 PlaceholderAPI

KaGuilds fully supports PlaceholderAPI (PAPI). You can use KaGuilds placeholders anywhere that supports PAPI, including chat, scoreboards, BossBars, and Tab lists.

## 📋 Basic Information

### Variable Prefix

All KaGuilds placeholders use `kaguilds` as the prefix:

```
%kaguilds_xxx%
```

### Installation Steps

1. Download and install [PlaceholderAPI](https://www.spigotmc.org/resources/6245/)
2. Place `KaGuilds.jar` and `PlaceholderAPI.jar` in the `plugins` folder
3. Restart the server — PlaceholderAPI will automatically load the KaGuilds expansion
4. Use `/papi parse player %kaguilds_name%` to test the placeholder

## 🎯 Placeholder List

### 📌 Guild Information

| Placeholder | Description | Example |
|:-----------|:------------|:--------|
| `%kaguilds_name%` | Guild name | `My Guild` |
| `%kaguilds_id%` | Guild ID | `5` |
| `%kaguilds_level%` | Guild level | `3` |
| `%kaguilds_balance%` | Guild balance | `1500.0` |
| `%kaguilds_announcement%` | Guild announcement | `Welcome!` |
| `%kaguilds_owner%` | Owner's name | `Player1` |
| `%kaguilds_create_time%` | Creation time | `2024-01-15 10:30:00` |
| `%kaguilds_exp%` | Current experience | `1500` |
| `%kaguilds_need_exp%` | Experience needed for next level | `2000` |

### 👥 Member Information

| Placeholder | Description | Example |
|:-----------|:------------|:--------|
| `%kaguilds_member_count%` | Current member count | `15` |
| `%kaguilds_max_members%` | Maximum members | `20` |
| `%kaguilds_member_list%` | Member list | `Player1, Player2, Player3` |
| `%kaguilds_contribution%` | Personal contribution | `500` |

### 🔐 Rank Permissions

| Placeholder | Description | Example |
|:-----------|:------------|:--------|
| `%kaguilds_role_name%` | Rank name | `Admin` |
| `%kaguilds_is_owner%` | Is owner | `Yes/No` |
| `%kaguilds_is_admin%` | Is admin | `Yes/No` |
| `%kaguilds_is_staff%` | Is staff member | `Yes/No` |

### ⚔️ PvP Data

| Placeholder | Description | Example |
|:-----------|:------------|:--------|
| `%kaguilds_pvp_wins%` | Win count | `10` |
| `%kaguilds_pvp_losses%` | Loss count | `3` |
| `%kaguilds_pvp_draws%` | Draw count | `2` |
| `%kaguilds_pvp_total%` | Total matches | `15` |

### 📝 Other Information

| Placeholder | Description | Example |
|:-----------|:------------|:--------|
| `%kaguilds_pending_requests%` | Pending join requests | `3` |
| `%kaguilds_has_guild%` | Player has a guild | `Yes/No` |
| `%kaguilds_reset_countdown%` | Daily task reset countdown (HH:MM:SS) | `5h 30m 20s` |
