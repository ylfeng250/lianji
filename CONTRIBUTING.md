# 贡献指南

谢谢你愿意帮忙。练迹是一个个人项目，但按开源项目的方式维护：改动尽量小、能复现、能测试。

## 开始之前

- 先搜一下已有的 issue，避免重复。
- 大改动（数据模型、导航结构、动作目录格式）先开 issue 聊一下，再写代码。
- 不接受：联网同步/账号/分析 SDK、来源不明的图片或字体、医疗/康复承诺、把动作图换成非 CC BY-SA 授权素材。
- 涉及动作图的改动必须遵守 [NOTICE.md](NOTICE.md) 的 CC BY-SA 4.0 要求。

## 环境

- JDK 21
- Android SDK 36（platforms;android-36、build-tools;35.0.0）
- Python 3.10+（只有重新生成动作目录时才需要）

`local.properties` 写 `sdk.dir=...`，或者设置 `ANDROID_HOME`。

```bash
./gradlew :catalog-tools:test :app:testDebugUnitTest :app:assembleDebug
```

## 目录

```
app/src/main/java/com/superman/lianji/
  core/data/        Repository、设置、备份、目录导入（AppGraph.kt）
  core/database/    Room 实体、DAO、数据库（LianjiDatabase.kt）
  core/domain/      纯逻辑：训练规则、统计、备份格式、排序
  core/ui/          主题、图表、ExerciseArt、滑动/多选/Snackbar 组件
  feature/          今日、动作库/详情、训练、计划、数据、我的
  src/test/         JVM 单元测试
  src/androidTest/  设备测试 + 截图测试
catalog-tools/      动作目录生成脚本与 Kotlin 校验器
```

架构是单 Activity + Compose Navigation，`AppContainer` 手动装配依赖，没有 DI 框架。
数据层用 Room Flow 暴露给 ViewModel，界面状态尽量留在 ViewModel。

## 代码约定

- Kotlin 官方风格（`kotlin.code.style=official`），4 空格缩进。
- Composable 尽量无状态；状态放 ViewModel，纯计算放 `core/domain` 并补单元测试。
- 新页面沿用现有的交互语言：长按多选、左滑删除、删除必须可撤销、危险操作才用确认框。
- 文案用简体中文，避免英文缩写（单位除外）。
- 不引入需要网络权限的依赖；合并清单里出现 `INTERNET` 会让构建失败。
- 数据库变更：Room 版本号 + 迁移 + 更新 `app/schemas/`；不要依赖破坏性迁移。
- **不要在父子表上用 `@Insert(onConflict = REPLACE)` 做更新**。SQLite 的 REPLACE 是先删再插入，
  会触发 `ON DELETE CASCADE` 把子记录删掉；更新请用 `@Update`。`RoutineRepositoryTest` 是这条的回归测试。

## 动作目录与美术资源

动作图是生成文件，不要手改 `app/src/main/res/drawable/ex_*.xml`。

```bash
# 只需要动作元数据/文字时不一定要跑；重新生成全量目录时需要：
git clone https://github.com/bryllim/workout-guide.git third_party/workout-guide-src
python3 catalog-tools/generate_catalog.py
./gradlew :catalog-tools:test :app:testDebugUnitTest
```

`catalog-work/matched.json` 和 `catalog/match_audit.json` 已经提交，一般不需要重新匹配 free-exercise-db；
只有要改匹配结果时才需要它的原始数据（`catalog-work/exercises.json`，Unlicense，不提交）：

```bash
git clone https://github.com/yuhonas/free-exercise-db.git third_party/free-exercise-db
cp third_party/free-exercise-db/dist/exercises.json catalog-work/exercises.json
```

修改中文名、肌群或步骤时：

1. 名称改 `catalog-tools/names_zh.json`；
2. 步骤改 `catalog-tools/src/main/resources/instructions_zh.json`，只能使用 free-exercise-db
   （Unlicense）里匹配到的文字，并保持步骤数量与原文一致；
3. 不要引入 free-exercise-db 的图片；
4. 重新生成后确认 `catalog/match_audit.json` 的变化符合预期；
5. 如果新增/替换了动作图，在 PR 里说明来源和改动，并确认仍为 CC BY-SA 4.0。

## 测试

```bash
# 必须通过
./gradlew :catalog-tools:test :app:testDebugUnitTest :app:assembleRelease

# 有模拟器/手机时
./gradlew :app:connectedDebugAndroidTest
```

- 新增交互（尤其是滑动、批量、撤销）时，在 `InteractionTest` 里补一条设备测试。
- 纯计算逻辑在 `app/src/test` 里补单元测试。
- 改数据层时优先补一个“数据不会丢”的回归测试，参考 `RoutineRepositoryTest`。
- UI 改动可以跑 `ScreenshotTest`，把截图附在 PR 里。

## 提交与 PR

- 一个 PR 只做一件事；标题用中文或英文都可以，建议带类型前缀，如 `fix: 修复计划改名丢动作`。
- PR 描述写清楚：改了什么、为什么、怎么验证的、有没有截图。
- 提交前确认：
  - [ ] 构建通过，新增测试通过
  - [ ] 没有新增网络权限
  - [ ] 没有提交 `local.properties`、密钥、`build/`、`third_party/`
  - [ ] 素材改动保留了署名和 CC BY-SA 4.0
  - [ ] 文档/CHANGELOG 在需要时更新

## 许可

- 代码贡献按 [MIT](LICENSE) 授权。
- 动作图及衍生图贡献按 [CC BY-SA 4.0](LICENSE-ASSETS) 授权。
- 文字贡献如果是翻译自 free-exercise-db，保留 Unlicense 来源说明。

## 发版

维护者流程见 [docs/RELEASING.md](docs/RELEASING.md)。
