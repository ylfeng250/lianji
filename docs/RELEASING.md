# 发版说明

这篇文档写给维护者。仓库地址按 `ylfeng250/lianji` 写的；如果你换了 owner 或仓库名，
把 README、CHANGELOG 和本文里的地址一起替换。

## 1. 一次性配置签名密钥

正式 release 需要一个固定密钥，否则不同机器构建的 APK 签名不同，用户无法覆盖升级。

```bash
keytool -genkeypair -v \
  -keystore lianji-release.jks \
  -alias lianji \
  -keyalg RSA -keysize 4096 -validity 10000
```

把 `lianji-release.jks` 和密码离线备份好。**不要提交到仓库**（`.gitignore` 已经排除）。

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 添加四个 secrets：

| Secret | 内容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 密钥文件的 base64，一行 |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | `lianji` |
| `RELEASE_KEY_PASSWORD` | key 密码 |

生成 base64：

```bash
# macOS
base64 -i lianji-release.jks | pbcopy

# Linux
base64 -w0 lianji-release.jks
```

没有配置这些 secrets 时，release 工作流仍然能跑，但会用 debug 签名，并把产物命名为
`lianji-<版本>-debug-signed.apk`，只适合测试。**正式发布请务必配置 secrets。**

### 本地发布（可选）

复制 `keystore.properties.example` 为 `keystore.properties`，填好路径和密码，然后：

```bash
./gradlew :app:assembleRelease
```

`keystore.properties` 已在 `.gitignore` 中。

## 2. 发版前检查

1. 更新 `app/build.gradle.kts` 里的默认 `versionCode` / `versionName`（tag 会覆盖它们，
   但本地构建保持一致更不容易出错）。
2. 更新 `CHANGELOG.md`，把 `[Unreleased]` 移到新版本号下。
3. 更新 README 里的「当前版本」。
4. 本地跑一遍：

```bash
./gradlew :catalog-tools:test :app:testDebugUnitTest :app:assembleRelease
```

5. 有模拟器/手机时再跑：

```bash
./gradlew :app:connectedDebugAndroidTest
```

6. 确认 `app/build/outputs/apk/release/app-release.apk` 的版本、签名和体积：

```bash
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -1
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 3. 打 tag 触发自动发布

```bash
git add .
git commit -m "chore: release 1.2.0"
git push origin main

git tag -a v1.2.0 -m "练迹 1.2.0"
git push origin v1.2.0
```

tag 必须是 `v主版本.次版本.修订号` 的形式（可以用 `v1.2.0-rc.1` 这样的预发布后缀）。

## 4. release 工作流做了什么

[.github/workflows/release.yml](../.github/workflows/release.yml)：

1. 从 tag 解析版本号，`versionName=1.2.0`，`versionCode=10200`（`主*10000 + 次*100 + 修订`）。
2. 安装 JDK 21 和 Android SDK 36 / build-tools 35。
3. 解码 `RELEASE_KEYSTORE_BASE64`，写入临时密钥文件；没有就退回 debug 签名。
4. 跑目录校验、单元测试、`assembleRelease`（R8 + 资源压缩）。
5. 核对 906 张动作图仍在 APK 中（`assertExerciseArtRetained`）。
6. 校验签名，把 APK 重命名为 `lianji-<版本>.apk`，生成 `.sha256`。
7. 用 `softprops/action-gh-release` 创建 GitHub Release，自动生成 release notes，
   上传 APK 和校验和；预发布 tag 会标成 pre-release。

也可以在 **Actions → Release → Run workflow** 里手动输入 tag 重跑。

## 5. 发布后检查

- Release 页面有 APK 和 `.sha256`。
- 下载后本地校验：

```bash
shasum -a 256 lianji-1.2.0.apk
```

- 安装测试：

```bash
adb install -r lianji-1.2.0.apk
```

- 如果用户的旧版本是 debug 签名，而新版本是正式签名，需要卸载重装；从正式签名版本开始就不会再有这个问题。

## 6. Fork / 改名

如果你 fork 了这个项目并发布自己的版本：

- 换一个 `applicationId`（`app/build.gradle.kts`），否则会和本项目的签名包冲突。
- 替换 README、CHANGELOG、本文里的 `ylfeng250/lianji`。
- 动作图仍然要遵守 CC BY-SA 4.0，保留 [NOTICE.md](../NOTICE.md) 和
  [LICENSE-ASSETS](../LICENSE-ASSETS)。
- 不要用「练迹」这个名字和图标去暗示官方版本。

## 7. 常见问题

**Q: CI 里构建失败，提示找不到 `platforms;android-36` 或 `build-tools;35.0.0`？**
工作流会安装这两个包；如果是 fork 的旧 runner，确认 `ANDROID_HOME` 和
`cmdline-tools/latest/bin/sdkmanager` 存在。

**Q: release 是 debug 签名？**
没配置 `RELEASE_KEYSTORE_BASE64`。检查 secrets，或看 workflow 的 warning。

**Q: `git push` 报错 `refusing to allow an OAuth App to create or update workflow ... without workflow scope`？**
用 `gh` 的 OAuth token 通过 HTTPS 推送时，token 缺少 `workflow` scope。二选一：

```bash
gh auth refresh -h github.com -s workflow   # 重新授权，补上 workflow scope
# 或把 remote 换成 SSH（SSH key 不受 OAuth scope 限制）
git remote set-url origin git@github.com:ylfeng250/lianji.git
```

`gh auth refresh` 会打开浏览器，授权后重试 `git push` 即可。

**Q: 版本号不对？**
tag 必须是 `v1.2.3`；工作流会用 tag 覆盖 Gradle 默认值。手动跑时也可以传
`-PappVersionName=1.2.3 -PappVersionCode=10203`。

## 8. GitHub 仓库设置建议

第一次推送后，在仓库页面把这些打开：

- **About**：描述写「离线、无网络权限的中文训练记录 · Offline workout log for Android」；
  Topics 建议：`android`、`kotlin`、`jetpack-compose`、`material3`、`room`、`offline-first`、
  `fitness`、`workout-tracker`、`no-internet`。
- **Features → Discussions**：开启，用来放使用问题和动作纠错。
- **Security → Private vulnerability reporting**：开启，`SECURITY.md` 里的流程依赖它。
- **Branches → main 保护**：要求 PR + CI 通过，避免直接推坏 release。
- **Releases → 勾选 “Set as latest release”** 由工作流自动处理，不需要手动改。
- **Actions → General**：Workflow permissions 保持 “Read repository contents”，
  release 工作流自己声明了 `contents: write`。
- **Secrets**：按本文第 1 节配置发布密钥；fork 的人如果不发布正式包，可以不配。

推送前确认仓库地址不是 `ylfeng250/lianji` 的话，替换 README、CHANGELOG、本文和
`.github` 工作流里出现的地址（工作流本身不依赖仓库名，只有 README/徽章需要）。
