# MovieShorts Pro — Android local-processing MVP

This repository contains an early native Android MVP source package. It is **not a production release** and has not yet passed a real Android build or physical-device test.

## Implemented in source
- Kotlin + Jetpack Compose dark, mobile-first dashboard.
- User must enter a project name before selecting a movie.
- Android system document picker selects a local video.
- Media3 Transformer creates sequential clips of up to 90 seconds; the final clip can be shorter.
- Clips are kept in app-specific Movies storage, separated by project folder.
- Previously exported clips are scanned and restored into the in-app list on next launch.
- Each clip can be previewed/shared, or copied to a user-selected phone folder using Android's Save dialog.
- GitHub Actions workflow builds a debug APK and uploads it as a workflow artifact.

## Build without Android Studio
1. Create a GitHub repository and upload the contents of this folder to its `main` branch.
2. Open the repository's **Actions** tab.
3. Run **Android APK** using **Run workflow**, or push a commit to `main`.
4. Open the successful workflow run and download the `MovieShortsPro-debug-apk` artifact.
5. Extract the artifact ZIP on your Android phone and install the APK. Android may ask you to allow installation from your browser/file manager.

The workflow uses GitHub-hosted runners and Gradle 8.9. This environment did not execute that workflow; the first successful GitHub Actions run is the actual build verification.

## Android output
Working clips are initially stored in:
`Android/data/com.movieshorts.pro/files/Movies/<ProjectName>/`

Use **Save to phone** on a clip to choose a normal user-visible destination such as Movies or Downloads. Android controls the destination through the system file picker.

## Not implemented
- Full-featured timeline editor, trim/crop/rotate/reframe 9:16, text overlays, filters, stickers, audio mixing.
- Durable project database and project-specific browsing/filtering UI.
- WorkManager/foreground-service processing that survives process death during an export.
- User signup/login, password reset, server-side user accounts and cloud sync.
- Production backend, database, queue, quotas, monitoring and backups.
- Secure admin authentication, MFA, audit logs and live user controls.
- Meta OAuth, Page selection, publishing permissions, App Review and real Reel publishing.
- Deployment of `https://edit.freesite.us.cc` or `/admin`.
- Signed production APK, privacy/terms pages, release signing and device test matrix.

## Security requirements
- Never collect Facebook passwords. Use Meta's official OAuth authorization and supported Page publishing API.
- Never ship a default admin password or put secrets in source control.
- Do not publicly deploy the current backend scaffold. It is a development-only sample without account authentication, ownership checks or production hardening.
- Do not claim Facebook profile publishing; supported Page publishing and Meta permission review are required.

## Before production
Set up GitHub repository access, a hosting provider, database, DNS/HTTPS access, Meta developer app and Page, release signing secret, privacy policy, terms, copyright policy and support contact.
