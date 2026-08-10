# AprismJDK 设计文档（v26.4-Alpha.1，目标：AprismJDK 子项目）

> 研究 + 设计交付物。本文档定义 AprismJDK 是什么、为何存在、其架构，
> 以及通往可构建 OpenJDK 变体的路线图。JDK 构建本身的实现是后续里程碑；
> 本 Alpha 交付设计、子项目骨架与治理契约。
>
> 作者：BlockConnect@StarsailsClover。英文权威版：
> `docs/research/aprismjdk/01-design.md`。仓库：`AprismLab/AprismJDK`
> （GPL-2.0）。

---

## 1. 定位

**AprismJDK**（工作品牌：**AJR — Aprism Java Runtime**）是一个
OpenJDK 变体发行版，首要服务于 Aprism 加载器生态，其次服务于通用 Java
社区。它**不是**一个在语言/VM 语义上偏离 OpenJDK 的分支；它是上游
OpenJDK 之上的*构建 + 补丁层*，与 Amazon Corretto、Azul Zulu、
Microsoft Build of OpenJDK、Red Hat OpenJDK 同属一个家族。

其差异化在于 AprismJDK 是**自底向上围绕 Aprism agent 模型**设计的：
该 JDK 随附一个 **AprismateAgent**（类 JavaAgent 的底层 agent）与一组
**开放的、稳定的 JVM 接口**，Aprism 的深度 API 以它们为目标。主流发行版
为服务器吞吐与企业 LTS 优化，而 AprismJDK 为以下目标优化：

1. **底层触达** —— 一等支持运行时的类重定义、方法钩挂与字节码转换
   （与 Aprism 的 `ClassRedefiner`/`MethodHookRegistry` 相同的能力，
   但在 VM 层面加固）。
2. **性能 + 硬件融合** —— 将 CPU 特性探测、缓存行/NUMA 感知、向量化
   提示作为稳定 API 暴露。
3. **开放接口** —— 通过*稳定的、版本化的*表面暴露更多 JVM 内部，
   而非反射 hack。
4. **跨语言过渡** —— 基于 Foreign Function & Memory（FFM）API 构建的
   一等 Cpp2Java / Rust2Java 桥。
5. **极高兼容性** —— 跨越 Java 版本的兼容性矩阵，使针对某条 AprismJDK
   线构建的模组在更新后仍可运行。

### 1.1 为何需要自定义 JDK？

Aprism 已经**在原生 OpenJDK 上**通过 `java.lang.instrument` + ASM +
Mixin 栈达成加载器级目标（Mixin 织入、多 loader 同款、类型化注册表、
事件总线）。只有当原生 JVM 成为*硬性天花板*时，自定义 JDK 才有理由：

- **能力天花板。** 某些底层操作（如增删字段地重定义类、可靠钩挂被 JIT
  内联的热方法、读取某些 VM 内部状态）仅靠 `Instrumentation` 在原生
  HotSpot 上不可能或脆弱。打过补丁的 VM 可以安全支持它们。
- **性能天花板。** Aprism 需要确定性、低开销的钩子。原生 HotSpot 的
  安全点、去优化与内联可能与 agent 注入的代码相冲突。感知 Aprism 钩子
  点的 VM 能保持它们廉价。
- **接口天花板。** Aprism 的深度 API 目前通过 `ManagementFactory`、
  `Instrumentation` 与 FFM 触达 JVM。自定义 JDK 可把最常用的这些提升为
  *具名的、稳定的*接口。

因此设计将 AprismJDK 视为**赋能者**而非必需：每项 Aprism 能力仍须在
原生 OpenJDK 上工作（优雅降级），AprismJDK 只是*解锁*更深层级。

---

## 2. 与 Aprism 项目的关系

```
                 +-------------------------------------+
                 |            Aprism（加载器）          |
                 |  Mixin / 事件总线 / 注册表 /          |
                 |  同款面（在原生 JDK 上工作）          |
                 +-------------------------------------+
                                  |  深度 API 调用
                                  v
                 +-------------------------------------+
                 |         AprismJDK（AJR 运行时）      |
                 |  AprismateAgent + 开放 VM 接缝 +     |
                 |  性能/硬件 API + FFM 桥              |
                 +-------------------------------------+
                                  |
                                  v
                 +-------------------------------------+
                 |        上游 OpenJDK 25（LTS）        |
                 +-------------------------------------+
```

