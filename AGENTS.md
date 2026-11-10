# Codex project instructions — JARVIS Android

This repository belongs to a Turkish-speaking user building a **free Android wake-word assistant**. Reply to the user in Turkish. Use the existing Kotlin/Gradle codebase, keep changes small, test each change with the GitHub Actions build, and report actual CI results rather than claiming a device test.

## Goal
- Wake phrase must be the single word **“Jarvis”** (not "Hey Jarvis"), including a *user-started* microphone foreground service after the screen locks when Android permits it.
- Give an audible Turkish response (“Buyurun efendim”) and proceed to a **genuine voice conversation with ChatGPT** wherever an officially supported method exists.
- Do not falsely promise that a background app can open ChatGPT's Voice UI from the Android lock screen. Android background activity launch restrictions, assistant role behavior, and absence of a public ChatGPT Voice launch API must be respected. If seamless ChatGPT voice is not feasible, document limitations and offer a lawful supported handoff (notification or appropriate default assistant integration) or an explicitly separate own-voice-assistant architecture (which may require API costs).

## Existing implementation
- `app/src/main/java/com/razen/jarvis/MainActivity.kt` — simple UI, start/stop, manual speech test, diagnostics and attempt to launch a configured assistant.
- `app/src/main/java/com/razen/jarvis/WakeWordService.kt` — user-started foreground microphone service, on-device openWakeWord, Android TextToSpeech, notification.
- `app/src/main/AndroidManifest.xml` — microphone and foreground service permissions.
- `app/build.gradle.kts` — Android app dependencies.
- `.github/workflows/android-apk.yml` — builds debug APK and publishes the `JARVIS-Android-APK` artifact.
- `jarvis_v1.onnx` is downloaded during CI into `app/src/main/assets/` from the community collection; offline inference at runtime. Confirm model compatibility, origin/license, size/checksum and confidence threshold.

## User-reported problems
1. Earlier 0.2.x APK showed no response when called, including with screen off; cause not verified by device logs.
2. User demands **“Jarvis” only**, and immediate ChatGPT voice interaction on a locked screen.
3. Chat sandbox download link failed on user's device. Use GitHub Actions artifact or GitHub Release for APK delivery instead.
4. 0.3.0 GitHub Actions debug build succeeded on 2026-11-10 but **has NOT been tested on user's Android device**.

## Quality checklist
- Diagnose microphone capture and recognition separately from TTS and handoff, including actual AudioRecord initialization failures and foreground-service state; avoid claiming the detector started successfully simply because the async thread was created.
- Keep notification behavior predictable, permission errors visible, and avoid unnecessary background restarts and battery drain.
- Handle Android 12–16 foreground mic, lock-screen, battery optimization, and microphone indicator constraints without bypass.
- Review and test audio focus/mic handoff (a service with active AudioRecord can block ChatGPT Voice).
- Test on-device (or request device logs, Android release and manufacturer if actual device not available), report what was **built** vs what was **tested**.
- GitHub Actions must complete and publish a reproducible APK artifact for each release. Prefer user-friendly release distribution and document upgrade/signature caveats.
- Respect third-party model licenses; no embedded secrets or paid third-party dependencies.
