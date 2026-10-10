<template>
    <div class="dashboard">
        <h3 class="page-title">
            <i class="fa-solid fa-gauge-high"></i>
            首页概览
            <span class="sub">葫芦娃助手 运行状态</span>
        </h3>

        <div class="stat-grid">
            <div class="stat-card">
                <div class="stat-label">
                    <i class="fa-solid fa-bell" style="color: var(--accent)"></i>
                    消息中心
                </div>
                <div class="stat-value">{{ overview.message.unread }}</div>
                <div class="stat-foot">未读 / 共 {{ overview.message.total }} 条</div>
            </div>

            <div class="stat-card">
                <div class="stat-label">
                    <i class="fa-solid fa-network-wired" style="color: var(--accent-2)"></i>
                    Frp 隧道
                </div>
                <div class="stat-value">{{ overview.frp.enabled }}</div>
                <div class="stat-foot">已启用 / 共 {{ overview.frp.total }} 个</div>
            </div>

            <div class="stat-card">
                <div class="stat-label">
                    <i class="fa-solid fa-paper-plane" style="color: var(--ok)"></i>
                    推送渠道
                </div>
                <div class="stat-value">{{ overview.push.enabled }}</div>
                <div class="stat-foot">已启用 / 共 {{ overview.push.total }} 个</div>
            </div>

            <div class="stat-card">
                <div class="stat-label">
                    <i class="fa-brands fa-docker" style="color: #2496ED"></i>
                    Docker 容器
                </div>
                <div class="stat-value">{{ overview.docker.running }}</div>
                <div class="stat-foot">
                    运行中 / 共 {{ overview.docker.total }} 个
                    <span v-if="!overview.docker.available" class="pill warn" style="margin-left: 8px">
                        <span class="dot"></span>不可用
                    </span>
                </div>
            </div>
        </div>

        <div class="panel glass-card">
            <div class="panel-head">
                <span><i class="fa-solid fa-server"></i> 运行环境</span>
                <n-button size="small" quaternary :loading="loading" @click="load">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    刷新
                </n-button>
            </div>
            <n-descriptions :column="2" label-placement="left" size="small" bordered>
                <n-descriptions-item label="应用名称">{{ overview.application || '-' }}</n-descriptions-item>
                <n-descriptions-item label="服务器时间">{{ overview.serverTime || '-' }}</n-descriptions-item>
                <n-descriptions-item label="系统状态">
                    <span class="pill ok"><span class="dot"></span>已初始化</span>
                </n-descriptions-item>
                <n-descriptions-item label="Docker 守护进程">
                    <span :class="['pill', overview.docker.available ? 'ok' : 'off']">
                        <span class="dot"></span>{{ overview.docker.available ? '在线' : '未连接' }}
                    </span>
                </n-descriptions-item>
            </n-descriptions>
        </div>
    </div>
</template>

<script>
import http from '@/libs/http'

export default {
    name: 'DashboardPage',
    data() {
        return {
            loading: false,
            overview: {
                application: '',
                serverTime: '',
                message: { total: 0, unread: 0 },
                frp: { total: 0, enabled: 0 },
                push: { total: 0, enabled: 0 },
                docker: { running: 0, total: 0, available: false }
            }
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        async load() {
            this.loading = true
            try {
                const data = await http.get('/dashboard/overview')
                this.overview = Object.assign({}, this.overview, data)
            } catch (error) {
                // 错误提示已由 http 拦截器统一处理
            } finally {
                this.loading = false
            }
        }
    }
}
</script>

<style scoped>
.panel {
    margin-top: 16px;
    padding: 16px 18px;
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
    color: var(--text);
    font-weight: 600;
    font-size: 14px;
}

.panel-head i {
    color: var(--accent);
    margin-right: 6px;
}
</style>