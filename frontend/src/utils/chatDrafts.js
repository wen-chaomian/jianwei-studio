// Tab-local drafts are isolated by portal, account and conversation.
export function createChatDrafts(storage) {
  const key = (account, id) => `jianwei.chatDraft:${account}:${id}`
  const read = k => { try { return storage.getItem(k) || '' } catch { return '' } }
  const write = (k, value) => { try { value ? storage.setItem(k, value) : storage.removeItem(k) } catch { /* Storage may be unavailable; typing still works. */ } }
  return {
    read: (account, id) => read(key(account, id)),
    save: (account, id, value) => write(key(account, id), value),
    select: (account, id) => write(key(account, '@last'), id),
    last: account => read(key(account, '@last')),
    remove: (account, id) => { write(key(account, id), ''); if (read(key(account, '@last')) === id) write(key(account, '@last'), '') },
  }
}
