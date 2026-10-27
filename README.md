# God（Fabric 1.20.1）

自行摸索

## 光影 / Iris 渲染适配

- Fabric：`WorldRenderEvents.LAST` 抓取 Iris 世界深度（实体/天气之后、final 之前），
  `WorldRenderEvents.END`（LevelRenderer RETURN，Iris final 之后）绘制神环。
- 通过反射读取 `IrisRenderingPipeline.renderTargets` → `RenderTargets.getDepthTexture()`，
  在片元着色器里做真实深度比较。
- 结果：背后神环完整，正面玩家身体遮挡神环，墙体/地形正常遮挡。
- 需要 Iris 1.7.x / 1.8.x；未安装时自动走原生深度路径。
- Forge 端对应 `AFTER_WEATHER` 抓深度、`AFTER_LEVEL` 绘制。
