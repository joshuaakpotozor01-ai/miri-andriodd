# Building and running the Miri Android app

This is a real native Android app: a small floating orb bubble that sits on
top of every other app (like Messenger chat heads / Siri's edge indicator),
which opens the full Miri assistant when tapped. Voice recognition and
text-to-speech run through Android's native APIs (not the browser's), which
is what makes the mic reliable inside a WebView.

**Why we're not compiling this on-device in Termux:** Android app compilation
needs Google's `aapt2` tool, and the version Google ships is built for
desktop Linux/macOS/Windows — it doesn't run on Termux's on-phone
architecture without patched/rebuilt binaries, which is a genuinely fiddly,
frequently-breaking setup even for experienced Termux users. Routing the
*build* through GitHub Actions (free for public repos, and free for small
private ones too) sidesteps that completely — you still write/edit every
file on your phone in Acode, you just don't compile on the phone.

## 1. Push this project to GitHub (from Termux)

```
pkg install git -y
cd miri-android          # the folder you unzipped this into
git init
git add .
git commit -m "Miri Android app"
```

Create an empty repo on github.com (in Chrome: github.com/new — name it
`miri-android`, don't add a README/license, keep it private if you prefer).
Then:

```
git remote add origin https://github.com/YOUR_USERNAME/miri-android.git
git branch -M main
git push -u origin main
```

It'll ask for your GitHub username and a **Personal Access Token** as the
password (GitHub removed plain-password pushes) — create one at
github.com/settings/tokens (classic token, `repo` scope) and paste it when
prompted.

## 2. Let the Action build the APK

The push itself triggers `.github/workflows/build.yml` automatically. On
github.com, open your repo → **Actions** tab → the running workflow → wait
for the green check (2-4 minutes) → open it → under **Artifacts**, download
`miri-debug-apk` (a zip containing `app-debug.apk`).

You can do all of this from Chrome on your phone.

## 3. Install it on your phone

1. Unzip the downloaded artifact to get `app-debug.apk`.
2. Tap it in your Downloads/Files app. Android will ask you to allow
   "install unknown apps" for that app (Chrome or Files) — allow it once.
3. Install. This is a debug build, self-signed automatically by Gradle, so
   no separate signing step is needed to sideload it.

## 4. First run

1. Open **Miri**. It'll ask for microphone permission — allow it.
2. It'll then send you to Android's **"draw over other apps"** settings
   screen — find Miri in the list and enable it, then go back.
3. A small glowing bubble should now float on your home screen and stay
   visible over other apps. Drag it anywhere; tap it (without dragging) to
   open the full assistant.
4. If Android's battery optimizer kills the bubble after a while: Settings →
   Apps → Miri → Battery → set to **Unrestricted**, so the background
   service isn't stopped.

## Still true from before

The `termux_assistant.py` backend (device commands + Claude brain) is
unchanged — run it in Termux exactly as before, and both the browser
version of `miri.html` *and* this native app will use it automatically over
`http://127.0.0.1:8765/ask` whenever it's running. If it's not running, the
app falls back to the same canned replies.

## If you'd rather not touch GitHub at all

The realistic fallback is building on an actual desktop computer once,
with Android Studio installed — open this same project folder there,
click Run, and it does everything (SDK, signing, install-over-USB)
automatically. If you get access to any PC or Mac even briefly, that's
the smoothest path; come back and tell me and I'll give you the exact
Android Studio steps instead.
