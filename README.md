# CleanYourTV for the Xiaomi MiTV-AXSO0

This is a copy of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) set up for one TV:

| | |
|---|---|
| **Model** | Xiaomi MiTV-AXSO0, codename dangal (sold as Mi TV 4C Pro 2019 and Mi TV 4A Pro) |
| **System** | Android TV 9 (SDK 28), firmware PI.6036, Amlogic |
| **Debloat list** | [`examples/xiaomi-mitv-axso0/debloat.txt`](examples/xiaomi-mitv-axso0/debloat.txt) |
| **Launcher settings** | [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml), already set for this TV |

It does two things:

1. **Debloat:** switches off PatchWall, the ads and sponsored rows, and the Xiaomi and Google tracking.
2. **New home screen:** a dark home screen with no clutter. Your apps sit in a dock at the bottom, the rest
   go in a grid below it, and the top has a clock and buttons for Inputs, Wi-Fi and Settings. There's no
   PatchWall, no recommendation rows and no ads.

Everything can be undone. Apps are **disabled, never uninstalled**, and there's no root and no flashing.

---

## What gets switched off

| Group | Packages | What happens |
|---|---|---|
| Tracking | `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender` | No more Xiaomi analytics or Google feedback uploads |
| Ads and content rows | PatchWall (`com.mitv.tvhome.atv`), `com.xm.webcontent`, `com.xiaomi.floatingframe`, Google TV recommendations, the partner auto-installer | No sponsored rows, and no apps installed behind your back |
| Lock-screen ads | Glance (`com.glance.tv`) | The screensaver is clean |
| Sponsored channels | Mi TV+ (`com.mitv.tvhome.mitvplus`), Sensara (`co.sensara.tv.mitv`) | No promoted channels or show "suggestions" |
| Xiaomi background apps | TV Manager cleaner, handbook, legal screens, update service | Less running in the background on a 1 GB TV |
| Preloaded apps | Mi Music, Play Games, Play Movies & TV, Mi Gallery | Gone from the app list. USB photos still open in Media Player |
| **Your choice** | Voice assistant, Chromecast, Mi Cast, Miracast, FanCode, Sun NXT, Eros, discovery+, Prasar Bharati, Al Jazeera | Read [Before you run it](#3-choose-what-to-keep) first |

**These stay on:** Live TV and the HDMI inputs (`com.android.tv`), the input menu, the remote and Bluetooth,
the on-screen keyboard (Gboard), the Play Store and Play Services, Netflix, Prime Video and YouTube.
The new home screen hides some of them (the Play Store and YouTube among them) to keep the grid clear. They
stay installed and working.

---

## What you need

- **The TV**, switched on and connected to Wi-Fi or Ethernet.
- **A computer on the same network** (Windows, macOS or Linux) with Git and Python 3.
- **For the new home screen only:** JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). The easiest
  way to get both is to install Android Studio.

