package com.huluwa.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
