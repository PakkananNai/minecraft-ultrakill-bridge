# Minecraft ULTRAKILL Bridge guest mod

Standalone Fabric client mod for the Milestone 4 control connection. It connects in the background to `127.0.0.1:47653`, sends the canonical MCUB HELLO, validates HELLO_ACK, exchanges PING/PONG heartbeats, and sends SHUTDOWN when the Minecraft client stops. If the host is unavailable, the mod logs the failure and leaves normal Minecraft operation alone.

## Build and tests

Requirements: Java 21. The project uses Gradle 8.10.2, Fabric Loom 1.7.4, Minecraft 1.21.1, Yarn `1.21.1+build.3`, Fabric Loader 0.16.5, and Fabric API `0.102.1+1.21.1`.

Run `./gradlew build` to compile, run JUnit tests, and produce the mod jar. The tests verify exact canonical HELLO bytes and exercise HELLO/HELLO_ACK, PING/PONG, and graceful disconnect using a loopback test server on port 47653.

This milestone does not implement rendering, shared memory, world/entity/input synchronization, or reconnection.
