// A version is acknowledged only after its data has been safely applied.
export function createChangeSync({
  fetchData,
  current,
  apply,
  key = () => '',
  paused = () => false,
  onError = () => {},
  onSuccess = () => {},
  readVersion,
  now = () => Date.now(),
}) {
  let running = false,
    stopped = false,
    acceptedVersion,
    acceptedContext,
    lastRead = null
  return {
    async check() {
      if (stopped || running || paused()) return
      const context = key()
      running = true
      try {
        const snapshot = readVersion ? await readVersion() : null
        if (stopped || paused() || context !== key()) return
        const recent = lastRead !== null && now() - lastRead < 60000 && acceptedContext === context
        if (snapshot && recent && (!snapshot.available || snapshot.version === acceptedVersion)) {
          onSuccess()
          return
        }
        const next = await fetchData()
        if (stopped || paused() || context !== key()) return
        if (JSON.stringify(next) !== JSON.stringify(current())) apply(next)
        acceptedVersion = snapshot?.available ? snapshot.version : undefined
        acceptedContext = context
        lastRead = now()
        onSuccess()
      } catch (error) {
        if (!stopped && !paused() && context === key()) onError(error)
      } finally {
        running = false
      }
    },
    stop() {
      stopped = true
    },
  }
}
