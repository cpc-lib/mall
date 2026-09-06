# GitHub AI 管线行动卡

> 用在：AI 产出要进入多人协作和主干合并。目标是用 Git + GitHub workflow 把 AI 的改动变成可追溯、可审查、可阻断的工程对象。

## 输入

- GitHub 仓库。
- issue 模板。
- PR 模板。
- 分支和 commit 约定。
- workflow：Maven 验证命令。默认使用 `mvn test`；如果项目存在依赖真实外部环境的集成测试，PR 必须写明本次可复现的最小 Maven 等价命令和未覆盖原因。

## AI 动作

1. 先复述 issue 的目标、验收标准、不要动的范围。
2. 开独立分支，不直接改主干。
3. 小步 commit，提交信息写 `Refs #<issue号>`。
4. 开 PR，正文写 `Closes #<issue号>`。
5. 等 workflow 跑 Maven 验证命令。
6. 如果红，根据失败日志做最小修复。
7. 在 PR 里说明改动范围、验证命令、未改动范围、风险。

## 人审动作

- 检查 issue 是否写清验收标准和不要动的地方。
- 检查 PR 是否只做一件事。
- 检查 diff 是否可 review、可回退。
- 检查 workflow 是否设为 required check。
- 检查失败修复是否扩大 scope。
- 最终 merge 由人确认。

## Git 管理保障了什么

- **issue 划边界**：AI 不能自由发挥。
- **branch 隔离风险**：主干不会被半成品污染。
- **commit 定位责任**：出问题能用 diff / blame / bisect 追到哪一步。
- **PR 形成审查对象**：人审的是代码差异，不是聊天记录。
- **required check 强制纪律**：不是提醒跑测试，而是不绿就合不了。

## 完成标准

- issue → branch → commit → PR 可追溯。
- workflow 的 Maven 验证命令全绿；如存在外部环境型测试失败，必须在 PR 中列明失败项、原因和人工裁决点。
- required review 通过。
- PR 合并后能自动关闭对应 issue。

## 禁止事项

- 禁止 AI 产出绕过 workflow。
- 禁止红 CI 合并。
- 禁止重构、修 bug、新功能混在一个 PR。
- 禁止把 AI review 当确定性门禁。

## 可复制 Prompt

```text
请按这个 GitHub issue 工作：

1. 先复述 issue 的目标、验收标准和不要动的范围；
2. 给出最小实现计划；
3. 创建一个只解决本 issue 的小步修改；
4. 提交信息包含 Refs #<issue号>，PR 正文包含 Closes #<issue号>；
5. 运行 workflow 的本地等价 Maven 命令，例如：mvn test；如果项目存在外部环境依赖，使用本 issue 明确的最小 Maven 验证命令并说明未覆盖范围；
6. 如果失败，只根据失败信息做最小修复；
7. PR 描述里列出：改动范围、验证命令、未改动范围、风险。

不要自动合并，不要扩大 scope。
```
