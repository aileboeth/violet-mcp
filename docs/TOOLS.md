# Tool list and permissions (1.4.1)

**English** | [简体中文](TOOLS.zh-CN.md)

The server can expose only the 21 **read-only** tools below (1.4.1 added 7 "life state" tools), controlled in two layers:

1. **Layer 1: the read-only allowlist**, hard-coded in [`ReadOnlyPolicy.kt`](../mcp-core/src/main/kotlin/com/mcp/server/core/ReadOnlyPolicy.kt). `ToolRegistry` refuses to register tools outside the list, and both `tools/list` and `tools/call` look only at this registry.
2. **Layer 2: privacy switches**, with defaults in [`PrivacyPolicy.kt`](../mcp-core/src/main/kotlin/com/mcp/server/core/PrivacyPolicy.kt). The user switches each one on the app's "Tools & permissions" page; **they can only be changed on the phone**, and no MCP method can change them. Tools that are off do not appear in `tools/list`, and calling one directly returns `-32602`, like an unknown tool.

Every tool in `tools/list` carries `annotations.readOnlyHint=true` and `destructiveHint=false`.

| Tool | Default | Sensitive | Android permission needed |
|---|---|---|---|
| `get_foreground_app` | **on** | — | Usage access |
| `get_screen_state` | **on** | — | None (usage access optional) |
| `battery` | **on** | — | None |
| `screen_info` | **on** | — | None |
| `health` | **on** | — | None |
| `get_app_usage_stats` | off | yes | Usage access |
| `installed_apps` | off | yes | None |
| `app_info` | off | yes | None |
| `device_info` | off | yes | None |
| `system_properties` | off | yes | None |
| `permission_state` | off | yes | None |
| `storage_info` | off | — | None |
| `locale_info` | off | — | None |
| `time_now` | off | — | None |
| `next_alarm` (1.4.1) | **on** | — | None |
| `audio_output` (1.4.1) | **on** | — | None |
| `now_playing` (1.4.1) | off | yes | Notification access (reads media sessions only, never notifications) |
| `network_state` (1.4.1) | off | yes | None (location optional: to recognise which Wi-Fi) |
| `step_count` (1.4.1) | off | — | Physical activity |
| `calendar_events` (1.4.1) | off | yes | Calendar |
| `phone_events` (1.4.1) | off | — | None (arriving at / leaving places needs location, optional) |

When a sensitive tool is turned on, the app shows a dialog explaining what information it exposes. Since 1.4.1 every item on the "Tools & permissions" page shows a plain name and a one-sentence plain-language explanation
(`ToolKit.PLAIN`: "what Violet can know once this is on"), with the tool name and the description for the model in the details; "Grant" directly brings up the matching
system permission request (runtime permissions) or opens the matching system settings page (usage access, notification access).

## 1.5 Life state (1.4.1)

| Tool | Returns | Notes |
|---|---|---|
| `next_alarm` | `hasAlarm`, `time`, `minutesUntil` | `AlarmManager.getNextAlarmClock()`; alarms set by any alarm app count |
| `now_playing` | `playing`, `sessions[]` (`app`, `title`, `artist`, `album`, `playing`) | `MediaSessionManager`; the system requires notification access, and `MediaListenerService` handles no notifications at all |
| `audio_output` | `headphonesOrBluetooth`, `devices[]`, `musicActive` | `AudioManager.getDevices` |
| `network_state` | `type` (wifi / cellular / ethernet / none / other), `place` | `place` is the name the user gave the current Wi-Fi in the app (e.g. "home"); the Wi-Fi name (SSID) is only compared on the phone and is **never returned** |
| `step_count` | `stepsToday`, `countedSince` | Step counter sensor; while the service runs it records readings in 15-minute batches, carrying over midnight and reboots |
| `calendar_events` | `events[]` (`title`, `allDay`, `start`, `end`) | Argument `days` (1–7, default 2); does not read location, attendees or notes; skips declined events |
| `phone_events` | `epoch`, `latestSeq`, `events[]` (`seq`, `type`, `at`, `detail`) | Things happening on the phone: `unlock` (with `screenOffMinutes`), `screen_on` / `screen_off`, `charger_connected` / `charger_disconnected`, `headphones_connected` / `headphones_disconnected`, `place_arrived` / `place_left` (with `place`). Recorded while the service runs from dynamically registered system broadcasts, `AudioDeviceCallback` and network callbacks, kept only in memory (up to 200, never written to a file); arguments `after` (the previous `latestSeq`) and `limit`. Violet fetches new events once a minute (this "push" does not go through the relay, so the relay needs no changes); a changed `epoch` means the service restarted |

