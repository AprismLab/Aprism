# Aprism BE：方案研究报告（v26.2-Alpha.4，目标 #5）

> 作者：BlockConnect@StarsailsClover
> 状态：研究交付物；用于指导 BE 原生平台注入器产品线的后续工作。
> 语言：中文版（英文权威版见 `01-be-approach-en.md`）。
> 引用来源在文中内联标注；涉及第三方项目的论断截至 2026-08-10，撰写前已对照其仓库核实。

## 1. 问题定义

Minecraft 基岩版（BE）是闭源 C++ 二进制，没有官方原生模组 SDK。官方 Script API（`@minecraft/server`）是沙箱化的 JavaScript 运行时，除 JSON 定义的内容外无法表达自定义方块、物品或维度。因此，要在 BE 上达到与 JE 模组对等的能力，原生模组化是唯一路径。

两个事实主导整个设计空间：

1. **版本脆弱性。** Bedrock 更新会不可预测地移动函数偏移与结构体布局。所有生产级 BE 加载器（Amethyst、LeviLamina）都以「按版本逆向的头文件 + 签名/偏移数据库」来应对。不存在可链接的稳定 ABI。
2. **各平台代码签名与进程保护差异。** 每个 BE 平台（Windows、Android、iOS、BDS 服务器）对注入施加不同约束：Windows 的 DLL 加载规则、Android 的 SELinux + Zygote 进程模型、iOS 的重签名要求，以及 BDS 无反作弊但也无持久化。

Aprism 已承诺的范围（FACT.md 9.16）：BE 仅支持 26.x 及以后版本；fail-closed（拒绝而非猜测）的版本解析；版本适配器 + 签名数据库是 BE 子系统中最重要的一项工程投资。

## 2. Aprism 已有基础（v26.1 线）

v26.1 线交付了完整的 **Java 侧** BE 基础（aprism-loader-core `com.aprism.loader.bedrock`）：

| 组件 | 职责 |
|---|---|
| `BedrockVersionDatabase` | fail-closed 版本 + 签名数据库（JSON schema v1） |
| `BedrockVersionAdapter` | 归一化原始 BE 版本号，强制 26.x 范围边界，对库解析 |
| `BedrockSignatureDbLoader` | 加载并校验磁盘上的签名库 |
| `BedrockInjectionPlan` | 纯 Java 注入计划计算，模组收集前按版本门控 |
| `BedrockInjectionCoordinator` | 将 适配器 -> 平台检测 -> 计划 串入 `AprismRuntime` |
| `BedrockNativeStager` | 从 `.abe` 归档中按平台安置原生库 |
| `NativeInjector`（SPI） | 原生平台注入器实现的接缝 |

`.abe` 发现、按平台原生库解析（`windows-x64`/`android`/`ios`...）、BP/RP/scripts 内容检测也已就位（v26.0 线）。剩余的是 **原生半区**：各平台实际的进程附着、符号解析与钩子应用。

## 3. 候选路线

### 3.1 Windows 客户端：代理 DLL 劫持 + 内联钩子

游戏会优先从自身目录加载 `version.dll`（早于系统目录的同名库）。一个具有相同导出表的代理 DLL 会先被加载，使 Aprism 在一个明确定义的时刻（一个被合法加载的模块的 DllMain）获得游戏进程内的代码执行。对于预加载真实 `version.dll` 的加固启动器，手动映射注入是回退手段。

- **钩子引擎：** MinHook 或 SafetyHook 做内联钩子；libhat 做签名扫描，从签名库定位函数。
- **先例：** Amethyst（FrederoxDev）正是用这套技术栈（libhat + MinHook，xmake 构建）实现 MCBE 1.21-1.26.x 客户端模组化。截至本报告撰写时，Amethyst 核心运行时仍开源，1.26.x 支持以「自带类型」库的形式发布。
- **风险：** Windows Defender 对手动映射的启发式拦截；以签名构建与优先代理 DLL 路径缓解。
- **结论：** 已被验证，研究风险最低。**主路线（P0）。**

### 3.2 Android（root）：Zygisk 模块 + ShadowHook

