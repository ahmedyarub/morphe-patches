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
> **[v1.0.0-dev.1](https://github.com/ahmedyarub/morphe-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Reddit&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2026.37.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove Reddit Pro section](#remove-reddit-pro-section) | Removes the Reddit Pro promos: the post creation and subreddit join upsell sheets, and the Reddit Pro banner on the profile feed. |  |

</details>

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
