# Production gates — MovieShorts Pro

This repository is an Android MVP source package, not a deployed production service.

## Implemented in this package
- Native Android Compose dashboard.
- Android system video picker.
- Required project name before selecting a movie.
- Sequential local clip export, with each segment up to 90 seconds.
- Per-clip open/share action.
- GitHub Actions workflow to build a debug APK artifact.

## Not implemented / not verified
- Persistent project database and reliable project restoration after app restart.
- Foreground service / WorkManager processing that safely survives process death.
- Full editor (trim UI, crop, rotation, 9:16 reframing, text overlays, filters, stickers, audio mixing).
- User registration, login, password reset, verified email, account ownership and API authorization.
- Production admin authentication, MFA, audit logs, user moderation APIs.
- Production backend, database migrations, job queue, monitoring and backups.
- Meta OAuth, Page selection, required permissions, App Review and live publishing.
- Deployment of `https://edit.freesite.us.cc` or its `/admin` route.
- Signed release APK and device compatibility test matrix.

## Important safety decisions
- Never collect Facebook passwords in this app. Use Meta's official OAuth authorization and short-lived/long-lived tokens handled server-side.
- Do not publish the current backend scaffold to the public internet. It has no authentication or per-user ownership checks.
- Do not use a shared/default admin password. Configure a unique secret out-of-band, store only a password hash, enable MFA and rotate credentials.
- Android local app-specific storage is not the same as a user-visible permanent folder. Add a user-selected export destination (Storage Access Framework) before promising permanent exports.
- Video export may fail for unsupported codecs, damaged files, low storage, thermal limits or Android vendor-specific behavior. Always test on physical devices.

## Required external setup before production
1. A GitHub repository and permission to push this project.
2. A hosting provider, database and secrets manager for the API/admin.
3. DNS access for `edit.freesite.us.cc` and HTTPS certificate setup.
4. Meta developer app, business/Page access, OAuth redirect URI, publishing permissions and App Review if required.
5. Privacy policy, terms, copyright policy, retention policy and support contact.
6. Release signing key stored as a GitHub Actions secret (never commit it).
