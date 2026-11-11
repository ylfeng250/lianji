# 安全与隐私

## 支持的版本

| 版本 | 状态 |
|---|---|
| 1.1.x | 接收安全修复 |
| 1.0.x | 不再维护 |

## 报告问题

- 优先使用 GitHub 的 **Security → Report a vulnerability**（私有安全公告）。
- 如果不涉及敏感信息，也可以开一个普通 issue。
- 请附上版本号、Android 版本/机型、复现步骤；不要附真实的个人训练数据或备份文件。

## 设计上的边界

- 应用**不申请网络权限**（`INTERNET` / `ACCESS_NETWORK_STATE` 在合并清单里被检查并移除），
  没有账号、广告、分析或崩溃上报。
- 训练数据只存在本机 Room 数据库和 DataStore；`android:allowBackup="false"`。
- 备份文件由用户通过系统文件选择器主动导出/导入，应用不会自动上传。
- 唯一的显式权限是 `VIBRATE`。

## 需要注意的地方

- 导入的 `.lianji` 文件是用户主动选择的文件；只从可信来源导入备份。
- 发布用的签名密钥只保存在维护者本地和 GitHub Actions secrets，不进入仓库。
- 依赖通过 Dependabot 定期更新；CI 在每次 PR 上构建，release 工作流只从 tag 触发。
