<template>
    <n-config-provider :theme="theme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN"
        class="app-provider">
        <n-message-provider>
            <n-dialog-provider>
                <n-notification-provider>
                    <n-loading-bar-provider>
                        <div id="app-root" class="grid-bg">
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
import './libs/discrete'
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

.app-provider,
.n-config-provider {
    height: 100%;
}
</style>