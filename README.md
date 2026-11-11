# JARVIS Android

A Kotlin Android voice assistant starter with:
- Compose dashboard
- Floating overlay orb (user grants "Display over other apps")
- Speech recognition and spoken replies
- Local commands for app launching, web search, dialer, message composer, and settings
- Optional OpenAI-compatible chat endpoint for hybrid AI
- Optional AccessibilityService screen text reading (must be explicitly enabled by the user)
- GitHub Actions APK build

## Important Android limitations
- Android does not permit an app to silently control every other app. Actions use public Android intents and explicit user interaction.
- Calls open the dialer; messages open a prefilled composer. The user confirms before sending/calling.
- Bluetooth, Wi-Fi, permissions, and many protected settings open the relevant Settings screen rather than changing protected state directly.
- "Hey Jarvis" is supported while the assistant's microphone listening session is active. Reliable always-on hotword listening is restricted by Android background microphone rules, battery management, and device vendor behavior. This starter does not secretly record audio.
- Screen reading requires manually enabling the included Accessibility Service in Android Settings. It reads visible text only and does not click or enter text in other apps.
- Do not put API keys in source code or commit them to GitHub. For a production app, route AI requests through your own authenticated backend. The optional direct API-key setting is for local testing only and can be extracted from an APK.

## Build
Open in Android Studio, or push to GitHub and use the included workflow.
The workflow uploads `app-debug.apk` as an Actions artifact.

## Configure optional AI
In the app's AI settings, enter an OpenAI-compatible endpoint, model, and API key. Example endpoint:
`https://api.openai.com/v1/chat/completions`
The API key is stored locally in app preferences on the device. This is a prototype convenience, not secure secret storage.

## Termux upload
1. Extract this ZIP.
2. `cd` into the extracted `JarvisAndroid` folder.
3. Initialize or clone your own GitHub repository.
4. `git add . && git commit -m "Initial JARVIS Android app" && git push`
5. In GitHub, open Actions and run the Android APK workflow.
