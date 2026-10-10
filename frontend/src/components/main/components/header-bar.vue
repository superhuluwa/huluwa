<template>
    <div class="header-bar">
        <button class="icon-btn" type="button" @click="$emit('on-coll-change', !collapsed)"
            :title="collapsed ? '展开侧栏' : '收起侧栏'">
            <i :class="collapsed ? 'fa-solid fa-indent' : 'fa-solid fa-outdent'"></i>
        </button>

        <n-breadcrumb class="crumb">
            <n-breadcrumb-item>
                <i class="fa-solid fa-house crumb-home"></i>
                <span>{{ currentTitle }}</span>
            </n-breadcrumb-item>
        </n-breadcrumb>

        <div class="custom-content-con">
            <slot></slot>
        </div>
    </div>
</template>

<script>
export default {
    name: 'HeaderBar',
    props: {
        collapsed: Boolean
    },
    emits: ['on-coll-change'],
    computed: {
        currentTitle() {
            return (this.$route.meta && this.$route.meta.title) || '首页'
        }
    }
}
</script>

<style scoped>
.header-bar {
    height: 56px;
    flex: none;
    background: rgba(13, 21, 38, .7);
    border-bottom: 1px solid var(--line);
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 0 18px;
}

.icon-btn {
    background: transparent;
    border: none;
    color: var(--text-2);
    cursor: pointer;
    font-size: 15px;
    padding: 6px 8px;
    border-radius: 8px;
    display: flex;
    align-items: center;
}

.icon-btn:hover {
    background: rgba(148, 163, 184, .08);
    color: var(--text);
}

.crumb {
    white-space: nowrap;
}

.crumb-home {
    margin-right: 6px;
}

.custom-content-con {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 16px;
}
</style>