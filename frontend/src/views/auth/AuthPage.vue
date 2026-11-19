<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '../../components/BrandLogo.vue'
import { registerAccount } from '../../api/authApi'
import { login, auth, homeFor, rememberedLogin } from '../../composables/useAuth'
const route = useRoute(),
  router = useRouter(),
  registering = computed(() => route.path === '/customer/register')
const targetArea = computed(() => route.meta.area || 'customer')
const loginTitle = computed(() =>
  targetArea.value === 'admin'
    ? '管理员登录'
    : targetArea.value === 'staff'
      ? '员工登录'
      : '欢迎回到见微',
)
const loginHint = computed(() =>
  targetArea.value === 'admin'
    ? '请使用管理员账号登录管理工作台。'
    : targetArea.value === 'staff'
      ? '请使用员工账号登录员工工作台。'
      : '登录后，查看你的预约与服务信息。',
)
function routeError() {
  if (route.query.error === 'session-ended') return '此登录已在另一个标签页退出，请重新登录。'
  return route.query.error === 'offline'
    ? '暂时无法连接服务，请确认后端已启动后重试。'
    : route.query.error === 'role'
      ? '当前账号无权进入此工作台，请切换为' +
        (targetArea.value === 'admin' ? '管理员' : '员工') +
        '账号。'
      : ''
}
const form = reactive({ phone: '', displayName: '', password: '', confirmPassword: '' }),
  busy = ref(false),
  error = ref(routeError()),
  success = ref('')
function restoreForm() {
  Object.assign(
    form,
    registering.value ? { phone: '', password: '' } : rememberedLogin(targetArea.value),
    { displayName: '', confirmPassword: '' },
  )
}
watch(() => [targetArea.value, registering.value], restoreForm, { immediate: true })
watch(
  () => route.fullPath,
  () => {
    error.value = routeError()
    success.value = ''
  },
)
async function submit() {
  error.value = ''
  success.value = ''
  if (registering.value && form.password !== form.confirmPassword) {
    error.value = '两次密码不一致'
    return
  }
  busy.value = true
  try {
    if (registering.value) {
      await registerAccount({ ...form })
      await router.push('/customer/login')
      success.value = '注册成功，请登录'
    } else {
      await login({ phone: form.phone, password: form.password })
      const next = route.query.redirect
      const target =
        typeof next === 'string' &&
        (next === '/' + targetArea.value || next.startsWith('/' + targetArea.value + '/'))
          ? next
          : homeFor(auth.user)
      const roles = router.resolve(target).meta.roles
      if (roles && !auth.user.roles.some((role) => roles.includes(role))) {
        error.value =
          '此账号无权进入该工作台，请使用' +
          (targetArea.value === 'admin' ? '管理员' : '员工') +
          '账号登录。'
        form.password = ''
        return
      }
      await router.push(target)
    }
  } catch (e) {
    error.value = e.userMessage || '操作失败，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="auth-shell" :class="'auth-' + targetArea">
    <section class="auth-story">
      <BrandLogo />
      <div v-if="targetArea === 'customer'">
        <span class="eyebrow">A MOMENT TO KEEP</span>
        <h1>留一点时间，<br />给值得纪念的自己。</h1>
        <p>从一场轻松的相遇，开始你的见微故事。</p>
        <div class="light-art"><i></i><b></b><span>LIGHT / LIFE / MOMENT</span></div>
      </div>
      <div v-if="targetArea !== 'customer'" class="portal-intro">
        <span class="eyebrow">{{
          targetArea === 'admin' ? 'STUDIO MANAGEMENT' : 'TEAM WORKSPACE'
        }}</span>
        <h1>
          {{ targetArea === 'admin' ? '让工作室的每一天，' : '专注眼前的服务，' }}<br />{{
            targetArea === 'admin' ? '井然有序。' : '认真对待每次相遇。'
          }}
        </h1>
        <p>
          {{ targetArea === 'admin' ? '预约、资源与经营管理' : '今日工作、个人排期与服务协作' }}
        </p>
      </div>
      <RouterLink v-if="targetArea === 'customer'" to="/customer">← 返回见微</RouterLink
      ><small v-else>见微 · {{ targetArea === 'admin' ? '管理工作台' : '员工工作台' }}</small>
    </section>
    <section class="auth-form">
      <div>
        <span class="eyebrow">WELCOME TO JIANWEI</span>
        <h2>{{ registering ? '初次见面，欢迎你' : loginTitle }}</h2>
        <p>{{ registering ? '创建账号，收藏属于你的时光。' : loginHint }}</p>
        <form
          :id="targetArea + '-' + (registering ? 'register' : 'login')"
          @submit.prevent="submit"
        >
          <label
            >手机号<input
              name="username"
              v-model="form.phone"
              autocomplete="username"
              inputmode="tel"
              pattern="1[3-9][0-9]{9}"
              maxlength="11"
              required
              placeholder="请输入11位手机号" /></label
          ><label v-if="registering"
            >姓名<input
              v-model="form.displayName"
              autocomplete="name"
              minlength="2"
              maxlength="40"
              required
              placeholder="如何称呼你" /></label
          ><label
            >密码<input
              name="password"
              v-model="form.password"
              :autocomplete="registering ? 'new-password' : 'current-password'"
              type="password"
              :minlength="registering ? 8 : 1"
              maxlength="64"
              required
              placeholder="请输入密码" /></label
          ><label v-if="registering"
            >确认密码<input
              v-model="form.confirmPassword"
              autocomplete="new-password"
              type="password"
              minlength="8"
              maxlength="64"
              required
              placeholder="再次输入密码"
          /></label>
          <p v-if="error" role="alert" class="error-message">{{ error }}</p>
          <p v-if="success" role="status" class="success-message">{{ success }}</p>
          <button class="primary-button" :disabled="busy">
            {{ busy ? '正在处理…' : registering ? '创建账号' : '登录' }} <span>→</span>
          </button>
        </form>
        <p v-if="targetArea === 'customer'" class="auth-switch">
          {{ registering ? '已有账号？' : '还没有账号？' }}
          <RouterLink :to="registering ? '/customer/login' : '/customer/register'">{{
            registering ? '前往登录' : '注册新账号'
          }}</RouterLink>
        </p>
        <small>{{
          targetArea === 'customer'
            ? '手机号作为登录账号；忘记密码请联系工作室核实处理。'
            : '账号由工作室分配；无法登录请联系管理员。'
        }}</small>
      </div>
    </section>
  </div>
</template>