- **Aprism** 保持 JDK 无关。其深度 API（v26.4 线）检测是否运行在
  AprismJDK 上，并在其上升级行为。
- **AprismJDK** 独立版本化但*跟踪*上游 LTS 线。v26.4 以 **OpenJDK 25**
  为目标（当前 LTS，GA 2025-09，支持至约 2032）。上游移向下一 LTS 时，
  AprismJDK 重新基线。
- 二者通过**能力描述符**（见 §6）协调：Aprism 询问运行时"你暴露哪些
  AprismJDK 能力？"，运行时以版本化的能力集作答。

---

## 3. AprismateAgent

**AprismateAgent** 是旗舰组件：一个*随 JDK 镜像捆绑*、无需任何外部 jar
即可触达的类 JavaAgent agent。

### 3.1 入口点

与标准 JavaAgent 一样支持两种模式，但带 JVM 侧支持：

| 模式 | 触发 | 用途 |
|------|------|------|
| `premain` | `-javaagent:aprismate.jar=...`（或在 AprismJDK 上自动附加） | 加载期织入、启动期钩子注册 |
| `agentmain` | 运行时 Attach API | 向运行中的游戏热附加钩子 |

在 AprismJDK 上，agent 还可通过 JVM 标志（`-XX:+AprismateAgent`）
**自动加载**，在 `main` 之前接入 agent，免去启动器传递 `-javaagent`。
这就是"深入底层"的部分：VM 知晓该 agent，因此可以在原生 agent 无法
触达的位置安装钩子。

### 3.2 能力

agent 暴露稳定的程序化表面（`com.aprismate.api`）：

- **ClassRedefiner+** —— 重定义类，包含原生 `Instrumentation.
  redefineClasses` 拒绝的结构性变更（增删字段/方法）。由打过补丁的
  HotSpot 支撑，执行带实例迁移的安全类重定义。
- **MethodHookRegistry+** —— 在任意方法（含 JIT 编译的方法）上注册
  入口/出口钩子，VM 保证钩子在内联后存活（VM 将钩子点视为去优化锚点）。
- **BytecodeTransformer** —— ASM 支撑的管线钩子，在验证之前于加载期
  见到类，实现无需独立 Mixin 运行时的 Mixin 式织入。
- **VmIntrospection** —— 通过具名方法（而非 JMX 反射）读取线程栈、
  类统计、堆概览与 JIT/GC 状态。

### 3.3 失败安全契约

每项 agent 能力都**对游戏 fail-closed，绝不对 VM**：坏钩子被记录并跳过；
失败的转换回退到未转换的类。agent 绝不使 JVM 崩溃。这呼应 Aprism 已
应用于加载器的失败安全纪律。

---

## 4. 开放接口与能力

AprismJDK 将常用的 JVM 内部提升为**稳定的、版本化的 API 模块**
（`jdk.aprismate`）。具体：

- `jdk.aprismate.Agent` —— AprismateAgent 程序化入口。
- `jdk.aprismate.VmInfo` —— VM 构建身份、AprismJDK 版本、能力集。
- `jdk.aprismate.runtime.ThreadInsight` —— 线程栈与调度内省。
- `jdk.aprismate.runtime.HeapInsight` —— 堆区域 / GC 内省。
- `jdk.aprismate.runtime.JitInsight` —— 编译队列与方法编译内省。

这些对 JDK 是**纯增量**的（不移除、不重命名原生 API），因此针对原生
OpenJDK 编译的程序在 AprismJDK 上不变运行，反之亦然（就非 AprismJDK
子集而言）。

---

## 5. 性能优化与硬件融合

AprismJDK 将硬件感知作为一等、稳定的 API 暴露，而非让模组解析
`/proc/cpuinfo` 或调用 JNI：

- **CpuFeatures** —— 探测到的指令集特性（SSE4.2、AVX2、AVX-512、NEON、
  SVE），带能力令牌 API。
