<template>
    <n-config-provider :theme="theme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN"
        class="app-provider">
        <n-message-provider>
            <n-dialog-provider>
                <n-notification-provider>
                    <n-loading-bar-provider>
                        <div id="app-root" class="grid-bg" :class="{ 'is-fullscreen-page': fullscreen }">
                            <router-view />
                        </div>
                    </n-loading-bar-provider>
                </n-notification-provider>
            </n-dialog-provider>
        </n-message-provider>
    </n-config-provider>
</template>

<script setup>
import { zhCN, dateZhCN } from 'naive-ui'
import { theme, themeOverrides } from '@/theme/naive'
import { useFullscreenPage } from '@/composables/useFullscreenPage'
import './libs/discrete'

// 全屏沉浸页面（手机端会话页）：去掉背景网格与四周内边距，让界面真正铺满整屏
const fullscreen = useFullscreenPage()
</script>

<style>
html,
body,
#app,
#app-root {
    width: 100%;
    height: 100%;
    margin: 0;
    padding: 0;
}

html,
body {
    overflow: hidden;
    background: var(--bg);
    color: var(--text);
    font-family: "Segoe UI", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, sans-serif;
}

#app-root {
    position: relative;
    overflow: hidden;
}

#app-root > * {
    position: relative;
    z-index: 1;
}

/* 全屏沉浸页面：背景网格会让聊天列表显得花，整屏页面直接铺纯色背景 */
#app-root.is-fullscreen-page.grid-bg::before {
    display: none;
}

.app-provider,
.n-config-provider {
    height: 100%;
}
</style>