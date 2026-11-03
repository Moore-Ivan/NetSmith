# 网匠 (NetSmith)

> 基于 Java FlatLaf 开发的桌面级网络计算工具，同时支持 IPv4 与 IPv6 双栈环境，面向网络工程师、系统管理员及运维人员设计，提供从基础地址解析到复杂网络规划的一站式计算能力。

## ✨ 功能特性

应用采用多标签页 (Tabbed) 布局，按功能维度划分为 IPv4 与 IPv6 两大体系，覆盖基础解析、子网划分与高级工具三大场景。

### IPv4 功能

**基础工具**

| 功能           | 说明                                      |
| ------------ | --------------------------------------- |
| 网段详情         | 输入 CIDR，输出网络地址、广播地址、掩码、反掩码、可用主机范围、主机数量等 |
| IP 地址解析      | 解析单个 IP 地址所属网段、地址类别（A/B/C）等信息           |
| IP 范围 → CIDR | 给定起止 IP，自动合并为最优 CIDR 列表                 |

**子网划分**

| 功能          | 说明                           |
| ----------- | ---------------------------- |
| 等长子网划分      | 按子网数量或每子网主机数，等长划分主网段         |
| VLSM 变长子网划分 | 按各部门主机需求列表进行可变长子网划分，最大化地址利用率 |
| 超网拆分        | 将大网段拆分为指定前缀的子网，支持分页加载海量结果    |

**高级工具**

| 功能     | 说明                   |
| ------ | -------------------- |
| 路由汇总   | 多个离散网段自动汇总为最精简路由     |
| 冲突检测   | 检测多个网段之间是否存在地址重叠     |
| 网络规划向导 | 按部门数量与每部门主机数自动规划子网方案 |
| 随机 IP 地址池 | 生成指定范围内的随机 IP 地址，支持排除特定地址 |
| ACL 策略检查 | 验证 ACL 规则是否覆盖指定网段，检测冲突或遗漏 |
| NAT 配置辅助 | 根据内部网段自动计算 NAT 转换规则和端口映射 |

### IPv6 功能

**基础工具**

| 功能          | 说明                           |
| ----------- | ---------------------------- |
| 网段详情        | 输入 IPv6 CIDR，输出网络地址、范围、地址数量等 |
| 地址解析        | 解析 IPv6 地址，支持压缩、全展开等多种表示形式   |
| 地址范围 → CIDR | 给定起止 IPv6，自动转换为 CIDR 列表      |

**子网划分**

| 功能        | 说明                             |
| --------- | ------------------------------ |
| 子网划分      | 按前缀或数量划分 IPv6 子网，支持大数据量分页与流式加载 |
| VLSM 变长子网 | IPv6 环境下的可变长子网划分               |

**高级工具**

| 功能           | 说明                     |
| ------------ | ---------------------- |
| 路由汇总         | IPv6 多网段汇总             |
| EUI-64 地址生成  | 基于 MAC 地址生成 IPv6 接口标识符 |
| 多播地址解析       | 解析 IPv6 多播地址的作用域与组信息   |
| DHCPv6 前缀委派  | 计算 DHCPv6 PD 委派前缀      |
| IPv4 映射 IPv6 | IPv4 与 IPv6 映射地址互转     |

### 通用辅助工具

- 🔄 **进制转换器** —— 二/八/十/十六进制互转
- 🎭 **掩码转换器** —— CIDR 前缀 ↔ 点分十进制掩码 ↔ 反掩码互转
- 📋 **输入历史记录** —— 自动保存近期输入，可一键清除
- 📊 **内存监控** —— 实时显示 JVM 内存占用
- 🌗 **三模式主题** —— 基于 FlatLaf，支持跟随系统 / 亮色 / 暗色三种模式（F2 循环切换）；跟随系统时自动同步 Windows / macOS / Linux 的系统深浅色并即时切换
- 🖥️ **全屏模式** —— 支持一键切换全屏（F11 独占全屏，不修改窗口装饰）
- ⚡ **启动加速** —— 标签页懒加载，仅构建首屏可见面板，其余面板首次切换时按需创建
- 🚦 **防重复操作** —— 计算期间自动禁用触发按钮；快速计算不弹窗，长任务延迟 300ms 才显示进度对话框，支持 Esc 取消
- 🔄 **在线更新** —— 自动检查 GitHub Release 新版本，支持实时下载速率/剩余时间显示、后台下载不阻塞主窗口、一键取消

