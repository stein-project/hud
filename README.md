# HUD

A [Stein Loader](https://github.com/Stein-Project) mod for Minecraft 1.8.9: a customizable HUD, a minimap and a
better Tab list.

- **Elements on the HUD grid**: armor and held item with durability, potion effects, a compass, performance (FPS,
  1% low, worst frame, ping, CPU, GPU, RAM and CPS), the inventory, a minimap and your current target (head, name,
  distance, health, armor and held item). Move, resize and turn each one on or off on the grid (Right Shift), where
  each element also has its own options.
- **Minimap**: detailed or simple terrain, or radar only; square or round, turning with you or north up; players,
  monsters, animals and spawners, with relation colors (allies, enemies, your clan) when a mod that marks
  them provides them; caves in the Nether and underground.
- **Tab list**: the server's, or ours: ping as bars, number or both, heads, distance, up to 500 players instead of
  80, filters (with clan, my clan, allies, enemies, nearby), sorting (ping, name, distance) and groups (by clan or by
  relation). Set it up on the Panel (Right Shift) > HUD.

The settings are kept in `config/steinhud.json`; each element's place on the grid is kept by the Loader.

<!-- Screenshot: docs/hud.png (the HUD grid with the elements) -->

## Download

The [Stein Client](https://stein-project.github.io/downloads/) installs and updates it for you.
By hand: download `HUD.steinmod` from the [latest release](https://github.com/Stein-Project/hud/releases/latest),
put it in `.minecraft/mods` and play with the **Stein Loader** profile.

## Building

You need **JDK 25 or newer**.
The [Stein SDK](https://github.com/Stein-Project/sdk) Gradle plugin prepares Minecraft 1.8.9 for compiling the
first time, from your own installation or from Mojang.

```sh
./gradlew build            # build/libs/HUD.steinmod (also checks the formatting)
./gradlew spotlessApply    # format the code (google-java-format, AOSP style) and add the licence header
./gradlew installMod       # build and copy into .minecraft/mods
```

Before a Stein SDK release (developing the SDK and the mod together), build against a local SDK: in a clone of
[sdk](https://github.com/Stein-Project/sdk) run `./gradlew build publishPlugin`, then here
`./gradlew build -Pstein.sdkDir=<path to that clone>` (the plugin comes from its `build/maven-repo`, the SDK from
its `build/sdk/stein-sdk.jar`).

## Contributing

Bug reports, ideas and pull requests are welcome. Read the
[contributing guide](https://github.com/Stein-Project/.github/blob/main/CONTRIBUTING.md) first; your first pull
request asks you to sign the [CLA](https://github.com/Stein-Project/.github/blob/main/CLA.md). For a change to this
mod, also check that:

- an existing `config/steinhud.json` still loads: its JSON keys (the fields of `HudConfig` and `TabConfig`) are
  what players already have, and so are the element ids (`steinhud.armor`, `steinhud.minimap`...) under which the
  Loader keeps each element's place;
- everything is drawn through the Loader API (`Render`), never with OpenGL directly, and looks right with both the
  Stein GFX renderer and the fixed-function fallback (`-Dsteinbridge.gfx=false`);
- the text is translated in `en_US`, `pt_BR` and `es_ES` (`src/main/resources/assets/steinhud/lang`).

Releases are made by the maintainer: a `v<version>` tag, with `changelog/<version>.en_US.md` (and optionally `.pt_BR.md`)
written first (see `.github/workflows/release.yml`).

## Licence

HUD is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party notices are in [NOTICE](NOTICE).
The Stein name and logo are not covered by the licence: see the
[trademark policy](https://github.com/Stein-Project/.github/blob/main/TRADEMARK.md).

Stein is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft.

Created by [Breno Xavier](https://github.com/brenoxavier).
