package com.qiyue.cashflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.qiyue.cashflow.ui.CashFlowApp
import com.qiyue.cashflow.ui.CashFlowViewModel

class MainActivity : ComponentActivity() {
    private val cashFlowViewModel by viewModels<CashFlowViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CashFlowApp(cashFlowViewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        cashFlowViewModel.refreshDuePlans()
    }
}
