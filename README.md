# 🧩 Morphe Patches — ahmedyarub

Personal [Morphe](https://morphe.software) patches.

## ❓ About

Patches built with [Morphe Patcher](https://github.com/MorpheApp/morphe-patcher) for apps I
use. Each patch is developed against a specific, decompiled APK version, and only versions
that have actually been verified are declared as compatible.

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=ahmedyarub/morphe-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release. -->

#### A list of the patches will automatically be shown here after the first release is created.

<!-- PATCHES_END -->

&nbsp;

## 🚀 Building

The Morphe patches Gradle plugin is published to GitHub Packages, so a GitHub token with
`read:packages` is required to build:

```sh
GITHUB_ACTOR=<username> GITHUB_TOKEN=<token> ./gradlew build
```

Or put `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`.

## 📜 License

GNU General Public License v3.0. See [LICENSE](LICENSE).
