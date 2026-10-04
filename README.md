<p align="center">
  <img src="app/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="CycleCal icon"/>
</p>

# 周期日程 CycleCal

> **English**: CycleCal is a lightweight Android app (pure Java, ~50 KB APK, no third-party dependencies) that batch-creates recurring calendar events — e.g. *every 30 days for the next 6 months* — straight into the system calendar, with a preview of every date, an undo option, and a Material-style adaptive UI. See the Chinese guide below; build with `tools/build.ps1`.

一款安卓小工具：设置好周期后，**一次批量生成几个月的日历日程**，例如「每 30 天一次、持续 6 个月」，一键写入系统日历。

## 功能

- **重复间隔**：每 N 天（默认 30 天，1–365 可调），支持步进按钮快速加减
- **持续时长**：持续 M 个月（1–36），实时预览将要创建的日程数量、首个和末个日期
- **两种创建方式**：
  - 一条循环日程（推荐）：日历中只占一条（RRULE 规则），删除/修改一次即可全部生效
  - 多条独立日程：每次单独一条，可单独修改或删除某一次
- **提前 1 天提醒**：每次日程开始前 24 小时提醒（可开关）
- **全天日程**：可选按全天事件写入
- **写入日历可选**：自动读取手机上的日历账户（如手机账户、Google 日历等），可自由选择
- **一键撤销**：创建完成后可直接撤销本次批量创建
- **深色模式自适应**：跟随系统自动切换

界面为卡片式设计，带入场动画、涟漪反馈，原生系统日期/时间选择器，操作顺滑。

## 安装要求

- Android 7.0（API 24）及以上
- 手机上需已登录/添加过日历账户（绝大多数手机自带）

## 安装步骤

1. 将 `CycleCal.apk` 传到手机（微信/QQ/数据线/网盘均可）
2. 在手机上点击 APK 文件安装；如提示「未知来源」，允许来自该来源的应用安装
3. 打开「周期日程」，首次使用会请求**日历权限**，请选择允许
4. 开始使用

## 使用方法

1. 填写日程名称（如「给家里打电话」）
2. 选择开始日期和时间（或勾选全天日程）
3. 设置重复间隔（默认 30 天）
4. 设置持续时长（默认 6 个月），下方实时显示「将创建 N 个日程」及日期范围
5. 选择写入哪个日历账户
6. 点击底部「创建 N 个日程」按钮，弹出结果确认；如创建错了可点「撤销」

## 技术信息

- 纯 Java + 原生 Android API（无第三方依赖），APK 仅约 50 KB
- 日程通过系统 CalendarProvider 写入，可在系统日历、Google 日历等任意日历应用中查看、修改、删除
- minSdk 24 / targetSdk 34，v2+v3 签名

## 构建方式（如需自行修改）

```powershell
powershell -ExecutionPolicy Bypass -File tools\build.ps1
```

依赖：`_sdk` 目录下的 JDK 17、android-34 平台、build-tools 34.0.0（首次构建脚本会自动生成签名密钥 `build\release.keystore`，密钥口令 `cyclecal123`）。
