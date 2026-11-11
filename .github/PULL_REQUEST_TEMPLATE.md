## 改了什么

<!-- 一两句话说明这个 PR 的目的 -->

## 为什么

<!-- 关联 issue：Fixes #123 -->

## 怎么验证的

- [ ] `./gradlew :catalog-tools:test :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleRelease`
- [ ] 有设备时跑了 `./gradlew :app:connectedDebugAndroidTest`
- [ ] UI 改动附了截图

## 检查清单

- [ ] 没有新增网络权限
- [ ] 没有提交 `local.properties`、密钥、`build/` 或 `third_party/`
- [ ] 动作图/文字改动保留了来源和 CC BY-SA 4.0 / Unlicense 署名
- [ ] 数据层改动使用 `@Update` 而不是父子表上的 `REPLACE`
- [ ] 必要时更新了 `CHANGELOG.md` 和文档
