# Javaagent 拓扑下的共享类空间 — 可行性研究

> Aprism FACT.md 9.2（Knot 式共享类空间）研究交付物。由 opencode 智能体
> （模型：ox-alpha）在 AprismRefract v26.8 兼容线期间的真机证据基础上
> 撰写。本文为中文镜像，英文为规范原文。

## 1. 问题陈述（真机证据）

mixin 重度优化模组会将**自家类的引用注入原版类**。实测案例：Lithium
mc26.2-0.25.3（Fabric）织入 `EntityPushableMixin`，使被转换的
`net.minecraft` 类引用
`net.caffeinemc.mods.lithium.common.entity.pushable.FeetBlockCachingEntity`。
当前拓扑下 JVM 在原版 bootstrap 期间于 main 线程死亡：

```
Exception in thread "main" java.lang.NoClassDefFoundError:
  net/caffeinemc/mods/lithium/.../FeetBlockCachingEntity
  at jdk.internal.loader.BuiltinClassLoader.findClassOnClassPathOrNull
```

根因：原版类由系统（app）加载器定义，模组类位于子加载器
`AprismClassLoader`——父定义的类永远无法解析仅存于子加载器的类。

v26.8 实测线观察到的兼容性分层：

| 层级 | 行为 | 示例 |
|---|---|---|
| 1 | 构造并运行 | JEI、FerriteCore、examplemod |
| 2 | 构造成功；入口点等待客户端启动事件 | 需要 FMLClientSetupEvent 的模组（CLIENT 阶段分发缺口，独立事项） |
| 3 | 需要共享类空间 | Lithium 及一切向原版类注入模组类引用的 mixin |

## 2. 候选方案

### (A) AprismClassLoader 对游戏命名空间子优先 — 否决（根本性缺陷）

JVM 入口类 `net.minecraft.client.main.Main` 必须由系统加载器定义
（启动器契约；javaagent 无法迁移它），其传递闭包（SharedConstants、
Bootstrap、BuiltInRegistries 等）因此必然由系统侧定义。子优先定义会为
"模组引用到但 Main 尚未触及"的游戏类创建**第二份副本**——注册表身份
分裂（BuiltInRegistries 被定义两次是灾难性的）。该重复种群风险是
javaagent 拓扑的固有属性，非实现细节。

### (B) Instrumentation.appendToSystemClassLoaderSearch — 推荐（javaagent 兼容的共享空间）

通过 `appendToSystemClassLoaderSearch(JarFile)` 将模组 jar 追加到系统
加载器。模组与原版共享同一个定义加载器：

- 原版 → 模组引用可解析（修复 Tier 3）
- 模组 → 原版引用可解析
- 全局 `ClassFileTransformer`（经 `inst.addTransformer` 注册的
  AprismClassTransformer）继续织入——它本就对系统加载器定义生效
  （今天的原版 mixin 即如此工作）
- 类加载器拓扑零改动；AprismClassLoader 保留服务旧线

代价与缓解：

| 代价 | 缓解 |
|---|---|
| 不可逆（jar 无法移除） | 失去 `URLClassLoader.close()` 的文件锁释放；在热重载存在前可接受（尚无热重载；游戏退出即释放） |
| BytecodeRemapper 挂在 `AprismClassLoader.findClass`；模组类将改经系统加载器定义 | 现代主目标（NO_REMAP，MC 26.1+）无重映射——直接可用；REMAPPED（pre-26.1）档保留现路径（开关门控） |
| `AprismMixinService` 经 AprismClassLoader 路由查找 | 扩展为同时查询系统加载器（小而内聚的改动） |
| 无逐模组隔离 | 与 9.2 既有意图一致（共享空间本就是设计） |

### (C) 包装启动模式 — 战略备选

mdl 启动 `net.aprism.bootstrap.Main`，由它构建 AprismClassLoader 作为
包括游戏入口类在内的一切的加载器（真正的 Knot 对等，无重复种群）。
产品身份从"javaagent"变为"包装启动器"；触及 mdl `--aprism` 契约。
待 (B) 被证明不足后再议。

### (D) AJR 内建加载器 — 长期归宿

AprismJDK 运行时可原生集成加载器。归入 AJR 线，此处不展开。

## 3. 建议

以 `aprism.sharedClassSpace=system`（默认关闭；现拓扑作为回退）实现
(B)，首期仅覆盖 NO_REMAP 档。顺序：

1. `AprismRuntime.loadMods` 中对模组 jar 执行
   appendToSystemClassLoaderSearch（开关门控）
2. AprismMixinService 查找回退至系统加载器
3. LoadReport / 模组列表不变（发现机制本就独立）
4. 真机 E2E：Lithium 构造 + 进世界（Tier 3 验收）
5. 现代线验证后再决策 REMAPPED 档支持

## 4. 证据基础

- 2026-08-24/25 真机 E2E 会话（refract-e268 实例，MC 26.2，JRE 25）：
  上表 Tier 1/2/3 行为；完整栈轨迹归档于 AprismRefract FACT.md 会话志。
- JEI 30.25.0.177 在 Aprism 下构造成功（v26.8 + GameBootstrapGate）；
  经 Despotes 原始协议驱动进世界；14 分钟世界内运行。

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
