package com.huluwa.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.huluwa.android.data.SkinPreferences
import com.huluwa.android.ui.AppSkin
import com.huluwa.android.ui.HuluwaColors
import com.huluwa.android.ui.HuluwaRoot
import com.huluwa.android.ui.HuluwaTheme

class MainActivity : ComponentActivity() {
    /** 与 setContent 里 viewModel() 取到的是同一个实例（同属 Activity 的 ViewModelStore）。 */
    private val viewModel: HuluwaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 当前皮肤均为浅色，透明状态栏统一使用深色图标，避免跟随系统深色模式变白。
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        val skinPreferences = SkinPreferences(this)
        HuluwaColors.selectSkin(AppSkin.fromId(skinPreferences.selectedSkin()))
        handleShare(intent)
        setContent {
            HuluwaTheme {
                HuluwaRoot(viewModel, onSkinSelected = { skin ->
                    skinPreferences.selectSkin(skin.id)
                    HuluwaColors.selectSkin(skin)
                })
            }
        }
    }

    /** singleTop 下应用已在前台时再收到分享，不会重新走 onCreate，需要在这里转交。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** 取 ACTION_SEND / ACTION_VIEW 里的分享文本交给 ViewModel；没有内容时忽略。 */
    private fun handleShare(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        viewModel.receiveShare(text)
    }
}
