# Environment Audit & System Inspection Report

This document records the exact, verified technical inspection of the development host, ULTRAKILL installation, BepInEx mod loader, build toolchains, and Minecraft environment.

---

## 1. Host Machine & Operating System

* **Operating System:** Ubuntu 26.04 LTS (x86_64)
* **Kernel:** Linux `7.0.0-34-generic` #34-Ubuntu SMP PREEMPT_DYNAMIC
* **CPU:** Intel(R) Core(TM) i5-7300U @ 2.60GHz (2 cores, 4 threads)
* **RAM:** 32 GB (approx. 29 GiB usable, 17 GiB free)
* **Storage:** 256 GB NVMe SSD (`/dev/nvme0n1`)
* **Display Server:** Wayland desktop session
* **GPU / Graphics:** Intel HD Graphics 620 (Kaby Lake GT2)

---

## 2. Host Game: ULTRAKILL

* **Installation Path:** `/home/pakkanannai/Downloads/ULTRAKILL.v2026.04.25/ULTRAKILL.v2026.04.25`
* **Executable:** `ULTRAKILL.exe` (PE32+ x86-64 executable for MS Windows)
* **Engine:** Unity `2022.3.29f1` (build `8d510ca76d2b`)
* **CLR / Scripting Backend:** Unity Mono x64 (CLR version `4.0.30319.42000`)
* **Execution Environment:** Wine 11.0 (`/usr/local/bin/wine`)
* **Wine Prefix:** `/home/pakkanannai/.wine` (Windows 10 64-bit profile)
* **Drive Mappings:**
  * `C:` -> `~/.wine/drive_c`
  * `Z:` -> `/home/pakkanannai/Downloads` (contains ULTRAKILL)
  * `W:` -> `/home/pakkanannai/Documents/antigravity/minecraft-ultrakill-bridge` (workspace link)
* **Launch Command:**
  ```bash
  cd /home/pakkanannai/Downloads/ULTRAKILL.v2026.04.25/ULTRAKILL.v2026.04.25
  WINEDLLOVERRIDES="winhttp=n,b" wine ./ULTRAKILL.exe
  ```
* **Graphics API (from Unity Player.log):** Direct3D 11.0 [feature level 11.1] running on Intel HD Graphics 620 via Wine translation.
* **Input System (from Unity Player.log):** Unity New Input System (experimental) initialized with `Windows.Gaming.Input`.

---

## 3. Mod Loader: BepInEx 6 Unity Mono

* **Version:** BepInEx `6.0.0-be.788` (commit `5b766a3b7f6c164d4798924a93f3acf4db769d06`)
* **Architecture:** Windows x64 Unity Mono
* **Injection Mechanism:** Unity Doorstop via `winhttp.dll` and `doorstop_config.ini`
* **Entrypoint:** Configured in `BepInEx.cfg` hooking `UnityEngine.CoreModule.dll -> UnityEngine.Application..cctor`
* **Installed Core Assemblies (`BepInEx/core/`):**
  * `BepInEx.Core.dll` (Core framework, plugin metadata attributes, logging, config)
  * `BepInEx.Unity.Mono.dll` (Provides `BepInEx.Unity.Mono.BaseUnityPlugin`)
  * `BepInEx.Unity.Common.dll`
  * `0Harmony.dll` (Harmony 2 runtime detour engine)
  * `MonoMod.RuntimeDetour.dll` & `MonoMod.Utils.dll`
  * `Mono.Cecil.dll`
