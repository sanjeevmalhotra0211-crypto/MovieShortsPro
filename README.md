# MovieShorts Pro — Android source (GitHub repository)

This is the native Android project. Upload the **contents of this folder** to a GitHub repository (the `settings.gradle.kts` file should be at repository root).

## Build
- Open this folder in Android Studio, or use the included GitHub Actions workflow.
- GitHub Actions builds a debug APK on push to `main` or manual workflow dispatch.
- The workflow uses Gradle 8.9 and Java 17.

## Branding
Option A brand assets are in `branding/`. Android app label and drawable brand mark are configured in the app source.

## Current scope
This is an early Android MVP, not the full requested production app. Login, remote PHP API integration, coin wallet, Cashfree payments, manual payments, full editor, Facebook publishing, and production signing are not implemented yet. A successful Android build/device test is still required.
