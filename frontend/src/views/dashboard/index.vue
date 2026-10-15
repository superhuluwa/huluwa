<template>
    <div class="dashboard">
        <h3 class="page-title">
            <i class="fa-solid fa-gauge-high"></i>
            首页概览
            <span class="sub">葫芦娃助手 运行状态</span>
        </h3>

        <div class="stat-grid">
            <div class="stat-card resource-card">
                <div class="resource-metric">
                    <div class="resource-gauge" role="img" :aria-label="'CPU 使用率 ' + cpuPercentText">
                        <svg class="resource-ring" viewBox="0 0 144 144" aria-hidden="true">
                            <circle class="resource-ring-track" cx="72" cy="72" r="60" />
                            <circle
                                v-if="number(server.cpu && server.cpu.percent) > 0"
                                class="resource-ring-progress"
                                cx="72" cy="72" r="60" pathLength="100"
                                :stroke-dashoffset="ringOffset(server.cpu && server.cpu.percent)"
                            />
                        </svg>
                        <div class="resource-reading">
                            <span class="resource-value">{{ cpuPercentText }}</span>
                        </div>
                    </div>
                    <div class="resource-label">CPU</div>
                    <div class="resource-foot">{{ cpuFoot }}</div>
                </div>

                <div class="resource-metric">
                    <div class="resource-gauge" role="img" :aria-label="'内存使用 ' + memoryUsedText + '，' + memoryFoot">
                        <svg class="resource-ring" viewBox="0 0 144 144" aria-hidden="true">
                            <circle class="resource-ring-track" cx="72" cy="72" r="60" />
                            <circle
                                v-if="number(server.memory && server.memory.percent) > 0"
                                class="resource-ring-progress"
                                cx="72" cy="72" r="60" pathLength="100"
                                :stroke-dashoffset="ringOffset(server.memory && server.memory.percent)"
                            />
                        </svg>
                        <div class="resource-reading">
                            <span class="resource-value">{{ memoryReading.value }}</span>
                            <span v-if="memoryReading.unit" class="resource-unit">{{ memoryReading.unit }}</span>
                        </div>
                    </div>
                    <div class="resource-label">内存</div>
                    <div class="resource-foot">{{ memoryUsedText }}</div>
                    <div class="resource-foot">{{ memoryFoot }}</div>
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

const METRICS_REFRESH_INTERVAL = 2000

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
            metricsActive: false,
            metricsTimer: null,
            metricsRequest: null,
            serverSnapshot: null,
            overview: {
                application: '',
                serverTime: '',
                server: {},
                docker: { available: false }
            }
        }
    },
    computed: {
        server() {
            return this.serverSnapshot || this.overview.server || {}
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
        memoryReading() {
            const used = this.number(this.server.memory && this.server.memory.used)
            const total = this.number(this.server.memory && this.server.memory.total)
            if (used === null || used < 0 || total === null || total <= 0) {
                return { value: '-', unit: '' }
            }
            const [value, unit] = formatBytes(used).split(' ')
            return { value: Number(value).toFixed(1), unit }
        },
        memoryFoot() {
            const memory = this.server.memory || {}
            const percent = this.number(memory.percent)
            if (percent === null) return this.server.available === false ? '暂不可用' : '等待采样'
            return '已用 ' + percent.toFixed(0) + '% · 可用 ' + formatBytes(memory.available)
        }
    },
    // 首页由 KeepAlive 缓存，每次进入时恢复刷新，离开时释放轮询与请求。
    activated() {
        this.metricsActive = true
        document.addEventListener('visibilitychange', this.handleVisibilityChange)
        this.load()
    },
    deactivated() {
        this.stopMetricsPolling()
    },
    beforeUnmount() {
        this.stopMetricsPolling()
    },
    methods: {
        number(value) {
            const parsed = Number(value)
            return value === null || value === undefined || !Number.isFinite(parsed) ? null : parsed
        },
        /** SVG 圆环以 100 为完整周长，缺失数据和越界数值均收敛到有效范围。 */
        ringOffset(percent) {
            const value = this.number(percent)
            return 100 - Math.min(100, Math.max(0, value === null ? 0 : value))
        },
        pauseMetricsPolling() {
            clearTimeout(this.metricsTimer)
            this.metricsTimer = null
            if (this.metricsRequest) {
                this.metricsRequest.abort()
                this.metricsRequest = null
            }
        },
        stopMetricsPolling() {
            this.metricsActive = false
            document.removeEventListener('visibilitychange', this.handleVisibilityChange)
            this.pauseMetricsPolling()
        },
        handleVisibilityChange() {
            if (document.hidden) {
                this.pauseMetricsPolling()
            } else {
                this.refreshMetrics()
            }
        },
        async refreshMetrics() {
            if (!this.metricsActive || document.hidden || this.metricsRequest) return
            clearTimeout(this.metricsTimer)
            this.metricsTimer = null
            const request = new AbortController()
            this.metricsRequest = request
            try {
                const data = await http.get('/dashboard/server', undefined, {
                    silent: true,
                    signal: request.signal
                })
                if (!request.signal.aborted && this.metricsActive && !document.hidden) {
                    this.serverSnapshot = data
                }
            } catch (error) {
                // 自动刷新失败保留上次快照，下一轮重试，避免反复弹出错误提示。
            } finally {
                if (this.metricsRequest === request) {
                    this.metricsRequest = null
                    if (this.metricsActive && !document.hidden) {
                        // 请求结束后再安排下一轮，防止慢请求重叠或乱序覆盖。
                        this.metricsTimer = setTimeout(() => {
                            this.metricsTimer = null
                            this.refreshMetrics()
                        }, METRICS_REFRESH_INTERVAL)
                    }
                }
            }
        },
        async load() {
            this.refreshMetrics()
            if (this.loading) return
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
.dashboard {
    container-type: inline-size;
}

.stat-grid {
    grid-template-columns: minmax(0, 1fr);
}

.resource-card {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px;
    padding: 28px 24px 24px;
    border-radius: 24px;
}

.resource-metric {
    min-width: 0;
    text-align: center;
}

.resource-gauge {
    position: relative;
    width: min(100%, 156px);
    aspect-ratio: 1;
    margin: 0 auto;
}

.resource-ring {
    display: block;
    width: 100%;
    height: 100%;
    fill: none;
    stroke-width: 14;
    transform: rotate(-90deg);
}

.resource-ring-track {
    stroke: rgba(148, 163, 184, .18);
}

.resource-ring-progress {
    stroke: #086BFF;
    stroke-linecap: round;
    stroke-dasharray: 100;
    transition: stroke-dashoffset .4s ease;
}

.resource-reading {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
}

.resource-value {
    font-size: 24px;
    font-weight: 500;
    line-height: 1.2;
}

.resource-unit {
    font-size: 17px;
    line-height: 1.3;
}

.resource-label {
    margin-top: 10px;
    color: var(--text-2);
    font-size: 18px;
    font-weight: 500;
}

.resource-foot {
    margin-top: 6px;
    color: var(--text-2);
    font-size: 12px;
    overflow-wrap: anywhere;
}

@container (max-width: 495px) {
    .resource-card {
        gap: 16px;
        padding: 24px 16px 20px;
    }
}

@container (max-width: 340px) {
    .resource-card {
        gap: 12px;
        padding-inline: 12px;
    }

    .resource-value {
        font-size: 21px;
    }

    .resource-unit {
        font-size: 15px;
    }
}

@media (prefers-reduced-motion: reduce) {
    .resource-ring-progress {
        transition: none;
    }
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
