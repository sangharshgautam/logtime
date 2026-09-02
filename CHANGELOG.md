<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# logtime Changelog

## [0.1.0] - 2026-09-02

### Added

- Automatic time tracking that logs consolidated coding sessions as Jira worklogs.
- Status bar widget showing the current LogTime state (toggleable in settings).
- Jira integration configured from Tools -> LogTime Settings: Jira URL, username, API token, and HTTP proxy support.
- Durable on-disk heartbeat queue that survives crashes and IDE restarts.
- Multi-instance safety so sessions are not double-posted when multiple projects (or IDE windows) are open.

[LogTime]: https://github.com/sangharshgautam/logtime
