# HomeHub

A personal, single-dashboard Android app to control both your Tuya and
CozyLife smart devices, without needing two apps.

- **Tuya devices**: controlled through Tuya's official Cloud OpenAPI, using
  your own free developer account. Nothing reverse-engineered here.
- **CozyLife devices**: controlled directly over your home WiFi, since their
  protocol turned out to be a plain, unencrypted local JSON protocol with
  no cloud dependency at all.

This is for your own home use. No compiling on your own machine required,
GitHub builds the installable `.apk` for you automatically.

---

## 1. Get the code onto GitHub

You said you're not a coder, so the easiest route:

1. Install **[GitHub Desktop](https://desktop.github.com/)** (a free app, not the command line).
2. Sign in with your existing GitHub account.
3. In GitHub Desktop: **File > New Repository**, name it `HomeHub` (or anything), pick any folder on your computer as the local path.
4. Open that folder in Finder/Explorer, and copy **all the files and folders you were given** (`.github`, `app`, `settings.gradle.kts`, etc.) into it, so they sit right inside the repo folder (not in an extra subfolder).
5. Back in GitHub Desktop, you'll see all the new files listed as changes. Write a commit message like "Initial app", click **Commit to main**, then click **Publish repository** (top right).

That's it, no git commands typed anywhere.

## 2. Let GitHub build the APK

As soon as you publish, GitHub Actions kicks off automatically (there's a
workflow file already set up for this: `.github/workflows/build.yml`).

1. On GitHub.com, open your new repo, click the **Actions** tab.
2. You'll see a run in progress ("Build APK"), give it a couple of minutes.
3. Once it's green, click into that run, scroll down to **Artifacts**, and download **HomeHub-debug-apk**. It comes as a `.zip`, unzip it to get `app-debug.apk`.
4. Every time you (or I, in a future update) push new code, this repeats automatically and a fresh APK shows up the same way.

## 3. Install it on your phone

Since this isn't on the Play Store, Android will ask you to allow installing
from this source the first time:

1. Get `app-debug.apk` onto your phone (email it to yourself, Google Drive, a USB cable, whatever's easiest).
2. Tap the file. Android will prompt to allow installs from that app (Files, Gmail, etc.), allow it once.
3. Install, open HomeHub.

## 4. Set up Tuya (one-time, ~5 minutes)

Your Tuya devices already work today through the real Tuya/Smart Life app,
this step just gives your own app permission to see and control the same
devices via Tuya's official API.

1. Go to **[iot.tuya.com](https://iot.tuya.com)** and register a free developer account.
2. **Cloud > Create Cloud Project.** Development Method: **Smart Home**. Pick the **Data Center** that matches your Tuya/Smart Life app's region (in the Tuya/Smart Life app: Me > Settings > Account and Security > Region).
3. On the Authorize API Services screen, accept the defaults and click Authorize.
4. Open your new project, go to the **Devices** tab, click **Link Tuya App Account > Add App Account**, and scan the QR code with your real Tuya/Smart Life app. Confirm on your phone.
5. Still on the Devices tab, click into your linked account, note the **UID** shown there.
6. Back on the project's **Overview** tab, copy your **Access ID** and **Access Secret**.
7. In the HomeHub app: **Settings**, paste in Access ID, Access Secret, your region host (see the field's hint text for the right one), and the UID from step 5. Save.
8. Back on the dashboard, tap **+ > Tuya > Sync from linked account**. Your Tuya devices should appear.

## 5. Add your CozyLife devices

No account needed for this part.

1. Make sure your phone is on the same WiFi network as your CozyLife plugs/lights (they should already be set up via the CozyLife app once).
2. In HomeHub: **+ > CozyLife > Scan network**. Devices should appear within a few seconds.

## What works right now

- Dashboard with a device grid and a live power-draw / cost estimate (for devices that report metering data, currently confirmed for Tuya plugs with a metering chip)
- On/off control for both brands
- Rename and remove devices
- Threshold notifications ("alert me when watts/voltage drops below X"), checked roughly every 15 minutes in the background (Android's minimum interval for this kind of periodic check)
- Time-based on/off schedules, per device, per day of week

## Known gaps / next steps

- **CozyLife brightness/color**: only the power on/off data point (`"1"`) was confirmed from the decompiled protocol. Brightness/color/color-temperature data-point IDs are model-specific and weren't in the code we mapped, `CozyLifeClient.setDataPoint()` is ready to use once you capture the right IDs for your specific bulbs (e.g. by watching what the real CozyLife app sends).
- **CozyLife metering**: no evidence of energy-monitoring data points in the protocol we found, so the energy dashboard currently only lights up for Tuya devices. If your CozyLife plugs do report this, we can add it once we know the data-point ID.
- **Tuya brightness/color UI**: the client can send any command code (`sendCommand`), but the detail screen doesn't yet have sliders wired up for it, straightforward to add once you confirm which devices need it.
- Threshold checks only cover Tuya devices for now, for the reason above.
