# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| Latest  | Yes       |
| Older   | No        |

Only the latest release receives security fixes.

## Reporting a Vulnerability

If you discover a security vulnerability in Copper Weather, please report it responsibly:

1. **Do NOT open a public GitHub issue.**
2. Use [GitHub Security Advisories](https://github.com/danielcopper/copper-weather/security/advisories/new) to report
   privately.
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

You should receive a response within 7 days.

## Scope

Copper Weather signs in with Home Assistant as a normal (non-admin) user and reads one sensor made for the app (D2,
D3 in [docs/decisions.md](docs/decisions.md)). It handles:

- **A Home Assistant refresh token**, kept on the phone, with which the app gets short-lived access tokens. It grants
  what that user may do in Home Assistant, so it is a credential to protect even though the app only reads.
- **Requests** to the Home Assistant address the user signs in with, and to nothing else.
- **A snapshot** of the sensor's last good answer, kept on the phone for the widgets.

### Known security considerations

- Nothing the app keeps is backed up or moved to a new device (Android's backup and device transfer exclude all of
  the app's data); after a new install the user signs in again.
- The app has no account or server of its own: it talks only to the user's Home Assistant.

The current version signs in and keeps the refresh token, sealed under a key in the Android Keystore (D26); it
fetches nothing yet and keeps no snapshot.
