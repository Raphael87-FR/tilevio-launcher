# Real-TV validation checklist

Use this checklist with the signed release APK on a physical Android TV or Google TV device. Record observable results, not numerical performance benchmarks. A check passes only when its expected result occurs; record any failure and stop publication until it is resolved and the affected checks pass again.

Device and manufacturer:

Android version:

APK version and commit:

Date and tester:

Use only the TV remote unless a step explicitly asks for ADB or another device. Record **Pass**, **Fail**, or **Not run**, and add an observable result for every row.

| Check | Steps | Expected result | Result and observations |
| --- | --- | --- | --- |
| HOME selection | Open Tilevio settings and activate default home. Accept the system HOME request, then press HOME from another App. | Android offers Tilevio as a HOME choice; pressing HOME opens Tilevio with the expected Wallpaper, visible Tiles, and usable focus. | |
| HOME replacement | In Android settings, select the previous home screen, press HOME, then select Tilevio again and press HOME. | Each selected home screen opens. Tilevio can replace and be replaced by another HOME without errors or repeated prompts. | |
| Manufacturer settings fallback | On a device where the role request does not complete HOME selection, use Tilevio's default-home action and the manufacturer or Android home-screen settings route. | A usable settings route opens, and Tilevio can be selected as HOME. Record the manufacturer-specific path used. | |
| Reboot | Configure a solid or imported Wallpaper and hide at least one App. Reboot the TV and allow startup to finish. | Tilevio opens as HOME. The chosen Wallpaper and Hidden App state persist, the App grid is usable, and no crash or configuration reset occurs. | |
| Sleep/wake | While Tilevio is displayed, put the TV to sleep with the remote, then wake it. Repeat while another App is open, then press HOME. | Tilevio remains usable after waking and after returning HOME, with the expected Wallpaper, Tiles, and focus. | |
| Process recreation | Note the Wallpaper, Hidden App state, and focused Tile. Run `adb shell am force-stop dev.tilevio.launcher`, then press HOME to recreate the process. | Tilevio restarts without a crash or blank screen. Wallpaper and visibility settings persist; cold-launch focus starts on the first visible App. | |
| USB removal after Wallpaper import | Import an image from USB storage as the Wallpaper and confirm it appears. Remove the USB storage safely, return HOME, then reboot the TV. | The imported Wallpaper remains visible after USB removal and reboot, without an error or blank background. | |
| Remote-only navigation | Disconnect or avoid touch and mouse input. Using only direction keys, OK, Back, and HOME, open settings, change a Wallpaper, hide and show an App, return to the grid, and open an App. | Every control and Tile is reachable. Focus remains visible and never becomes stranded; Back closes settings and does nothing on the HOME grid. | |
| App installation while in use | Keep Tilevio in use, install a new launchable App, then return to Tilevio. | The new App appears once in alphabetical order, the grid refreshes without a crash, and a valid Tile retains focus. | |
| App removal while in use | Open or focus an installed App, remove it through Android's normal App management, then return to Tilevio. | The removed App disappears, its hidden-state controls disappear, focus moves to a valid nearby Tile, and the Launcher remains usable. | |
| Absence of resident background work | With Tilevio configured, leave it idle, open another App, then inspect services with `adb shell dumpsys activity services dev.tilevio.launcher` and recent logs with `adb logcat -d`. Repeat the service inspection after reboot and HOME use. | No Tilevio foreground or background service is registered, no boot-started service appears, and no repeating Tilevio task or log activity continues while another App is in use. Package-change handling does not leave a service running. | |

Publication requires every row to pass. If the manufacturer fallback cannot be triggered on the available hardware, record exactly how HOME selection was exercised and leave that row **Not run** until suitable hardware is available; do not mark an untested row as passed.
