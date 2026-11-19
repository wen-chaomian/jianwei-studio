<script setup>
defineProps({ page: Number, total: Number, loading: Boolean })
defineEmits(['turn'])
</script>
<template>
  <div class="pagination">
    <span>共 {{ total }} 条</span
    ><button class="outline-button" :disabled="page <= 1 || loading" @click="$emit('turn', -1)">
      上一页</button
    ><span>{{ page }}</span
    ><button
      class="outline-button"
      :disabled="page * 10 >= total || loading"
      @click="$emit('turn', 1)"
    >
      下一页
    </button>
  </div>
</template>
