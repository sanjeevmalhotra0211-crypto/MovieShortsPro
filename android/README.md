# MovieShorts Pro — Android source

Upload the **contents** of this folder to the root of a GitHub repository. GitHub Actions builds a debug APK on push to `main` or manual workflow dispatch. No Android Studio or laptop is required for the cloud build; a phone browser/GitHub app is enough to upload source and download the artifact.

The starter Android UI now offers 1, 1.5, 2 and 5 minute clip durations and explains the confirmed 490-Coin once-per-Movie-Project fee. Local clip splitting uses the selected duration. The Android app is not yet connected to the PHP API for live account login, server wallet debit, cloud project sync or payment verification; see `../docs/API.md`.
