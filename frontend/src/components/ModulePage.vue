<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">JIANWEI STUDIO</span>
      <h1>{{ route.meta.title }}</h1>
      <p>用清晰的安排，留出更多认真服务的时间。</p>
    </div>
  </div>
  <section class="panel empty-state">
    <span class="empty-symbol">◇</span>
    <h3>{{ route.meta.title === '页面不存在' ? '没有找到这个页面' : '页面框架已就绪' }}</h3>
    <p>
      {{
        route.meta.title === '页面不存在'
          ? '请通过导航返回工作台。'
          : '业务功能将在后续批次接入，目前不会创建或修改业务数据。'
      }}
    </p>
  </section>
</template>