Magisk/Zygisk 模块通过 Zygote 派生游戏进程时的特化注入（类 `LD_PRELOAD` 方式），随后用 ShadowHook（PLT + 内联，ART 感知）应用内联钩子。

- **先例：** 这是 Android 游戏钩子的标准技术栈；LeviLamina 的社区工具与多个 BE 模组管理器已经发布基于 Zygisk 的加载器。
- **风险：** 需要 root（Magisk/KernelSU）；加固 ROM 上的 SELinux 拒绝；Magisk 分支间 Zygisk API 变动。
- **结论：** 在可接受 root 的前提下已被验证。**P1。**

### 3.3 Android（无 root）：容器 + 预加载劫持

在由应用容器（NMLauncher 式）中运行游戏，容器掌控进程环境并注入其自有的预加载库。

- **风险：** 容器必须按 Android/BE 组合重建；Google Play 完整性检查可能标记容器化安装；维护面大于 root 路径。
- **结论：** 可行但最脆弱的 Android 路径。**P1（在 root 路线稳定之后）。**

### 3.4 iOS/iPadOS：TrollStore 侧载 + 重签名

`insert_dylib` 向 BE 二进制添加 `LC_LOAD_DYLIB` 加载命令；应用重签名后经 TrollStore 安装（无需开发者账号）。钩子用 Dobby，新版 iOS 上以 ElleKit 为回退底座。

- **风险：** 按版本维护、负担重；TrollStore 可用性受 iOS 版本门控；App Store 政策禁止动态代码注入，因此该路线明确走商店外分发（FACT.md 9.11）。
- **结论：** 仅研究级。**P2。**

### 3.5 macOS/Linux：经由 LeviLamina 式加载器的 BDS 服务端

macOS/Linux 没有 Bedrock *客户端* 二进制；只有专用服务器（BDS）可运行。LeviLamina（LiteLDev）是事实上的 BDS 模组加载器，跟随当前 Bedrock 版本号维护，经其 `lip` 包管理器安装，处于活跃维护状态。

- **选项 A（推荐）：** 将 LeviLamina 视为 BE 服务端底座，把 Aprism BE 模组以 LeviLamina 兼容包的形式发布到 BDS，并在 Aprism 侧提供适配器，把 `.abe` 原生层翻译为 LeviLamina 插件格式。这避免了重复 BDS 注入工作。
- **选项 B：** 自建 BDS 注入器。工作量大，重复已解决该问题的生态，并使签名库工作碎片化。
- **结论：** **选项 A。** 集成，而非分叉。

### 3.6 主机

锁定系统，模组化的代码执行不可行。**不在范围内。**

## 4. 推荐架构

对原生加载器核心采用 LeviLamina 三层模式（FACT.md 9.9），跨 Windows/Android/iOS 共享：

1. **逆向头文件层** —— 由 `BedrockAnalyzer` 输出驱动的 `header_generator` 工具链自动生成；以头文件形式暴露类布局、vtable 索引与函数签名。
2. **核心层** —— 模组注册器、钩子管理器，以及 **版本适配器 + 签名库客户端**。签名库沿用 Aprism Java 侧已发布的 JSON schema v1（`BedrockSignatureDbLoader`），使同一个社区维护的签名仓库同时服务 Java 计划器与原生解析器。
3. **公开 API 层** —— 事件、命令、注册表访问；经清单 `type` 字段支持多语言模组（C++/Lua/C#/Rust）。

源码布局：`src/`（共享）、`src-client/`（客户端入口点）、`src-server/`（BDS 入口点）。

### Fail-closed 契约（承袭自 Java 侧）

- 当运行中的 BE 版本低于 26.x、不在签名库中、或存在未解析的必需签名时，原生注入器必须拒绝附着。
- 每一次拒绝都通过 Java 协调器既有的拒绝原因（`UNPARSEABLE` / `OUT_OF_SCOPE` / `NOT_IN_DATABASE`）产生人类可读的报告。

### NativeInjector 实现（每条路线一个）

