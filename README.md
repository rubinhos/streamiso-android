# StreamISO for Android

An Android app for [StreamISO](https://github.com/rubinhos/streamiso), a self-hosted library that lists your DVD
images (`.iso`) and audio CD images (BIN/CUE) and plays them with the real VLC, menus included, over a WebRTC stream.

The app is a thin native shell around the web interface your StreamISO server already provides. It adds what a
browser tab cannot do: a home screen icon, full screen with the phone locked in landscape for DVDs, a screen that
stays on while playing, and a Back button that stops the disc.

> **Status: early prototype, not yet tested on a device.** See [What has been tested](#what-has-been-tested).

## About this project (read this first)

**This is a "vibe coded" project,** like StreamISO itself: it was written with an AI assistant
([Claude](https://claude.ai) by Anthropic) and is tested by its author, Rubens, who directs the work. It has had no
formal code review or security audit. Treat it as a home-lab tool and read the code before you trust it.

## Requirements

- Android 11 or newer.
- A running StreamISO server that the phone can reach on your local network, with `SERVER_IP` set to an address the
  phone can use. Try `http://<server>:8080` in the phone's browser first: if video plays there, the network is fine.

## Install

1. On the phone, open the [Releases page](https://github.com/rubinhos/streamiso-android/releases) and download
   the latest `streamiso-<version>.apk`.
2. Open the downloaded file. Android asks you to allow installs from this source: allow it for your browser, then
   go back and tap **Install**. Google Play Protect may warn you about an unknown developer, because the app is not
   on the Play Store.
3. You can turn the permission off again afterwards. Installing a newer version the same way keeps your settings.

## Set the server address

The first time you open the app, type the address of your server, for example `192.168.0.10:8080`, and tap
**Connect**. The app checks it with `GET /healthz` and remembers it. If you leave out the port, it uses 8080.

To change it later, tap **Server** at the top of the catalog (it needs a current StreamISO server; on older ones,
**long-press an empty part of the catalog** and choose **Change address**). The error screen has a **Change address**
button too.

## How it behaves

| Where | Screen | Back button |
|---|---|---|
| Catalog | Follows the phone's rotation setting | Leaves the app |
| Playing a DVD | Immersive full screen, locked in landscape, stays on | Stops the disc and returns to the catalog |
| Playing an audio CD | Locked in portrait, stays on | Stops the disc and returns to the catalog |
| Connecting | "Connecting to ..." with a spinner, while the app checks `GET /healthz` (5 s at most) | Leaves the app |
| Server unreachable | Error with **Try again** and **Change address**, after 5 s at most (20 s if a page hangs) | Leaves the app |

In immersive mode the system bars are hidden: swipe from the edge of the screen to bring them back, and swipe again
for the Back gesture.

The app loads nothing from other sites. Top-level pages must come from the exact address you typed. Frames and
resources may come from any port of the same host, because the video stream (Neko) is served from another port.
Everything else is refused. Safe Browsing and WebView metrics are turned off so that the WebView does not contact
Google. The app asks for no camera, microphone or storage permission.

## Why cleartext HTTP

StreamISO serves plain HTTP on the local network, with no HTTPS, and Android blocks that by default. The app turns
the block off in [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml), for every address,
because the address is whatever you type.

**The risk:** nothing between the phone and the server is encrypted or authenticated. Anyone on the same network can
see what you play, read the player password (it travels in a URL) and control playback. Someone able to intercept
the traffic could also change the pages the app shows. StreamISO has no login either.

**So use it only on a local network you trust, or over a VPN such as Tailscale. Never expose the server to the
internet.**

## Known limitations

- **`NEKO_PUBLIC_URL` on another host does not work.** The app blocks every host other than the server's, so a video
  stream published on a different host is refused. A different port of the same host is fine.
- **The web player cannot go fullscreen by itself in the app,** but DVDs are already full screen. With a current StreamISO
  server, its Fullscreen button is left out here, and the handle at the bottom collapses the controls instead.
- **Changing a cover does nothing.** Editing is not a goal of this app, so it has no file picker. Use a browser.
- **Sound stops when the app goes to the background,** and comes back when you return. The disc keeps playing on the
  server. Swiping the app away does not stop the disc either: press Back first.
- **Tablets and unfolded foldables may ignore the landscape lock.** Android 16 ignores orientation locks on large
  screens for apps with this target version.
- **The target is Android 16 (API 36) on purpose.** Apps that target API 37 need a runtime permission to reach the
  local network, and how that applies to a WebView has not been checked yet.

## Troubleshooting

**"Cannot reach the server".** Check that the server is running and the address is right, and that the phone is on the
same network and not on a guest Wi-Fi with client isolation.

**The catalog opens, but the player has no video.** WebRTC is not getting through. Check `SERVER_IP` and UDP ports
52000-52100 on the server: see Troubleshooting in the
[StreamISO README](https://github.com/rubinhos/streamiso#troubleshooting).

**Android says the app was not installed, or that it conflicts with an existing one.** The installed copy was signed
with a different key, which happens if you installed a debug build. Uninstall it first.

## What has been tested

Nothing has run on a phone or emulator yet, so everything about how the app behaves on a device is untested, including
the WebRTC video in the WebView, sound, rotation, immersive mode, the Back button and the look of the icon.

What has run is the build itself: the Build workflow compiles the debug APK and passes the unit tests on GitHub Actions.

The unit tests cover only the address parsing and the rules for what may be loaded
([`ServerAddressTest`](app/src/test/kotlin/io/github/rubinhos/streamiso/server/ServerAddressTest.kt)).

## Building

Every push and pull request runs the [Build workflow](.github/workflows/build.yml), which compiles the debug APK and
runs the unit tests. Each run keeps the debug APK as a downloadable artifact for 7 days. It is signed with a throwaway
key, so uninstall the app before installing one, and again before going back to a release.

To build on your own machine you need JDK 17 and the Android SDK (with `ANDROID_HOME` set). Then:

```
./gradlew assembleDebug testDebugUnitTest
```

There are no libraries in the app, only the Android platform and the Kotlin standard library. JUnit is used by the
tests only. Android Studio is not required.

## Signing key and secrets

A release must always be signed with the same key: **Android refuses to update an installed app with a different key.**
Create the key once, on your own computer. These commands are for PowerShell on Windows.

**1. Install a JDK**, if you do not have one, and open a new terminal. It provides `keytool`.

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

**2. Create the keystore outside the repository.** `keytool` asks for a password and for your name: only the password
matters. Choose a long one. The name goes in the certificate, which is public.

```powershell
New-Item -ItemType Directory -Force "$HOME\streamiso-keys"
keytool -genkeypair -v -keystore "$HOME\streamiso-keys\streamiso-release.keystore" -storetype PKCS12 -alias streamiso -keyalg RSA -keysize 4096 -validity 10000
```

**3. Copy the keystore as base64 to the clipboard.**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\streamiso-keys\streamiso-release.keystore")) | Set-Clipboard
```

On Linux, the equivalent is `base64 -w0 streamiso-release.keystore`.

**4. Add the secrets.** On GitHub, open the repository's **Settings > Secrets and variables > Actions** and use
**New repository secret** for each of these:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The base64 text from step 3 (paste it) |
| `KEYSTORE_PASSWORD` | The password from step 2 |
| `KEY_ALIAS` | `streamiso` |
| `KEY_PASSWORD` | The same password as `KEYSTORE_PASSWORD` (a PKCS12 keystore has only one) |

Then clear the clipboard with `Set-Clipboard -Value " "`.

**5. Back up the key outside GitHub, now.** GitHub never shows a secret again, so it cannot give the key back to you.
Keep the keystore file and its password in a password manager, or in two separate places such as an encrypted USB
drive. **If you lose the key, you cannot update the installed app:** you would have to create a new key, and everyone
would have to uninstall the app and install it again.

Never commit the keystore or the passwords. `.gitignore` covers the usual keystore file names as a safety net.

## Releasing a version

**Releases are automatic.** Every push to `master` that changes the app or its build (`app/`, `gradle/` and the Gradle
files at the root) publishes the next patch version, for example `v0.1.2` after `v0.1.1`. Merging a pull request counts.
There is no pause for testing, so test the debug APK from the pull request before merging. Changes to anything else, such
as this README or the workflows, do not publish a release.

The [Release workflow](.github/workflows/release.yml) builds and signs the APK, checks the signature and creates a GitHub
Release, and its tag, with `streamiso-<version>.apk` and its SHA-256 checksum attached.

**To choose the number yourself,** for a bigger change, push a tag named `v` plus three numbers. Automatic releases then
continue from it:

```
git tag v0.2.0
git push origin v0.2.0
```

The version comes from the tag, not from a file: `versionName` is `0.2.0`, and `versionCode` is
`major * 1000000 + minor * 1000 + patch` (`v1.2.3` gives `1002003`). Each new tag must be a higher version than the last,
or Android will refuse the update. Minor and patch must stay below 1000.

If a release fails, fix the problem and merge the fix: the next run uses the same number, because a failed run creates no
tag. A tag you pushed by hand stays in place even if its release failed, so push a new tag with the next number.

## Project layout

```
app/src/main/kotlin/io/github/rubinhos/streamiso/
├── server/   address and URL rules, HTTP calls, saved address (no WebView code)
├── setup/    the screen that asks for the server address
└── web/      the WebView shell: orientation, immersive mode, Back button, errors
```

A later native client, with its own interface instead of the web one, can reuse `server/` and replace `web/`.

## License

Released under the [Apache License 2.0](LICENSE). See also [`NOTICE`](NOTICE).

Built by Rubens ([@rubinhos](https://github.com/rubinhos)) with [Claude](https://claude.ai) (Anthropic), which wrote the
code. "StreamISO" is not affiliated with Stremio.
