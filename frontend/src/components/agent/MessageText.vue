<script setup>
import { computed } from 'vue'
const props = defineProps({ content: { type: String, default: '' } })
// Render lightweight emphasis as text nodes; never execute model-provided HTML.
const parts = computed(() => props.content.split(/(\*\*[^*\n]+\*\*|`[^`\n]+`)/g).map(text => ({
  text: text.startsWith('**') && text.endsWith('**') ? text.slice(2, -2) : text.startsWith('`') && text.endsWith('`') ? text.slice(1, -1) : text,
  kind: text.startsWith('**') && text.endsWith('**') ? 'strong' : text.startsWith('`') && text.endsWith('`') ? 'code' : 'span',
})))
</script>
<template><div class="agent-message-content"><component v-for="(part, index) in parts" :key="index" :is="part.kind">{{ part.text }}</component></div></template>
