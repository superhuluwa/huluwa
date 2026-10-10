<template>
    <div class="resource-charts">
        <div ref="cpuElement" class="resource-chart" role="img" aria-label="CPU 使用率历史曲线，最近 60 次采样"></div>
        <div ref="memoryElement" class="resource-chart" role="img" aria-label="内存使用历史曲线，最近 60 次采样"></div>
    </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { formatBytes } from './resources'

use([LineChart, GridComponent, TooltipComponent, TitleComponent, CanvasRenderer])
const props = defineProps({ samples: { type: Array, default: () => [] } })
const cpuElement = ref(null)
const memoryElement = ref(null)
let cpuChart, memoryChart, observer

function option(title, key, color, memory = false) {
    return {
        animation: false,
        backgroundColor: 'transparent',
        title: { text: title, textStyle: { color: '#dbeafe', fontSize: 13 } },
        grid: { left: 12, right: 16, top: 42, bottom: 14, containLabel: true },
        tooltip: { trigger: 'axis', valueFormatter: value => memory ? formatBytes(value) : Number(value).toFixed(2) + '%' },
        xAxis: { type: 'time', axisLabel: { color: '#94a3b8' }, axisLine: { lineStyle: { color: '#334155' } } },
        yAxis: {
            type: 'value', min: 0,
            axisLabel: { color: '#94a3b8', formatter: value => memory ? formatBytes(value) : value + '%' },
            splitLine: { lineStyle: { color: 'rgba(148,163,184,.12)' } }
        },
        series: [{ name: title, type: 'line', smooth: true, showSymbol: false,
            data: props.samples.map(sample => [sample.at, sample[key]]),
            lineStyle: { color, width: 2 }, itemStyle: { color }, areaStyle: { color, opacity: .15 } }]
    }
}
function update() {
    cpuChart?.setOption(option('CPU 使用率 (%)', 'cpu', '#22d3ee'), true)
    memoryChart?.setOption(option('内存使用量', 'memory', '#38bdf8', true), true)
}
function setupCharts() {
    if (!cpuElement.value || cpuChart) return
    cpuChart = init(cpuElement.value)
    memoryChart = init(memoryElement.value)
    observer = new ResizeObserver(() => { cpuChart?.resize(); memoryChart?.resize() })
    observer.observe(cpuElement.value)
    observer.observe(memoryElement.value)
    update()
}
function destroy() {
    observer?.disconnect()
    cpuChart?.dispose()
    memoryChart?.dispose()
    cpuChart = memoryChart = observer = null
}
watch(() => props.samples, update)
onMounted(setupCharts)
onActivated(setupCharts)
onDeactivated(destroy)
onBeforeUnmount(destroy)
</script>

<style scoped>
.resource-charts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-top: 16px; }
.resource-chart { height: 240px; min-width: 0; border: 1px solid var(--line); border-radius: 8px; padding: 12px; }
@media (max-width: 760px) { .resource-charts { grid-template-columns: 1fr; } }
</style>
