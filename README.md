# LogTime

Automatic time tracking for JetBrains IDEs that logs your coding sessions as Jira worklogs.

![Build](https://github.com/sangharshgautam/logtime/workflows/Build/badge.svg)

## What it does

LogTime runs silently in the background while you work. It records heartbeats as you edit files,
grouping them into uninterrupted coding sessions per issue, and then posts those sessions to Jira
as worklogs — no timers, no manual entries.

- Tracks time and edits per project, per file, per issue.
- Never blocks your work: persistence is crash-safe and runs off the UI thread.
- Only removes a session from its durable queue after Jira confirms the worklog POST.

## How it works

1. Every edit/save produces a **heartbeat** (project, file, cursor position, timestamp).
2. Heartbeats are appended to a durable JSONL queue at `~/.logtime/heartbeats.jsonl` — the single
   source of truth, so nothing is lost on crash or restart.
3. Heartbeats are merged into **sessions**: consecutive heartbeats within 5 minutes of each other
   are grouped; sessions that belong to different issues are split.
4. Each session is posted to Jira as a worklog. Sessions are only removed from the queue after a
   successful POST. Failed posts retry up to 3 times before being dropped.
5. Two IDE instances never double-post: heartbeats are claimed before posting, and a lock file
   serializes writes between processes.

## Requirements

- Any JetBrains IDE built on the IntelliJ Platform **2026.1 or later** (IntelliJ IDEA, PyCharm,
  WebStorm, GoLand, CLion, etc.).
- A Jira instance with a **personal API token** and a user able to view the projects you work in.
- The Jira issue key is taken from the project name (e.g. a project opened from a `MYPROJECT` repo
  is matched to `MYPROJECT-*` issues). Sessions with no matching issue key are skipped.

## Installation

- **From JetBrains Marketplace** — once published:
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> >
  <kbd>Search for "LogTime"</kbd> > <kbd>Install</kbd>
- **Manually** — download the latest release
  (https://github.com/sangharshgautam/logtime/releases) and install it via
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> >
  <kbd>Install plugin from disk...</kbd>

## Testing pre-release builds

Test builds are published as **hidden releases**: they are approved by JetBrains but never listed
publicly. To test one, open the direct Marketplace version link shared with you and follow the
install prompt in your IDE. New versions are published by pushing a `vX.Y.Z` tag (or manually from
the GitHub Actions *Publish* workflow).

## Configuration

Open <kbd>Tools</kbd> > <kbd>LogTime Settings</kbd> and enter:

| Setting     | Description                                                        |
|-------------|--------------------------------------------------------------------|
| Jira URL    | Your Jira instance, e.g. `https://your-company.atlassian.net`      |
| Username    | Your Jira username or email                                        |
| API token   | Your Jira personal API token (Basic auth)                          |
| Proxy       | Optional HTTP proxy in `host:port` form (e.g. `proxy.corp:8080`)   |
| Debug       | Verbose logging to the IDE log                                     |

Data is stored in `~/.logtime/`:

- `logtime.cfg` — configuration (the settings you enter in the dialog).
- `heartbeats.jsonl` — the durable heartbeat queue.

Both locations can be redirected by setting the `LOGTIME_HOME` environment variable.

## Development

Requires JDK 21+ and Gradle (via the included wrapper).

```sh
./gradlew build          # compile, test, and package
./gradlew clean :test    # run the test suite only
./gradlew runIde         # launch a sandbox IDE with the plugin
```

The build uses the IntelliJ Platform Gradle Plugin. Plugin details live in
`src/main/resources/META-INF/plugin.xml`; the version and group come from `gradle.properties`.