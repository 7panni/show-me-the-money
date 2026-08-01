import SwiftUI
import CashFlowShared

struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("简易现金流计算")
                    .font(.title.bold())
                Text("iPhone 版底座已连接共享预测核心。")
                    .foregroundStyle(.secondary)
                Text("等连接 Wi-Fi 并安装 Xcode 后，再完成界面与模拟器验证。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .navigationTitle("现金流")
        }
    }
}
