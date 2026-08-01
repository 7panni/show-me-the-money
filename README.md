# 简易现金流计算-测试版

一个只在本机保存数据的 Android 现金流预测工具。维护账户真实余额、周期款项、预算和未来临时收支，即可查看未来余额变化。

## 功能

- 多账户余额：人民币、美元与数字货币分开显示
- 固定收入、支出、转账与分期付款
- 每周、每月、每季度、每年计划
- 周期预算和未来临时收支
- 30、90、365 天余额预测与可查看数值的走势图
- JSON 完整备份与恢复
- 首次启动可选极简、定制或完整模式；切换模式不会删除数据
- 账户余额支持直接输入或用加减算式快速校准

## 0.7 架构

- `app`：Android 界面与 Room 本地数据库
- `shared`：Android/iPhone 共用的整数金额、日期和现金流预测核心
- `iosApp`：iPhone SwiftUI 源码底座；按当前开发安排，尚未下载 Xcode 或执行 Apple 端测试

## 隐私

所有数据仅保存在设备本地。首次启动的可选示例均明确标注为虚构示例。本仓库不包含任何真实账户余额、备份、APK 或测试数据库。

## 构建

需要 JDK 17 和 Android SDK。仓库自带固定为 Gradle 8.7 的 Wrapper：

```bash
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
```

`release` 会启用代码压缩和无用资源移除。0.7 测试发布包暂用调试签名，可直接侧载；正式商店版需另建发布密钥。

Apple 目标默认不初始化，以免 Android 构建下载 Apple 相关工具；有 Xcode 后使用 `-PenableIos=true` 开启。

## 许可证

本项目采用 [MIT License](LICENSE)。
