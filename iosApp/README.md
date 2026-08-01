# iPhone 版底座

0.7 已将日期、周期、预算、转账和临时收支预测放进 `shared` Kotlin Multiplatform 模块，并准备 SwiftUI 入口源码。

当前按用户要求不下载 Xcode、不下载 iOS 模拟器，也不执行 Apple 端编译。等连接 Wi-Fi 后再完成以下工作：

1. 安装 Xcode 与一个 iPhone 模拟器运行时。
2. 建立 Xcode 工程并连接 `CashFlowShared` 静态 framework。
3. 用 SwiftUI 接入共享预测结果，再验证日期、金额和本地数据保存。

Android 版仍是 0.7 的可用主版本；此目录不影响 Android 构建，也不包含用户数据。