> **Only have a phone?** The Claude Code mobile app runs in the cloud, so it can't reach your TV at home.
> - **Have a computer?** Run `claude remote-control` in this folder on the computer. The session then
>   shows up in the mobile app and you control it from your phone ([Option A](#option-a-let-claude-code-do-it)).
> - **No computer, but an Android phone?** You can still do the debloat from Termux
>   ([Debloat from an Android phone](#debloat-from-an-android-phone)). Building the new home screen needs a
>   computer.

---

## 1. Turn on debugging on the TV

1. **Settings → Device Preferences → About**, then press OK on **Build** 7 times. The TV will tell you that
   you're now a developer.
2. Go back to **Device Preferences → Developer options** and turn on **USB debugging**.

That's all this TV needs. Xiaomi TVs have no *Wireless debugging* switch: turning on USB debugging opens
ADB over the network on port 5555 by itself. Don't use a USB cable. The TV's USB ports are for plugging
things in, and a computer can't connect to them.

**Find the TV's IP address:** go to **Settings → Network & Internet** and open your Wi-Fi network. Use
the line that looks like `192.168.x.x`. Ignore the long `fe80:` and `2406:` lines, which are IPv6
addresses.

In every command below, replace `<tv ip>` with that address.

---

## Option A: let Claude Code do it

On the computer:

```bash
git clone https://github.com/Mr-Saikiran/cleanyourtv-MiTV-AXSO0
cd cleanyourtv-MiTV-AXSO0
claude                  # or: claude remote-control, to drive it from the Claude Code app on your phone
```

Then send:

> Follow PROMPT.md for my TV. Model: Xiaomi MiTV-AXSO0, TV IP `<tv ip>`. Use
> `examples/xiaomi-mitv-axso0/debloat.txt` and the launcher config that is already set up.

It connects, makes a backup, asks which apps you use and which to put in the dock, does the debloat,
installs the home screen, and takes screenshots from the TV as it goes. When it connects, a prompt saying
**"Allow USB debugging?"** appears on the TV: tick *Always allow* and press OK.

---

## Option B: do it yourself

macOS and Linux commands are shown. On Windows, use Git Bash, or the PowerShell versions next to each
script (`tools\disable.ps1`, `tools\restore.ps1`, `tools\theme.ps1`).

### 2. Set up and connect

```bash
git clone https://github.com/Mr-Saikiran/cleanyourtv-MiTV-AXSO0
cd cleanyourtv-MiTV-AXSO0
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt

export TV_HOST=<tv ip>
tools/venv/bin/python tools/tv.py --probe      # accept "Allow USB debugging?" on the TV
```

`--probe` should print the model and Android 9. If it times out, check that the TV is on, on the same
network, and that USB debugging is still on.

**Make a backup before changing anything:**

```bash
mkdir -p backup
tools/venv/bin/python tools/tv.py 'pm list packages'    > backup/packages.txt
tools/venv/bin/python tools/tv.py 'pm list packages -d' > backup/disabled.txt
tools/venv/bin/python tools/tv.py 'getprop'             > backup/getprop.txt
tools/venv/bin/python tools/tv.py 'settings list global' > backup/settings-global.txt
tools/venv/bin/python tools/tv.py 'settings list secure' > backup/settings-secure.txt
```

`tools/disable.sh` also keeps its own record in `~/.cleanyourtv/`, and that record is what the undo uses.

### 3. Choose what to keep

Open [`examples/xiaomi-mitv-axso0/debloat.txt`](examples/xiaomi-mitv-axso0/debloat.txt) and put a `#` in
front of every line you want to **keep**:

| If you use… | Keep these lines |
|---|---|
| The mic button on the remote / Google Assistant | `com.google.android.katniss`, `com.google.android.speech.pumpkin` |
| Casting from your phone (YouTube, Chrome, Photos) | `com.google.android.apps.mediashell` |
| Mi Cast or screen mirroring from a phone | `com.mitv.milinkservice`, `com.xiaomi.mitv.smartshare`, `com.xiaomi.wfdsinkhelperservice` |
| Firmware updates (recommended) | `com.xiaomi.mitv.updateservice` |
| FanCode, Sun NXT, Eros Now, discovery+, Prasar Bharati, Al Jazeera | that app's line |

### 4. Debloat

```bash
tools/disable.sh examples/xiaomi-mitv-axso0/debloat.txt
tools/venv/bin/python tools/tv.py --screenshot shots/01-after-debloat.png
```

The script refuses lists that would break the TV, and it won't disable the stock launcher until the new
one is installed. Wait about 15 seconds after it finishes, then reboot:

```bash
tools/venv/bin/python tools/tv.py --reboot
```

Use `tv.py --reboot`, because `svc power reboot` gets killed on this firmware. Once the TV is back,
`tools/venv/bin/python tools/tv.py 'pm list packages -d'` should list everything you disabled.
`com.google.android.leanbacklauncher` is turned back on by the firmware at every boot, so it isn't in the
list.

### 5. Install the new home screen

The settings for this TV are already in
[`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):

| Setting | Value | Why |
|---|---|---|
| `live_tv_package` | `com.android.tv` | The **Inputs** button opens this TV's Live TV and the HDMI ports |
| `hidden_apps` | Live TV, Mi Gallery, Media Explorer, Smart Share, Play Store, Sun NXT, SonyLIV, YouTube | Keeps the grid clear. Live TV is in the Inputs menu, and hidden apps still open from Settings → Apps |
| `dock_apps` | TiviMate, SmartTube, Netflix, Prime Video, Hotstar, Airtel Xstream, Zee5 | The seven apps in the dock, in this order. Missing apps are skipped and their slots filled from the grid |
| `columns` | `7` | Seven dock slots, and seven tiles per grid row |

Build and install:

```bash
TV_HOST=<tv ip> launcher/build-and-install.sh
```

The script checks the TV's Android version, builds the APK, installs it, and makes it the Home app. Only
after that does it disable the stock Google launcher. Press **Home** on the remote and you should land on
the new screen.

**Optional: a nicer look.** Push one of the themes. `glass` gives a dark navy wallpaper, a frosted dock and
a glow in each app's colour:

```bash
TV_HOST=<tv ip> tools/theme.sh themes/glass
TV_HOST=<tv ip> tools/theme.sh --reset         # back to plain black
```

You can see all the themes in [`themes/README.md`](themes/README.md). A theme's `dock_apps` replaces the
one in `config.xml`, so edit `themes/<name>/config.json` if you want your own dock order with a theme.

### 6. Close ADB when you're done

Port 5555 isn't encrypted, and this TV has public IPv6 addresses. Don't leave it open:

1. On the TV: **Developer options → Revoke USB debugging authorisations**, then turn **USB debugging off**.
2. Reboot the TV.
3. Check:
   ```bash
   tools/venv/bin/python tools/adb-closed.py <tv ip>
   ```
   It should say the connection was **refused**.

---

## Debloat from an Android phone

If your phone runs Android and is on the same Wi-Fi as the TV, install [Termux](https://termux.dev) from
F-Droid and run:

```bash
pkg install git python python-cryptography
git clone https://github.com/Mr-Saikiran/cleanyourtv-MiTV-AXSO0
cd cleanyourtv-MiTV-AXSO0
python -m venv --system-site-packages tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=<tv ip>
tools/venv/bin/python tools/tv.py --probe
tools/disable.sh examples/xiaomi-mitv-axso0/debloat.txt
```

This gets you the debloat. You keep the stock home screen without PatchWall and its ads. The new
launcher has to be built on a computer.

---

## Changing things later

| To… | Do this |
|---|---|
| Undo everything | `TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv-axso0/debloat.txt` |
| Turn one app back on | `tools/venv/bin/python tools/tv.py 'pm enable com.google.android.katniss'` (any package name from the list) |
| Change the dock apps or order | Edit `dock_apps` in `config.xml` and run `launcher/build-and-install.sh` again, or edit a theme's `config.json` and push it with `tools/theme.sh` |
| Change the wallpaper | Put a `wallpaper.jpg` in a theme folder and push it with `tools/theme.sh themes/<name>` |
| Check after a firmware update | Updates bring the disabled apps back. Turn USB debugging on, then run `tools/venv/bin/python -I tools/check.py examples/xiaomi-mitv-axso0/debloat.txt` to see what came back, and `tools/venv/bin/python -I tools/check.py --fix examples/xiaomi-mitv-axso0/debloat.txt` to disable it again |
| Remove the new launcher | Run the restore command above, then uninstall *Home* (`com.gathin.tvhome`) in Settings → Apps |

No computer handy? **Settings → Apps → See all apps → Show system apps** on the TV can turn any disabled
app back on.

---

## If something goes wrong

| Problem | Fix |
|---|---|
| `--probe` times out | The TV is off, on another network, or USB debugging got turned off. Check the IP again |
| "unauthorized" | Accept the "Allow USB debugging?" prompt on the TV. If it doesn't show, revoke the authorisations in Developer options and connect again |
| Screenshot is empty | Live TV, HDMI and Netflix block screenshots. That's the TV, not damage |
| Home shows a black screen or the old launcher | Run `tools/restore.sh` as above, then `launcher/build-and-install.sh` again |
| A USB drive or speaker stopped working | Some TVs cut USB power while USB debugging is on. It comes back when you turn debugging off |
| Anything else | [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) |

---

## Credit and licence

Built on [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). The debloat list for this TV comes
from that project's tested lists. MIT licence, see [LICENSE](LICENSE).
