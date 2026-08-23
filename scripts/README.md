# Messages — UI test scripts

Manual test drivers for the Messages app on `emulator-5554`.
All scripts save step-by-step PNGs to `screenshots/` so you can verify later.

## Quick start

```bash
cd ~/Develop/Messages
scripts/run-all-tests.sh          # full end-to-end sweep (~2 min)
```

Or run individually:

| Script | What it does |
|---|---|
| `install.sh` | Gradle build + install APK |
| `grant-permissions.sh` | Silently grant all runtime permissions |
| `reset-permissions.sh` | Revoke permissions → relaunch → **you see real permission dialogs** |
| `open-app.sh` | Cold-launch app + home screenshot |
| `send-message.sh [row=1] [text]` | Open conversation #row from top of list, type, send |
| `new-message.sh <number> [text]` | Start chat FAB → enter number → send first message |
| `receive-sms.sh <number> [text]` | Inject a real inbound SMS via emulator radio (`adb emu sms send`) |
| `settings.sh` | Open Settings screen + theme dialog screenshots |
| `theme.sh dark\|light\|system` | Switch theme instantly (deep link) |
| `run-all-tests.sh` | Everything above in sequence |

## Notes

- Coordinate taps assume the default emulator **1080x2400 @ 420dpi**.
- Text entry uses `input text` with `%s` for spaces; sending uses the IME Send action (`keyevent 66`).
- To watch permission prompts yourself:
  ```bash
  scripts/reset-permissions.sh && scripts/open-app.sh
  ```
- Deep links used by scripts (implemented in MainActivity):
  - `am start -n com.anindra.messages/.MainActivity --es set_theme dark`
  - `am start -n com.anindra.messages/.MainActivity --ez open_settings true`
