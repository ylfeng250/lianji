# NOTICE

练迹（Lianji）把不同来源的内容组合在一起。**代码和素材的许可证是分开的**，下面逐项说明。

## 代码

- `app/`、`catalog-tools/`、构建脚本和测试：MIT，见 [LICENSE](LICENSE)。
- 不包含 GymMane、LibreFit 或其他 GPL 项目的源代码。交互设计参考了它们的公开界面和产品思路，
  实现是独立写的；详见下文「设计参考」。

## 动作图（CC BY-SA 4.0）

- 来源：[Workout Guide](https://github.com/bryllim/workout-guide)，作者 [Bryl Lim](https://bryllim.com)。
- 其中 76 张第一帧是 [Everkinetic](https://github.com/everkinetic/data) 位图矢量化后的改编，
  Everkinetic 同样采用 CC BY-SA 4.0。
- 本项目的改动：
  - 把 SVG 转成 Android VectorDrawable，按 Android 限制拆分过长路径；
  - 每个动作取三帧，生成 906 个 `app/src/main/res/drawable/ex_*.xml`；
  - 运行时用主题色着色，不改变图形几何；
  - 补充中文动作名、肌群、器械和训练类型。
- 上述衍生图按 CC BY-SA 4.0 分发，见 [LICENSE-ASSETS](LICENSE-ASSETS)。
- 完整的上游许可和署名文本随安装包一起打包在 `app/src/main/assets/licenses/`。

应用内「关于、隐私与许可」页面展示的也是这些文本。

## 分步文字与原创说明

- 来源：[free-exercise-db](https://github.com/yuhonas/free-exercise-db)，原文为 Unlicense（公有领域）。
- 只使用文字，不使用该项目的图片；85 个动作的步骤被译成简体中文，按 Unlicense 使用。
- 另外 217 个动作在 free-exercise-db 中没有可靠匹配，使用本项目原创的中文说明（MIT），
  不冒充上游内容。
- 译文的署名与来源记录在 `catalog-work/matched.json` 和 `app/src/main/assets/catalog/exercises_zh.json` 的
  `instructionSourceId` 字段里。

## 目录与审计数据

- `catalog-work/manifest.json`：来自 Workout Guide 的动作元数据，MIT。
- `catalog-work/matched.json`、`catalog-work/slugs.txt`：本项目生成的匹配结果。
- `catalog-work/exercises.json`：free-exercise-db 原始数据，Unlicense；体积较大，不提交，按
  [CONTRIBUTING.md](CONTRIBUTING.md) 的说明自行获取。
- `catalog/match_audit.json`：生成脚本输出的匹配审计，便于核对每个动作采用了哪条文字。

## 设计参考（未使用代码）

- [GymMane](https://github.com/InlitX/GymMane)（InlitX，GPL-3.0）
- [LibreFit](https://github.com/LibreFitOrg/LibreFit)（LibreFitOrg，GPL-3.0）

两个项目都采用 GPL，本项目**没有复制它们的源代码、资源或文案**。参考的是产品层面的公开信息：
训练记录的信息层级、动作筛选、休息计时、历史记录的组织方式。练迹的交互决策记录在
[docs/INTERACTION.md](docs/INTERACTION.md)。

## 第三方库

AndroidX、Jetpack Compose、Material 3、Room、Navigation、DataStore、Kotlin 和 kotlinx 系列库
均为 Apache License 2.0。应用没有捆绑自定义字体。

## 截图

`docs/screens/*.png` 是应用截图，其中包含 CC BY-SA 4.0 的动作图，因此这些截图整体按
CC BY-SA 4.0 提供。

## 应用图标

`app/src/main/res/drawable/ic_launcher_foreground.xml` 和自适应图标是本项目原创的简单图形，
随代码按 MIT 提供。

## 声明

动作图示、步骤和统计只作为训练参考，不能替代教练、医生或康复人员的指导。