| 平台 | 实现 | 加载方式 | 钩子方式 |
|---|---|---|---|
| Windows | `aprism-be-windows` | 代理 `version.dll`（手动映射回退） | MinHook/SafetyHook + libhat |
| Android root | `aprism-be-android` | Zygisk 模块 | ShadowHook |
| Android 无 root | `aprism-be-android-container` | 容器预加载 | ShadowHook |
| iOS | `aprism-be-ios`（研究） | TrollStore 重签名 dylib | Dobby / ElleKit |
| BDS 服务端 | LeviLamina 之上的适配器 | LeviLamina 插件 | LeviLamina API |

## 5. 分阶段计划

| 阶段 | 里程碑 | 退出标准 |
|---|---|---|
| P0 | Windows 代理 DLL 注入器 MVP | 真实 BE 26.x 带 Aprism 附着启动；一个原生钩子触发；未知版本 fail-closed |
| P1 | 签名库社区流水线 | 签名仓库公开；适配器在 Windows 上端到端消费它 |
| P2 | Android root 注入器 | Zygisk 模块在真机上附着成功 |
| P3 | BDS 适配器 | LeviLamina 兼容的 `.abe` 包加载一个 hello-world 原生模组 |
| P4 | Android 无 root + iOS 研究 | 容器路线评估完成；iOS 探针报告 |

## 6. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| Bedrock 更新移动偏移 | 高 | 高 | 签名库 + fail-closed 适配器 + 社区签名快速更新 |
| Windows Defender 标记注入 | 中 | 中 | 签名构建；优先代理 DLL 而非手动映射 |
| Magisk 分支间 Zygisk API 变动 | 中 | 中 | 锁定 Zygisk API 版本；以薄垫片抽象 |
| TrollStore 可用性门控 iOS | 中 | 低 | iOS 保持研究级；发布版本支持矩阵 |
| 启用 Live 的存档封禁风险 | 低 | 高 | 明确披露（Doc 01 §13.2）；推荐离线存档 |
| 重复 BDS 工作 | 中 | 中 | LeviLamina 适配器（选项 A）而非自建注入器 |

## 7. 决策记录

- **DEC-BE-1：** Windows 代理 DLL + MinHook/SafetyHook + libhat 为主原生路线（P0）。手动映射仅作回退。
- **DEC-BE-2：** 签名库保持 JSON schema v1，由 Java 计划器与所有原生注入器共享；社区维护的仓库是唯一事实来源。
- **DEC-BE-3：** BDS 服务端支持集成 LeviLamina，而非分叉自建注入器。
- **DEC-BE-4：** Android root 优先于 Android 无 root；iOS 在 TrollStore 稳定性改善前保持研究级。
- **DEC-BE-5：** 所有原生注入器遵守 fail-closed 契约；不对未解析签名做投机钩子。

## 8. 开放问题

- 原生核心采用 C++（与 LeviLamina 对齐）还是 Rust（内存安全）？C++ 与当前生态和工具链匹配；Rust 增加 FFI 与头文件生成的摩擦。推迟到 P0 探针决定。
- 签名库治理：Aprism 组织下的单一仓库，还是中立社区组织（AmethystAPI/Community-Headers 先例）。推迟到 P1。
- 类主机平台（Windows 掌机）继承 Windows 路线；不单独规划。

## 9. 参考资料

- FACT.md §9.8（BE 按平台注入）、§9.9（BE 加载器架构）、§9.16（BE 仅 26.x 范围）。
- 文档 01（架构设计）§7（BE 注入与加载器子系统）、§13.2（Bedrock 封禁风险披露）。
- Amethyst — github.com/FrederoxDev/Amethyst（MCBE 1.21-1.26.x 客户端加载器；libhat + MinHook；核心运行时开源，2026-08-10 核实）。
- LeviLamina — github.com/LiteLDev/LeviLamina（跟随当前 Bedrock 版本号的 BDS 模组加载器；经 `lip` 安装；2026-08-10 核实）。
- libhat、MinHook、SafetyHook、ShadowHook、Dobby、ElleKit、Zygisk：钩子与签名扫描库，引用见 Doc 01 §15。
