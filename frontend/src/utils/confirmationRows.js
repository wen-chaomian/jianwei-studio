export function confirmationRows(summary) {
  const formatted = summary.replace(/(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(?::\d{2})?/g, '$1 $2')
  // Legacy confirmations remain readable without changing their saved business data.
  const legacyBooking = formatted.match(/^([^\n]+?) · ([^\n]+?) · (\d{4}-\d{2}-\d{2} \d{2}:\d{2}) · (\d+分钟) · (¥[\d.]+)([\s\S]*)$/)
  if (legacyBooking) return ['拍摄套餐','摄影师','拍摄时间','拍摄时长','预约金额','补充信息'].flatMap((label,index) => {
    const value=legacyBooking[index+1].trim()
    return value ? [{ label, value }] : []
  })
  const lines = formatted.split('\n')
  const labels = /^(拍摄套餐|摄影师|拍摄时间|拍摄时长|预约金额|客户姓名|联系电话|备注|预约信息|办理内容|偏好主题|偏好内容|文章信息|正文|申请内容|办理说明)：(.*)$/
  return lines.flatMap(line => {
    const match = line.match(labels)
    if (match) return [{ label: match[1], value: match[2] || '—' }]
    return line.split(' · ').filter(x => x.trim()).map(value => ({ label: '', value: value.trim() }))
  })
}
