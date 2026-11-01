# Local fleet simulator

Two clearly labelled simulated Android agents advertise `_mika-agent._tcp` on ports 19301/19302. They implement pairing, authenticated HTTP, SSE state updates, independent command results, and synthetic H.264 virtual-screen streams. They do not run Android applications, emulate Android permissions, or validate physical-device performance.

From the workspace root on macOS:

```sh
swift outputs/Mika/tools/fleet-simulator/frames.swift work/mock-A.frames A
swift outputs/Mika/tools/fleet-simulator/frames.swift work/mock-B.frames B
python3 outputs/Mika/tools/fleet-simulator/server.py --frames 'work/mock-{label}.frames'
```

Keep Mac and iPhone on the same LAN. In iOS agent, choose `连接模拟设备` from the menu. Pairing is automatically accepted by these test servers only. Each server displays a synthetic screen continuously; actual Android screens appear only while Android's native virtual preview is active. Test servers have new tokens after restart, requiring re-pairing. Ctrl-C stops servers and DNS advertisements.

Fleet store regression test (use a disposable macOS process; clears its `fleetChats` defaults):

```sh
swiftc -parse-as-library outputs/Mika/tools/fleet-simulator/tests/FleetStoreTests.swift outputs/Mika/ios/App/FleetChatStore.swift -o work/test-fleet
work/test-fleet
```
