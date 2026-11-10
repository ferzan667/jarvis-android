# JARVIS Android

Android voice wake-word assistant prototype.

- Say **Hey Jarvis** after starting the microphone service.
- Wake phrase detection is local via openWakeWord Android.
- Speaks **Buyurun efendim** using Android Text-to-Speech.
- Shows a notification to **manually open ChatGPT**.
- Does **not** unlock the device, trigger ChatGPT voice mode automatically, or provide a full Siri replacement.
- Debug APK is built by GitHub Actions; see **Actions → JARVIS Android APK → Artifacts**.
- Source code requires Android 8.0+; real locked-screen behavior depends on device/battery policies.

No paid automation apps or OpenAI API key required.

⚠️ **Experimental:** generated debug APK must be tested on a phone. Background microphone use may consume battery.

The openWakeWord code is Apache-2.0; pretrained wake-word models are CC BY-NC-SA 4.0 (noncommercial).