- **CacheTopology** —— 缓存行大小与缓存层级提示，使模组能填充热结构
  以避免伪共享。
- **NumaTopology** —— NUMA 节点枚举与亲和性提示（在 OS 暴露处）。
- **VectorHints** —— 建议向量化友好循环形态的薄而安全的表面（仅建议；
  绝不作为正确性依赖）。

设计原则：**建议性，永不强制。** 读取 `CpuFeatures` 的模组必须在 API
缺失（原生 JDK）或主机缺少某特性时优雅降级。

---

## 6. 跨语言过渡（Cpp2Java / Rust2Java）

AprismJDK 将外部互操作标准化在 **Foreign Function & Memory（FFM）API**
（JDK 22+ 定稿）之上，并添加生成器 + 运行时层：

- **Cpp2Java** —— 消费 C/C++ 头文件并生成 Java 侧下行调用桩 + 上行
  调用包装的绑定生成器，加上掌管原生库生命周期（加载、符号解析、
  arena 作用域内存）的运行时。
- **Rust2Java** —— 面向 Rust `extern "C"` 导出的同一管线，Rust 侧用
  `cbindgen` 式头文件生成，Java 侧用 FFM 下行调用。

两个桥共享一份公共 **ABI 映射**文档（基元宽度、结构体布局、字符串/
指针所有权、错误传播）与公共**生命周期约定**（谁分配、谁释放、arena
作用域）。这是 Aprism 未来承载原生 BE/Java 互操作、并让 AprismRefract
的原生桥（Zygisk、代理 DLL）共享单一运行时的基础。

---

## 7. 极高兼容性（跨 Java 版本）

AprismJDK 承诺一份**兼容性矩阵**：

1. **能力描述符的前向兼容。** 模组查询能力，永不假设能力。为
   AprismJDK 25 编写的模组必须在 AprismJDK 26+ 上以"能力降低但可用"
   的方式运行。
2. **原生 JDK 回退。** 每个 AprismJDK 专属 API 都有文档化的原生 JDK
   等价物或文档化的空操作。Aprism 本身必须能在不带 AprismJDK 的原生
   OpenJDK 21/25 上运行。
3. **LTS 重新基线。** AprismJDK 跟踪上游 LTS 线（25，然后下一 LTS），
   并将 AprismJDK 补丁集回溯移植到每条线上，使能力表面跨 LTS 跳跃
   保持稳定。

---

## 8. 子项目骨架与治理

`AprismLab/AprismJDK` 仓库随本 Alpha 交付：

```
AprismJDK/
  README.md            - 项目简介（已有，将扩充）
  LICENSE              - GPL-2.0（已有）
  docs/
    01-architecture.md - 本设计，EN 权威版
    02-aprismate-agent.md
    03-opened-interfaces.md
    04-perf-hardware.md
    05-cross-language.md
    06-compatibility-matrix.md
  （JDK 源码树在 OpenJDK 分支构建启动时落地）
```

治理遵循 **Aprism 主项目管理与版本控制规范**（另行记录于
`Aprism/docs/en/10-project-management-and-version-control.md`，
在 v26.4-Alpha.2 交付）：签名提交/标签、每 Alpha 一个 Pre-Release、
GA 为裸版本号、双语文档、FACT.md 会话日志。

---

## 9. 状态与明确非目标（本 Alpha）

- [范围] 仅设计 + 文档 + 骨架。尚无 OpenJDK 源码分支、无 JDK 构建、
  无 agent 实现。这些是后续里程碑。
- [非目标] 为不需要 Aprism 深层级的用户替换原生 OpenJDK。
- [非目标] 改变 Java 语言语义或 JVM 标准类文件格式。

## 10. 参考文献

- Amazon Corretto 25（LTS）与 Corretto 26（FR）—— 发行版模型与
  LTS/FR 节奏（25 GA 于 2025-09-16；Corretto FAQ，2026）。
- Azul Zulu、Microsoft Build of OpenJDK、Red Hat OpenJDK —— 构建 +
  补丁发行版模式。
- JDK 22 Foreign Function & Memory API（定稿）—— 互操作基础。
- `java.lang.instrument.Instrumentation`、JVMTI —— AprismJDK 加固的
  原生底层接缝。