Do Not Disturb is not read (the user keeps DND on all the time and relies on a watch for alerts, so DND does not mean "busy").

## 1. Phone sensing

All sensing tools use official Android APIs (`UsageStatsManager`, `PowerManager`, `KeyguardManager`, `DisplayManager`), with no root and no accessibility service.

### `get_foreground_app`

| Argument | Notes |
|---|---|
| `lookbackMinutes` | How far back to look for system events, 1–1440, default 60 |

By default only these 5 fields are returned:

```json
{"state": "current", "app": "Example App", "package": "example.package", "observed_at": "2026-10-10T10:02:11.000+08:00", "age_seconds": 2}
```

| state | Condition | app / package |
|---|---|---|
| `current` | Screen on, unlocked, and no leave event since the app came to the foreground | Given |
| `screen_off` | Screen off (read in real time) | null |
| `locked` | Sitting on the lock screen (read in real time) | null |
| `unknown` | Cannot be determined: the app left the foreground (e.g. the notification shade is open), no events in the lookback range, or not unlocked yet since the phone restarted | null |
| `permission_denied` | Usage access not granted (with an extra `hint`) | null |

- `observed_at` is the time of the latest system event supporting the conclusion, and `age_seconds` is how many seconds ago that was.
- In the `current` state, `observed_at` is when the app came to the foreground; a large `age_seconds` only means the user has stayed in that app for a long time.
- With stale data, a locked screen or missing permission, only a clear state is returned, and **the current app is never guessed**.
- No activity history and no device identifiers are returned.

**Detailed mode**: after the user turns on "Foreground app: detailed mode" on the "Tools & permissions" page, results also include:

- `reason`, `explanation`
- `event_count`
- `last_app`, `last_package`, `last_seen_at` in non-current states

### `get_app_usage_stats` (sensitive, off by default)

