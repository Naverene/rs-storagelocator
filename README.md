# RS Storage Locator

An add-on for Refined Storage: hover an item or fluid in any grid and the tooltip lists where the network keeps it
(which disk in which Disk Drive, which blocks External Storages face, and how many each). A key press in the grid
outlines those blocks in the world.

It is the Refined Storage counterpart of [AE2 Storage Locator](https://github.com/Naverene/ae2-storagelocator).

This repository holds every version of the mod, one folder per Minecraft version, laid out like
[More Dyes](https://github.com/Naverene/moredyes):

| Folder | Minecraft | Loader | Refined Storage | Java |
| --- | --- | --- | --- | --- |
| [1.10.2](1.10.2) | 1.10.2 | Forge | 1.2.x | 8 |
| [1.11.2](1.11.2) | 1.11.2 | Forge | 1.4.x | 8 |
| [1.12.2](1.12.2) | 1.12.2 | Forge | 1.6.x | 8 |
| [1.14.4](1.14.4) | 1.14.4 | Forge | 1.7.x (beta; the only 1.14.4 versions) | 8 |
| [1.15.2](1.15.2) | 1.15.2 | Forge | 1.8.x | 8 |
| [1.16.5](1.16.5) | 1.16.5 | Forge | 1.9.x | 8 |
| [1.18.2](1.18.2) | 1.18.2 | Forge | 1.10.x | 17 |
| [1.19.2](1.19.2) | 1.19.2 | Forge | 1.11.x | 17 |
| [1.20.1](1.20.1) | 1.20.1 | Forge | 1.12.x | 17 |
| [1.20.4](1.20.4) | 1.20.4 | NeoForge | 1.13.x (beta; the only 1.20.4 versions) | 17 |
| [1.21.1](1.21.1) | 1.21.1 | NeoForge | 2.0.x (Refined Storage 2) | 21 |
| [1.21.1-fabric](1.21.1-fabric) | 1.21.1 | Fabric (needs Fabric API) | 2.0.x (Refined Storage 2) | 21 |
| [26.1.2](26.1.2) | 26.1.2 | NeoForge | 3.x (Refined Storage 2) | 25 |
| [26.1.2-fabric](26.1.2-fabric) | 26.1.2 | Fabric (needs Fabric API) | 3.x (Refined Storage 2) | 25 |

The other More Dyes versions have no Refined Storage release, so they have no folder here: 1.4.7 and 1.7.10
(Refined Storage starts at 1.9), 1.20.1 Fabric (Refined Storage 1 is Forge only), 1.21.11 and 1.21.11 Fabric, and
26.3.

Some folders go beyond More Dyes, for the Minecraft versions Refined Storage was released for that More Dyes skips:
1.10.2, 1.11.2, 1.14.4, 1.15.2, 1.20.4 and 26.1.2 Fabric.

Each Minecraft folder is its own Gradle project with its own wrapper. Open the folder for the version you want in
your IDE (not the repository root), and run `./gradlew build` inside it. The jar lands in that folder's
`build/libs`. See each folder's README for what that version does and how it is built.

## Builds

GitHub Actions (`.github/workflows/build.yml`) builds every version folder:

- a pull request builds only the versions whose folders it changes (all of them if the workflow changes)
- every push to the default branch builds all versions and updates the rolling "Development build" pre-release
- pushing a version tag such as `v1.2.0` builds all versions as that mod version and publishes one release with
  every jar

`.gitlab-ci.yml` does the same everyday builds on a self-hosted GitLab, like More Dyes.

Refined Storage itself comes from [Modrinth's maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven)
(`maven.modrinth:refined-storage:<Modrinth version id>`); each folder's `gradle.properties` names the version.

## Adding a Minecraft version

1. Make a new folder named after the Minecraft version (add `-fabric` when the loader is Fabric and the plain
   name is taken by Forge or NeoForge), with its own Gradle wrapper, `.gitignore` and `README.md`.
2. Add it to the table above, to `VERSIONS` in `.github/workflows/build.yml` and as a job in `.gitlab-ci.yml`.
