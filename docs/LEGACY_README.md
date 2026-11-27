# Archived previous README

[中文](LEGACY_README.zh-CN.md) · [Current README](../README.md)

This page replaces the root README snapshot from before the 2026-09-27 documentation cleanup. Version numbers, test counts, commit states, evidence paths and local paths in that snapshot describe only the earlier workspace and are not current instructions.

The previous development layout kept game files inside the repository:

- `libs/`: game A/B, game libraries and Fabric Loader for compilation and legacy launch.
- `game/`: `Airships.json`, static data, native libraries and MODs for `startAirships`.
- `loader-libs/`: launch shim, Fabric Loader, Mixin and ASM.

The current player workflow uses an external distribution and `Acbric.exe`; the game no longer needs to be copied into the repository. See the [build guide](BUILDING.md) for development inputs and commands. Legacy `distZip` remains an internal research package and is not for distribution.

Current architecture, APIs and packaging are defined by the [documentation index](README.md), [API guide](API.md) and [changelog](CHANGELOG.md).

Acbric uses the MIT License. The game and third-party components remain the property of their respective owners and are not included.