| Argument | Notes |
|---|---|
| `range` | `last_15m` / `last_1h` / `last_3h` / `last_24h` / `today` / `yesterday` / `last_7d`, default `today` |
| `start`, `end` | A custom range. ISO-8601 (e.g. `2026-10-09T08:00`, `2026-10-09T08:00+08:00`), a date (e.g. `2026-10-09`) or epoch milliseconds; `end` defaults to now |
| `limit` | 1–100, default 20 |
| `includeHomeScreen` | Whether the launcher counts in the list, default false (launcher time goes separately into `homeScreenMs`) |
| `method` | `events` (default, computed exactly from events, up to 10 days) or `system` (the system's daily aggregates; range boundaries are not exact) |

Returns:

- `apps[]`: for each app `appName`, `packageName`, `foregroundMs`, `foreground`, `sessions`, `lastUsed`, `inForegroundAtEnd`
- `totalForegroundMs`
- `range`
- `queryTime`
- `freshness`: `dataThrough`, `earliestEventInRange`, `possiblyIncomplete`, `note`

Time with the screen off does not count as usage.

### `get_screen_state`

| Field | Meaning |
|---|---|
| `screenOn` | The value of `PowerManager.isInteractive` |
| `displayState` | `ON` / `OFF` / `DOZE` / `DOZE_SUSPEND` / `ON_SUSPEND` / `VR` / `UNKNOWN` |
| `keyguardLocked`, `deviceLocked`, `deviceSecure` | Lock-screen related states |
| `summary` | A one-sentence summary |
| `stateSince`, `stateDurationSeconds` | When the current screen-on or screen-off state began. Needs usage access, and is given only when the latest screen on/off event matches the current state |
| `queryTime`, `freshness` | `realtime=true` |

## 2. Device information

| Tool | Contents |
|---|---|
| `battery` | Level, charging state, power source, temperature, voltage, health, battery saver, Doze, battery optimisation exemption |
| `screen_info` | Resolution, density, refresh rate, brightness setting |
| `storage_info` | Capacity of internal, external and removable storage (no file is accessed) |
| `locale_info` | Language, time zone |
| `time_now` | The phone's current time |
| `device_info` | Brand, model, Android version, security patch, ABI, fingerprint (no serial number, no baseband) |
| `system_properties` | Build properties from a fixed allowlist; **since 1.2 only the public `Build` API is used**, no more reflective calls to hidden APIs |

## 3. App information (sensitive, off by default)

| Tool | Arguments | Notes |
|---|---|---|
| `app_info` | `packageName` (required) | Name, version, install and update time, whether it is a system app, whether it can be launched, whether it is the launcher |
| `installed_apps` | `keyword`, `includeSystem`, `limit` | Apps with a launcher icon |

`QUERY_ALL_PACKAGES` is not requested. The manifest only declares the launcher and HOME intents with `<queries>`.

## 4. Server

| Tool | Notes |
|---|---|
| `health` | Version, service state (`running` / `restricted` / `stopped`), uptime, relay stage, latest successful connection, number of tools on; no secrets |
| `permission_state` (sensitive) | State of usage access, notifications, battery optimisation exemption, WakeLock and start on boot |

## 5. Removed tools (cannot be listed or called)

| Category | Tools |
|---|---|
| Command / script execution | `shell` (app / Shizuku / root), `shizuku`, `mcp_javascript` |
| Modifying and deleting files | `write_file`, `edit`, `create_directory`, `copy_file`, `move_file`, `delete_file`, `touch`, `compress_zip`, `extract_zip`, `web_download`, `set_workspace` |
| Reading files | `get_workspace`, `list_files`, `read_file`, `file_info`, `compare_files`, `file_search`, `continue` |
| App control | `stop_app` |
| Clipboard / notifications | `clipboard`, `send_notification` |
| Arbitrary network requests | `http_request`, `shorten_url` |
| Bypassing the dispatch layer | `batch` |
| Other | `running_processes`, `check_permission`, `json_format`, `text_convert`, `base64_encode`, `decrypt_xor`, `help`, `tools_list`, `stats`, and all MCP bridge tools |

Calling any of these names (including variants with different case, leading/trailing whitespace, bridge prefixes, duplicate keys and so on) returns `-32602 Unknown tool`.

## 6. Android permissions

| Permission | Purpose | When requested |
|---|---|---|
| `INTERNET` | Local endpoint, TLS relay | At install |
| `ACCESS_NETWORK_STATE` | Reconnect immediately after a network change; pause retries without a network | At install |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Long-running service (`specialUse`; dataSync is limited to 6 hours a day on Android 15 and cannot start on boot) | At install |
| `RECEIVE_BOOT_COMPLETED` | Resume after boot (the user must turn on "Start on boot" and must not have stopped the service by hand) | At install |
| `WAKE_LOCK` | A short wake lock of at most 10 seconds per request; a permanent WakeLock is off by default | At install |
| `POST_NOTIFICATIONS` | Foreground service notification (optional) | The first time "Start" is tapped |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | "Unrestricted battery" for this app only (optional); global battery saving does not need to be turned off | When the user taps "Request" on the settings page |
| `PACKAGE_USAGE_STATS` | Foreground app, usage time | Turned on by the user by hand in the system settings |
| `REQUEST_INSTALL_PACKAGES` (1.4.0) | Installing this app's own updates: only packages with the same package name, a higher version, a signing certificate chain containing this app's certificate, and a SHA-256 matching the release (`AppUpdater`); unrelated to the MCP tools | The user turns on "Install unknown apps" for this app in the system settings |
| `ACTIVITY_RECOGNITION` (1.4.1) | `step_count` | When the user taps "Grant" on "Tools & permissions" |
| `READ_CALENDAR` (1.4.1) | `calendar_events` (read-only) | Same as above |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (1.4.1, optional) | The system requires location permission to read the Wi-Fi name; used only to recognise Wi-Fi networks that were given a name, never to read the location | Same as above |
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`, 1.4.1) | `now_playing` reads media sessions; the service handles no notifications | Turned on by the user in the system settings |

There is also `com.mcp.server.violet.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, added automatically by androidx.core and usable only by this app itself.

Permissions not needed: root, Shizuku, accessibility service, all-files access, `QUERY_ALL_PACKAGES`, overlay, modifying system settings, phone, SMS, camera, microphone.
(Location permission is optional since 1.4.1, only for recognising Wi-Fi; see the table above.)

Other settings:

- `allowBackup=false`, and the data extraction rules exclude all data.
- `usesCleartextTraffic=false`.
- Only the launcher Activity, the boot broadcast receiver and (1.4.1) `MediaListenerService` are exported components; the boot receiver only responds to protected system broadcasts,
  and `MediaListenerService` can only be bound by the system (`BIND_NOTIFICATION_LISTENER_SERVICE`).
