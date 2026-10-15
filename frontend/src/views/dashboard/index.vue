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
                    <i class="fa-solid fa-microchip" style="color: var(--accent)"></i>
                    CPU 使用率
                </div>
                <div class="stat-value">{{ cpuPercentText }}</div>
                <div class="meter">
                    <span :class="meterClass(server.cpu && server.cpu.percent)" :style="meterStyle(server.cpu && server.cpu.percent)"></span>
                </div>
                <div class="stat-foot">{{ cpuFoot }}</div>
            </div>

            <div class="stat-card">
                <div class="stat-label">
                    <i class="fa-solid fa-memory" style="color: var(--accent-2)"></i>
                    内存使用
                </div>
                <div class="stat-value">{{ memoryUsedText }}</div>
                <div class="meter">
                    <span :class="meterClass(server.memory && server.memory.percent)" :style="meterStyle(server.memory && server.memory.percent)"></span>
                </div>
                <div class="stat-foot">{{ memoryFoot }}</div>
            </div>

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
            <n-descriptions :column="isMobile ? 1 : 2" label-placement="left" size="small" bordered>
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
import { NButton, NDescriptions, NDescriptionsItem } from 'naive-ui'
import http from '@/libs/http'
import { formatBytes } from '@/utils/format'
import { useIsMobile } from '@/composables/useIsMobile'

export default {
    components: { NButton, NDescriptions, NDescriptionsItem },
    name: 'DashboardPage',
    setup() {
        // 窄屏下描述列表收成单列，避免标签被压成竖排（桌面端仍为两列）
        return { isMobile: useIsMobile() }
    },
    data() {
        return {
            loading: false,
            overview: {
                application: '',
                serverTime: '',
                server: {},
                message: { total: 0, unread: 0 },
                frp: { total: 0, enabled: 0 },
                push: { total: 0, enabled: 0 },
                docker: { running: 0, total: 0, available: false }
            }
        }
    },
    computed: {
        server() {
            return this.overview.server || {}
        },
        /** 后台采样到位前显示占位符，不显示 0%——那会被误读成机器很空闲 */
        cpuPercentText() {
            const percent = this.number(this.server.cpu && this.server.cpu.percent)
            return percent === null ? '-' : percent.toFixed(1) + '%'
        },
        cpuFoot() {
            const cpu = this.server.cpu || {}
            const parts = [cpu.cores ? cpu.cores + ' 核' : '']
            if (this.number(cpu.load1) !== null) {
                parts.push('负载 ' + Number(cpu.load1).toFixed(2))
            }
            return parts.filter(Boolean).join(' · ') || '等待采样'
        },
        memoryUsedText() {
            const used = this.number(this.server.memory && this.server.memory.used)
            const total = this.number(this.server.memory && this.server.memory.total)
            return used === null || !total ? '-' : formatBytes(used) + ' / ' + formatBytes(total)
        },
        memoryFoot() {
            const memory = this.server.memory || {}
            const percent = this.number(memory.percent)
            if (percent === null) return this.server.available === false ? '暂不可用' : '等待采样'
            return '已用 ' + percent.toFixed(0) + '% · 可用 ' + formatBytes(memory.available)
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        number(value) {
            const parsed = Number(value)
            return value === null || value === undefined || !Number.isFinite(parsed) ? null : parsed
        },
        /** 占用越高越醒目：>80% 危险色、>50% 警告色，其余维持强调色 */
        meterClass(percent) {
            const value = this.number(percent)
            if (value === null) return 'meter-fill off'
            if (value > 80) return 'meter-fill danger'
            if (value > 50) return 'meter-fill warn'
            return 'meter-fill'
        },
        meterStyle(percent) {
            const value = this.number(percent)
            return { width: Math.min(100, Math.max(0, value === null ? 0 : value)) + '%' }
        },
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
/* 占用条与 .stat-value 一起构成「数值 + 直观比例」，避免只能靠百分比数字判断轻重 */
.meter {
    height: 6px;
    margin-top: 10px;
    border-radius: 999px;
    background: rgba(148, 163, 184, .14);
    overflow: hidden;
}

.meter-fill {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, var(--accent), var(--accent-2));
    transition: width .4s ease;
}

.meter-fill.warn {
    background: linear-gradient(90deg, var(--warn), #FBBF24);
}

.meter-fill.danger {
    background: linear-gradient(90deg, var(--danger), #FB7185);
}

.meter-fill.off {
    background: rgba(148, 163, 184, .45);
}

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