* **Plugins Directory:** `/home/pakkanannai/Downloads/ULTRAKILL.v2026.04.25/ULTRAKILL.v2026.04.25/BepInEx/plugins`
* **Execution Log Verification (`BepInEx/LogOutput.log`):**
  ```text
  [Message: Preloader] BepInEx 6.0.0-be.788 - ULTRAKILL (4/25/2026 3:34:10 PM)
  [Message: Preloader] Built from commit 5b766a3b7f6c164d4798924a93f3acf4db769d06
  [Info   :   BepInEx] System platform: Windows 10 (Wine 11.0) 64-bit
  [Info   :   BepInEx] Process bitness: 64-bit (x64)
  [Info   : Preloader] Running under Unity 2022.3.29f1
  [Info   : Preloader] CLR runtime version: 4.0.30319.42000
  [Info   : Preloader] Supports SRE: True
  [Message: Preloader] Preloader started
  [Info   :AssemblyPatcher] Loaded 1 patcher type from [BepInEx.Unity.Mono.Preloader 6.0.0.0]
  [Info   : Preloader] 1 patcher plugin loaded
  [Info   : Preloader] 179 assemblies discovered
  [Message:AssemblyPatcher] Executing 1 patch(es)
  [Message: Preloader] Preloader finished
  [Message:   BepInEx] Chainloader initialized
  [Info   :   BepInEx] 0 plugins to load
  [Message:   BepInEx] Chainloader startup complete
  ```

---

## 4. .NET & C# Toolchain Inspection

* **Target Framework:** `netstandard2.1`
  * Verified against Unity `2022.3.29f1` managed assemblies (`ULTRAKILL_Data/Managed/netstandard.dll` v2.1.0.0, `mscorlib.dll` v4.0.0.0).
  * Assemblies must reference `BepInEx.Core.dll` and `BepInEx.Unity.Mono.dll`.
  * The base class for plugins is `BepInEx.Unity.Mono.BaseUnityPlugin` (inheriting from `UnityEngine.MonoBehaviour`).
* **Available Compilers:**
  1. **Wine Mono C# Compiler (`mcs.exe`):**
     * Path: `~/.wine/drive_c/windows/mono/mono-2.0/lib/mono/4.5/mcs.exe`
     * Status: **Verified working.** Compiles C# code targeting `netstandard2.1` using `/nostdlib` and referencing `Managed/netstandard.dll` + `Managed/mscorlib.dll`.
     * Zero external installation required.
  2. **Host `.NET` CLI:**
     * Path: `/usr/bin/dotnet` (Host 10.0.12, runtime only, no SDK).
     * Local `~/.dotnet` has .NET runtime 8.0.28.
     * Installing .NET SDK 8.0/9.0 locally via `dotnet-install.sh` enables `dotnet build` with standard `.csproj` files.

---

## 5. Java & Minecraft Toolchain Inspection

* **Minecraft Target Version:** Minecraft Java Edition 1.21.1
* **Modding Framework:** Fabric Loader 0.19.3, Fabric API
* **JDK Availability:**
  * System `/usr/bin/java` is Java 8 (`1.8.0_504`), `/usr/lib/jvm/java-25-openjdk-amd64` contains only JRE headless.
  * **Verified Full JDKs Discovered:**
    * **Java 21 JDK:** `/home/pakkanannai/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-delta`
      * `java -version`: OpenJDK 21.0.7 LTS (Microsoft build 21.0.7+6-LTS)
      * `javac -version`: `javac 21.0.7`
    * **Java 25 JDK:** `/home/pakkanannai/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-epsilon`
      * `java -version`: OpenJDK 25.0.1 LTS
      * `javac -version`: `javac 25.0.1`
  * **Prism Launcher:** Installed and operational, with an existing Fabric instance (`26.1.2`) configured with Fabric Loader 0.19.3 and LWJGL 3.4.1.

---

## 6. Wine & Cross-Environment Considerations

* **Shared Memory Interop:**
  * Memory-mapped files (e.g. `/tmp/minecraft_bridge.shm`) created by Linux native processes are directly accessible to Wine Windows processes. Wine implements file mappings on top of Unix host file descriptors and kernel page tables, allowing true shared memory without socket serialization overhead.
* **Control Channel:**
  * TCP localhost (127.0.0.1) or Windows named pipes mapped through Wine can be used for bidirectional control messages (handshake, input, ping/pong). Localhost loopback TCP is fully cross-platform and incurs < 0.1ms latency on modern Linux.
* **Directory Conventions:**
  * Scripts running under Wine must use drive-relative paths (e.g., `Z:\...` or `W:\...`).
  * Never alter original ULTRAKILL game assemblies or BepInEx core files.
