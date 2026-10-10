<template>
    <div class="main-console">
        <aside class="sider" :class="{ collapsed }">
            <div class="logo">
                <img class="logo-mark" src="/huluwa-logo.svg" alt="葫芦娃 Logo" width="36" height="36" />
                <div v-show="!collapsed" class="logo-text">
                    <div class="logo-name">Huluwa Console</div>
                    <div class="logo-sub">OPS CONSOLE</div>
                </div>
            </div>
            <n-menu :value="activeMenu" :options="menuOptions" :collapsed="collapsed" :collapsed-width="64"
                :collapsed-icon-size="17" :indent="18" @update:value="turnToPage" />
            <div class="sider-foot" v-show="!collapsed">
                <span>版本</span>
                <span class="mono">v1.0.0</span>
            </div>
        </aside>

        <div class="main-body">
            <header-bar :collapsed="collapsed" @on-coll-change="handleCollapsedChange">
                <user-chip />
            </header-bar>

            <div class="content-wrapper">
                <router-view v-slot="{ Component }">
                    <keep-alive>
                        <component :is="Component" :key="$route.path" />
                    </keep-alive>
                </router-view>
            </div>
        </div>
    </div>
</template>

<script>
import { h } from 'vue'
import { RouterLink } from 'vue-router'
import { useAppStore } from '@/stores/app'
import HeaderBar from './components/header-bar.vue'
import UserChip from './components/user-chip.vue'

const renderIcon = (iconClass) => {
    if (!iconClass) return undefined
    return () => h('i', { class: iconClass })
}

export default {
    name: 'MainLayout',
    components: { HeaderBar, UserChip },
    setup() {
        return { appStore: useAppStore() }
    },
    computed: {
        collapsed() {
            return this.appStore.collapsed
        },
        activeMenu() {
            return this.$route.name
        },
        menuOptions() {
            const children = this.$router.options.routes
                .find((route) => route.path === '/')
                .children.filter((item) => !(item.meta && item.meta.hideInMenu))
            return children.map((item) => ({
                key: item.name,
                label: () => h(RouterLink, { to: { name: item.name } }, { default: () => item.meta.title }),
                icon: renderIcon(item.meta && item.meta.icon)
            }))
        }
    },
    methods: {
        turnToPage(name) {
            if (name !== this.$route.name) {
                this.$router.push({ name }).catch(() => {})
            }
        },
        handleCollapsedChange(value) {
            this.appStore.collapsed = value
        }
    }
}
</script>

<style scoped>
.main-console {
    display: flex;
    height: 100%;
    background: var(--bg);
}

.sider {
    width: 236px;
    flex: none;
    background: linear-gradient(180deg, #0d1526 0%, #0b1322 100%);
    border-right: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    padding: 14px 10px 0;
    transition: width .25s cubic-bezier(.4, 0, .2, 1);
    overflow: hidden;
}

.sider.collapsed {
    width: 64px;
}

.logo {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px 18px;
    flex: none;
    justify-content: center;
}

.sider:not(.collapsed) .logo {
    justify-content: flex-start;
}

.logo-mark {
    width: 36px;
    height: 36px;
    flex: none;
    border-radius: 9px;
    display: block;
    object-fit: contain;
    box-shadow: 0 4px 14px rgba(0, 0, 0, .45);
}

.logo-name {
    font-weight: 700;
    font-size: 16px;
    color: var(--text);
    white-space: nowrap;
}

.logo-sub {
    font-size: 11px;
    color: var(--text-3);
    letter-spacing: .5px;
    white-space: nowrap;
}

.sider :deep(.n-menu) {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
}

.sider-foot {
    border-top: 1px solid var(--line);
    padding: 10px 12px;
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--text-3);
    font-size: 12px;
    flex: none;
}

.main-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
}

.content-wrapper {
    flex: 1;
    padding: 18px;
    overflow: auto;
    position: relative;
}
</style>