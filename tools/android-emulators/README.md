# Real Android emulator LAN bridge

This helper advertises and forwards a real emulator service to LAN controllers. It does not create fake devices.

Pass `--serial` and `--adb` to automatically refresh the ADB forward when app updates or restarts change its listening port:

```sh
python3 lan-forward.py --port 19771 --target 28771 \
  --serial emulator-5554 --adb /path/to/adb \
  --id DEVICE_LAN_ID --name "安卓 A"
```

Use a separate port, target, serial and actual device ID for each emulator. The helper checks every three seconds and does not select a port when multiple candidate services exist. Android still requires its normal connection approval. Keep the helper running while using LAN clients. App updates do not require pairing again.
