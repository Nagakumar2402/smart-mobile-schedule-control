# Smart Mobile Schedule Control

Schedule a message now and send it from WhatsApp, SMS or another app at the right time.

At the scheduled time:

| Target | What happens |
|---|---|
| SMS | Sent automatically if SMS permission is allowed |
| WhatsApp, or a chosen app such as ChatGPT | A notification rings; tap it and press Send |

A notification also rings if an SMS fails or is more than 15 minutes late.
The app does not use Accessibility.

## Features

- One-time or daily schedules
- Edit a task, and choose its notification sound
- WhatsApp, SMS, or any app that accepts shared text
- For "Other app", pick an installed app (for example ChatGPT)
- Turn schedules on and off, delete them, or send one immediately
- Alarms are restored after reboot, app update and clock changes
- Data stays on the phone (no internet permission, no backup)

## Android requirements

Android 8.0 (API 26) or newer. WhatsApp must be installed to send through WhatsApp.

## GitHub repository setup

1. Create an empty repository on GitHub.
2. From the unzipped project folder:

   ```sh
   git init -b main
   git add .
   git update-index --chmod=+x gradlew
   git commit -m "Initial app"
   git remote add origin https://github.com/YOUR_NAME/YOUR_REPO.git
   git push -u origin main
   ```

Use git or GitHub Desktop. Browser drag-and-drop can skip hidden folders such as `.github`,
and then no workflow runs.

## Running GitHub Actions

The **Build Android APK** workflow runs on every push to `main` and on every pull request.
To run it by hand: **Actions → Build Android APK → Run workflow**.

## Downloading the APK

Open a successful run, scroll to **Artifacts**, and download
`Smart-Mobile-Schedule-Control-APK`. It is a zip containing `app-debug.apk`.
Artifacts are deleted after 14 days.

## Installing

1. Unzip and copy `app-debug.apk` to the phone.
2. Open it and allow your file manager or browser to install unknown apps.

Each CI run signs the debug APK with a new key, so a newer build may refuse to install over
an older one. Uninstall the old app first (this deletes your schedules).

## Permissions

- **SMS:** tap **Allow SMS** in the app. Carrier charges apply.
- If SMS is greyed out or blocked: Settings → Apps → Smart Schedule → ⋮ → **Allow restricted settings**.
- **Notifications:** allow when asked, or Settings → Apps → Smart Schedule → Notifications.
- **Exact alarms** (Android 12+): tap the warning in the app, or Settings → Apps →
  Special app access → Alarms & reminders. Without it reminders can be late.
- On phones with aggressive battery saving, set the app's battery use to unrestricted.

## Building without Android Studio

Needs JDK 17 and the Android SDK command-line tools, with `ANDROID_HOME` set.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Release APK

CI builds only the debug APK. For a release build, create a key once and keep it safe:

```sh
keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000
./gradlew assembleRelease
BT="$ANDROID_HOME/build-tools/35.0.0"
"$BT/zipalign" -p 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
"$BT/apksigner" sign --ks release.jks --out app-release.apk aligned.apk
```

Never commit the keystore or passwords. If you later sign in CI, store them as the GitHub
secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
The current workflow does not read them.

## Troubleshooting

| Problem | Fix |
|---|---|
| No workflow in the Actions tab | Check `.github/workflows/build-android.yml` was pushed |
| `Permission denied: ./gradlew` | `git update-index --chmod=+x gradlew`, commit, push |
| Build fails | Open the red step; the first line starting `e:` or `error:` is usually the cause |
| Lint fails | Read the lint step log for the file and line |
| No notification | Check both permissions and battery settings; force-stopping the app cancels alarms until you open it again |
| "App not installed" | Uninstall the older build first |
