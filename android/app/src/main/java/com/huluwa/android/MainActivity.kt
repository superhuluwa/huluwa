package com.huluwa.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huluwa.android.data.SkinPreferences
import com.huluwa.android.ui.AppSkin
import com.huluwa.android.ui.HuluwaColors
import com.huluwa.android.ui.HuluwaRoot
import com.huluwa.android.ui.HuluwaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 当前皮肤均为浅色，透明状态栏统一使用深色图标，避免跟随系统深色模式变白。
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        val skinPreferences = SkinPreferences(this)
        HuluwaColors.selectSkin(AppSkin.fromId(skinPreferences.selectedSkin()))
        setContent {
            HuluwaTheme {
                HuluwaRoot(viewModel(), onSkinSelected = { skin ->
                    skinPreferences.selectSkin(skin.id)
                    HuluwaColors.selectSkin(skin)
                })
            }
        }
    }
}
