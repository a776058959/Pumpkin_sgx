# 在安卓手机上跑 Pumpkin

本说明介绍如何在安卓手机上部署运行 Pumpkin（Rust 编写的 Minecraft 服务端）。

> 说明：本文中的配置项名称与默认值均依据本仓库当前代码（`pumpkin-config/src/`）核对。

---

## 1. 产物来源与适用平台

- 产物是 GitHub Actions 的 **Manual Build** 或自动构建 Release 中附带的可执行文件，附件名为
  `pumpkin-android-arm64-<日期>`。
- 该附件是针对 **`aarch64-linux-android`（bionic）动态链接**的 native 可执行文件，
  ELF interpreter 是 `/system/bin/linker64`，只能在安卓系统上运行，**在电脑上无法执行**。

---

## 2. 手机端准备

- 安装 **F-Droid 版 Termux**（不要装 Play 商店版——Play 版已被官方停止维护）。
- 可选：安装 **Termux:Boot**（开机自启服务）和任意文件管理器。
- 硬件要求：
  - 手机必须是 **64 位 ARM**（arm64，即 aarch64）；
  - Android **7.0 及以上**；
  - 建议**可用内存 ≥ 4GB**（内存越小，越需要调低视距与模拟距离，见第 5 节）。

---

## 3. 放置与运行

### 3.1 放置位置（关键）

- 二进制必须放在 **Termux 私有目录**：`~/` 即
  `/data/data/com.termux/files/home`。
- **绝不能放在 `/sdcard` 或 `~/storage` 下**——这些是 noexec 挂载，没有执行权限，
  会直接报「无法执行」。

```bash
# 假设已把下载的附件复制到 ~/downloads，然后移动到主目录
cp ~/downloads/pumpkin-android-arm64-20240101 ~/pumpkin
chmod +x ~/pumpkin
```

### 3.2 运行

```bash
./pumpkin
```

- 首次运行会在**当前工作目录**自动生成 `config/`、`world/`、`logs/` 等目录。
- 建议先在 `~/` 下建一个专用目录（如 `~/pumpkin-server`）再运行，便于管理。

### 3.3 保活（重要）

- **先执行 `termux-wake-lock`**，否则息屏后 CPU 会进入挂起状态，服务会被暂停。
- 在**系统设置**里给 Termux（以及 Termux:Boot）**关闭电池优化**（电池 → 后台运行 → 不受限制）。
- 尽量让服务跑在 **Termux 前台会话**里，**不要 daemon 化**（后台守护进程更容易被系统杀进程）。

---

## 4. 为什么不用静态 musl 版本

- 静态 **musl** 二进制在安卓上**读不到 `/etc/resolv.conf`**，导致 **DNS 解析失败**；
- 且可能被安卓的 **seccomp** 拦截而无法运行。
- 因此要选 **bionic**（动态链接）版本，即本项目的 `pumpkin-android-arm64-*` 附件。

---

## 5. 配置调优建议

配置文件位于 `config/` 目录：

- `configuration.toml` —— 基础配置（视距、模拟距离、人数、online mode 等）；
- `features.toml` —— 高级/特性配置（世界 autosave、networking 的 LAN 广播与 query 等）。

以下是针对手机硬件（内存、算力有限）的推荐值。配置项名称、所在文件与默认值均取自代码：

| 配置项 | 所在文件 | 默认值 | 手机建议 | 原因 |
|---|---|---|---|---|
| `view_distance`（视距） | `configuration.toml` | `16` | `6` ~ `8` | 大幅降低区块生成与同步开销，省内存省电 |
| `simulation_distance`（模拟距离） | `configuration.toml` | `10` | `4` ~ `6` | 减少实体/区块模拟量，压 CPU 与内存 |
| `max_players`（最大人数） | `configuration.toml` | `1000` | `5` ~ `10` | 手机不适合承载高并发，避免内存被吃满 |
| `online_mode`（联机认证） | `configuration.toml` | `true` | 视情况 `false` | true 时需联网验证 Mojang sessionserver；网络/DNS 有问题时设 false（offline 模式） |
| `[networking.lan_broadcast] enabled` | `features.toml` | `false`（默认已关） | `false` | 保持关闭，少一个定时多播任务 |
| `[networking.query] enabled` | `features.toml` | `true` | `false` | 关闭 query 服务，省一个监听端口与少量开销 |
| `[world] autosave_ticks`（自动保存间隔） | `features.toml` | `6000`（20TPS 下约 5 分钟） | 适当调大（如 `12000`） | 减少写入 world/ 次数，降低储存损耗与停顿 |

> 注意：`view_distance` 合法范围为 2–64，且当 `online_mode=true` 时 `encryption` 必须为
> `true`（代码中的校验规则）。若把 `online_mode` 设为 `false`，`encryption` 也可一并设为 `false`。

---

## 6. 连接方式

- 同一**局域网**内，Java 版客户端直接连接 `手机IP:25565`（`java_edition_address` 默认
  `0.0.0.0:25565`）。
- 在 Termux 里用 `ip addr`（或 `ip a`）查看手机局域网 IP。
- 安卓上 **LAN 自动发现**（多播 `224.0.2.60`）可能因缺少 **MulticastLock** 而失效，
  **属正常现象**，手动输入 `手机IP:25565` 即可。

---

## 7. 常见问题排查

| 现象 | 原因 / 解决 |
|---|---|
| `Permission denied` | 没执行 `chmod +x ~/pumpkin` |
| `cannot execute: required file not found` | 文件放在了 `/sdcard`/`~/storage`（noexec），或架构不对（非 arm64）；移到 Termux 私有目录 |
| 服务进程被杀 | 电池优化开启 / phantom process killer 生效；关闭 Termux（及 Termux:Boot）电池优化、参考关闭 phantom process killer、用 Termux:Boot 开机自动拉起 |
| 内存不足被 LMK 杀掉 | 内存被挤满；调小 `view_distance` 与 `simulation_distance` |
| 玩家登录认证失败 | `online_mode=true` 时手机须能正常访问 Mojang 的 sessionserver；网络/DNS 有问题就改用 offline（`online_mode=false`） |

---

## 8. 已知限制

- **原生插件**（`plugins/*.so`）必须是为 **`aarch64-linux-android`** 编译的 `.so`，
  桌面版的 `.so` **无法加载**。
- 手机长时间满载会**发热并降频**，服务吞吐会明显下降，建议控制视距/模拟距离与人数。

---

## 9. 本机构建（可选）

如果你想自己构建安卓产物：

- CI 工作流 `.github/workflows/manual-build.yml` 和 `.github/workflows/sgxbuild.yml`
  中均已加入 **`build-android`** job。
- 该 job 使用 **Android NDK（API 24）** 对 `aarch64-linux-android` 目标做交叉编译
  （C 依赖如 ring/lz4 由 NDK clang 编译），产物为动态链接的 bionic 可执行文件。

```bash
# 本地大致思路（完整流程以工作流脚本为准）
rustup target add aarch64-linux-android
# 通过 NDK 的 aarch64-linux-android24-clang 设置目标 linker 后：
cargo build --release --target aarch64-linux-android
```
