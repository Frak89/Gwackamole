# Gwackamole 🐾

Android digital-wellbeing prototype: a playful whack-a-mole challenge before distracting app use and over potentially adult search results.

## MVP scope (work in progress)

- **Individual app timer** per manually blacklisted app, plus random challenges.
- **Accessibility overlay:** a static white banner with a custom-drawn cartoon mole covers only visible search-result tiles containing adult terms, for supported Android browsers. Touching the banner opens a challenge; other results remain clickable. The search box is never intentionally covered.
- **Progressive difficulty:** the first detected access attempt starts with one mole. Repeated attempts against the same locally hashed visible result increase difficulty by one, capped at 10. More moles, more required hits, and less time.
- **Rewards:** success grants a limited allowance; failure applies a cooldown and calls \`lockNow()\` only if the user expressly activated Device Admin.
- **Navigation restriction:** kiosk/lock-task is engaged during the minigame **only if the app is an actual Device Owner**. Normal Accessibility and Device Admin permissions cannot block Android system gestures.
- **Safety:** emergency long-press exit remains available. No raw search queries or URLs are uploaded. No automatic crawling of adult websites in this proof of concept.

## Limitations and planned upgrades

The prototype is **not** a completed AppBlock equivalent. In particular:

1. Google/Bing result text detection is a **heuristic**, not reliable domain classification. It may produce false positives, and will miss websites/content not exposed through Android accessibility nodes. Review candidate domains before adding them to a permanent block list.
2. Browser site-level block for all traffic requires a carefully designed **VPNService/DNS** layer, potentially with offline curated domain feeds. HTTPS URL paths or page content are not visible through DNS alone. Treat detection of adult content as a separate on-device classifier, never as guaranteed detection.
3. App timer enforcement and Android background activity start restrictions need device tests, especially on Android 17; a normal app cannot guarantee enforcement against all ways to bypass it.
4. A **Device Owner** requires approved device provisioning, typically a new/reset test device, not merely toggling a permission. Do not factory-reset a personal device without a verified backup.
5. The current permissions/onboarding screen is basic. **Cooldown before loosening settings**, explicit per-app baseline locking, safe allowlisting, optional curated adult-domain feeds, and bypass detection are future development items and are not claimed implemented.
6. This prototype is for **self-directed, consented** focus management, not hidden monitoring.

## Build

Android Studio, JDK 17, Android SDK 35, Gradle 8.11.1. From the repository, use \`gradle :app:assembleDebug\`. GitHub Actions also builds a debug APK artifact.

## Configure

Launch Gwackamole and explicitly enable its Accessibility service. Grant Device Admin only if you want the screen to lock after a lost challenge. On a managed test device configured as Device Owner, lock task can stop system navigation during the minigame; it is never enabled silently.

## Privacy

Matching visible texts are hashed locally for difficulty counters. Detection and decisions are on device. Search queries and raw visited-site lists are not transmitted to Gwackamole servers.