## ⌨️ 快捷键

| 快捷键        | 功能          |
| ---------- | ----------- |
| `F1`       | 打开快捷键说明     |
| `F2`       | 循环切换主题：跟随系统 → 亮色 → 暗色 |
| `F3`       | 打开进制转换器     |
| `F4`       | 打开掩码转换器     |
| `F9`       | 检查更新        |
| `F10`      | 联系作者        |
| `F11`      | 切换全屏模式      |
| `Ctrl + L` | 清空结果区域      |
| `Ctrl + C` | 复制结果到剪贴板    |
| `Ctrl + Shift + L` | 一键清空全部历史与结果（含输入历史，结果区切回文本卡） |
| `Ctrl + PageUp / PageDown` | 在六大功能分组间快速切换 |
| `Enter`    | 在输入框中执行计算   |
| `Esc`      | 取消正在执行的计算   |

## 🛠️ 技术栈

- **语言**：Java 21
- **GUI 框架**：Java Swing + [FlatLaf 3.5](https://www.formdev.com/flatlaf/)（现代主题）
- **构建工具**：Gradle (with [Shadow Plugin](https://github.com/GradleUp/shadow))
- **序列化**：Gson 2.10.1（配置持久化）
- **测试框架**：JUnit 4
- **打包工具**：Jpackage（生成原生安装包）

## 📦 项目结构

```
NetSmith/
├── build.gradle                 # 构建脚本：依赖、版本号、Shadow Jar、jpackage 打包
├── src/main/java/com/NetSmith/
│   ├── SubnetGUI.java           # 主窗口：标签页懒加载、主题管理、全局快捷键、状态栏
│   ├── SubnetController.java    # 控制器：统一调度 Service 与 UI 回调
│   ├── SubnetCalculator.java    # IPv4 核心算法（纯静态，无 UI 依赖）
│   ├── IPv6Calculator.java      # IPv6 核心算法（纯静态，无 UI 依赖）
│   ├── service/                 # Service 层：输入校验 + 结果转换
│   │   ├── IPv4SubnetService.java
│   │   └── IPv6SubnetService.java
│   ├── *Panel.java              # 19 个业务面板：IPv4/IPv6 各功能的输入与展示
│   ├── CalculationWorker.java   # 异步计算封装（SwingWorker）：延迟进度窗、防重复、Esc 取消
│   ├── ConfigStore.java         # 配置持久化（Gson JSON）：主题模式、输入历史、列宽记忆
│   ├── UpdateChecker.java       # GitHub Releases 在线更新
│   ├── HistoryComboBox.java     # 带历史记录下拉的输入框
│   ├── ResultTablePanel.java    # 文本结果表格（双击复制 / 右键导出 CSV）
│   ├── SubnetTablePanel.java    # 子网列表表格（排序 / 分页 / 大数据量流式加载）
│   ├── ErrorIndicator.java      # 输入错误指示器（红框 + 悬浮提示）
│   └── ...                      # 校验器、进制/掩码转换器、快捷键说明等辅助组件
└── src/test/java/               # JUnit 测试（核心算法）
```

## 🚀 快速开始

### 环境要求

- JDK 21 或以上
- （可选）Gradle 8.x，或直接使用项目自带的 Gradle Wrapper

### 运行

```bash
# 方式一：使用 Gradle application 插件直接运行
gradle run

# 方式二：构建后运行 jar
gradle jar
java -jar build/libs/NetSmith-x.x.x.jar
```

### 构建

```bash
# 1. 构建 Thin Jar（需自行提供依赖）
gradle jar

# 2. 构建 Fat Jar（包含所有依赖，可直接运行）
gradle shadowJar
# 产物：build/libs/NetSmith-x.x.x-all.jar

# 3. 生成本地安装包（Windows 生成 .exe，macOS 生成 .dmg）
gradle myJpackage
# 产物：build/jpackage/
```

> 💡 **关于 jpackage**：打包任务依赖于 `installDist`，会自动准备 `lib` 目录。若项目根目录下存在 `custom-jre/` 目录，将作为自定义 JRE 运行时镜像打包，从而显著减小安装包体积。

## 🔄 在线更新

应用内置基于 GitHub Releases 的自动更新机制，安装后无需手动重新下载即可升级到新版本。

**工作流程**：

1. **启动自动检查** —— 程序启动时自动查询 GitHub Releases 最新版本，每 24 小时最多检查一次（时间戳仅在检查**成功**后更新，网络失败不消耗重试机会）。仅在发现新版本时弹窗，无新版本不打扰用户。
2. **手动检查** —— 点击底部「检查更新」按钮，或按 `F9`。
3. **版本比较** —— 采用语义化版本比较（`1.10` > `1.9`），与本地 `VersionInfo` 读取的版本号对比。
4. **下载安装** —— 确认更新后，下载新版 EXE 安装包。下载对话框为非模态（不阻塞主窗口），支持：
   - 实时下载速率显示（B/s → KB/s → MB/s 自动切换，指数平滑防跳变）
   - 剩余时间估算（基于平滑速率与剩余字节数）
   - **「后台下载」** 按钮：隐藏下载窗口，进度推送到主窗口状态栏，可继续使用程序所有功能
   - **「取消下载」** 按钮：中断下载并清理临时文件
   - 下载完成后以 `/passive` 模式启动安装程序并退出当前进程，由安装程序静默覆盖安装
5. **发布页跳转** —— 也可选择「查看发布页」在浏览器中打开 Release 说明。

> ⚠️ 安装目录位于 `Program Files` 等 system 目录时，更新会触发 UAC 提权，属正常现象。

**配置项**（`config/settings.json`）：

| 键                       | 默认值    | 说明                |
| ----------------------- | ------ | ----------------- |
| `update.autoCheck`      | `true` | 是否在启动时自动检查更新      |
| `update.lastCheckEpoch` | `0`    | 上次检查的 Unix 时间戳（秒） |

**仓库地址**：更新检查器硬编码指向 `Moore-Ivan/NetSmith`，如需 fork 后自用，修改 [UpdateChecker.java](src/main/java/com/NetSmith/UpdateChecker.java) 顶部的 `REPO_OWNER` / `REPO_NAME` 常量。

## 🏗️ 架构概览

应用遵循经典的分层架构：

```
┌─────────────────────────────────────────┐
│              View (Swing Panel)         │  各 *Panel.java 负责输入与展示
├─────────────────────────────────────────┤
│         Controller (SubnetController)   │  统一调度，回调驱动 UI 更新
├─────────────────────────────────────────┤
│  Service (IPv4/IPv6SubnetService)       │  业务编排 + 输入校验
├─────────────────────────────────────────┤
│  Core (SubnetCalculator / IPv6Calculator)│ 纯算法层，无 UI 依赖
└─────────────────────────────────────────┘
```

- **核心计算层**（`SubnetCalculator` / `IPv6Calculator`）为纯静态方法，不依赖 Swing，可独立复用或单元测试。
- **Service 层**封装校验与结果转换，IPv6 大数据量场景统一使用分页 / 流式 / 迭代器接口，避免 OOM。
- **异步计算**通过 `CalculationWorker`（基于 `SwingWorker`）执行，保证 UI 响应性，并支持任务取消。

## 🧪 测试

```bash
gradle test
```

现有测试覆盖 VLSM 子网划分等核心算法，位于 [VlsmTest.java](src/test/java/com/NetSmith/VlsmTest.java)。

## 工作流说明

| 工作流                                          | 触发条件             | 作用                        |
| -------------------------------------------- | ---------------- | ------------------------- |
| [build.yml](.github/workflows/build.yml)     | push / PR 到 main | 编译 + 测试，保证主干可构建           |
| [release.yml](.github/workflows/release.yml) | 推送 `v*` 标签 / 手动  | 构建 EXE 并发布 GitHub Release |

## 📄 许可

Copyright © Ivan. All rights reserved.

## 👤 联系作者

应用内点击「联系作者」按钮，或按 `F10`。

## 许可证

本项目采用双许可模式：

- **非商业使用**：个人学习、研究、非商业项目可免费使用，但**必须保留原作者署名**，禁止任何商业用途。
- **商业使用**：如需商用，请通过 \[<moore_ivan@foxmail.com>] 联系获取商业授权。