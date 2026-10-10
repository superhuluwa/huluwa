<template>
    <div class="error-page">
        <div class="glass-card box">
            <div class="code">500</div>
            <div class="text">服务异常</div>
            <n-button type="primary" @click="$router.push('/')">返回首页</n-button>
        </div>
    </div>
</template>

<script>
export default {
    name: 'Error500'
}
</script>

<style scoped>
.error-page {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg);
}

.box {
    padding: 40px 60px;
    text-align: center;
}

.code {
    font-size: 48px;
    font-weight: 700;
    color: var(--danger);
    font-variant-numeric: tabular-nums;
}

.text {
    margin: 10px 0 22px;
    color: var(--text-2);
}
</style>