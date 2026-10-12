const origin = (process.env.DEMO_ORIGIN || 'http://localhost:9530').replace(/\/$/, '')

async function login(username) {
  const response = await fetch(`${origin}/dev-api/admin/system/index/login`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: '111111' })
  })
  if (!response.ok) throw new Error(`Login for ${username} returned HTTP ${response.status}`)
  const payload = await response.json()
  if (payload.code !== 200 || !payload.data?.token) throw new Error(`Login failed for ${username}`)
  return payload.data.token
}

async function api(username, path) {
  const token = await login(username)
  const response = await fetch(`${origin}/dev-api${path}`, {
    headers: { Accept: 'application/json', token }
  })
  if (!response.ok) throw new Error(`${path} returned HTTP ${response.status}`)
  const payload = await response.json()
  if (payload.code !== 200) throw new Error(`${path} returned API code ${payload.code}`)
  return payload.data
}

const loginPage = await fetch(`${origin}/login`)
if (!loginPage.ok || !(await loginPage.text()).includes('<div id="app">')) {
  throw new Error('The unified client login page is unavailable')
}

const bobPending = await api('bob', '/admin/process/findPending/1/20')
const bobProcessed = await api('bob', '/admin/process/findProcessed/1/20')
const carolCopied = await api('carol', '/admin/process/findCarbonCopied/1/20')
if (!bobPending.total || !bobPending.records?.length) throw new Error('Bob has no pending approval')
if (!bobProcessed.total || !bobProcessed.records?.length) throw new Error('Bob has no ended approval')
if (!carolCopied.total || !carolCopied.records?.length) throw new Error('Carol has no copied approval')

let attachmentCount = 0
for (const record of carolCopied.records) {
  const detail = await api('carol', `/admin/process/show/${record.id}`)
  const attachments = await api('carol', `/admin/attachments/processes/${record.id}`)
  if (detail.isApprove !== false) throw new Error('A copied approval must be read-only')
  if (attachments.canUpload !== false) throw new Error('A copied recipient must not upload attachments')
  attachmentCount += attachments.attachments?.length || 0
}
if (!attachmentCount) throw new Error('Carol has no copied approval with attachment metadata')

console.log(`Compose demo OK: Bob pending=${bobPending.total}, ended=${bobProcessed.total}, Carol copied=${carolCopied.total}, attachments=${attachmentCount}`)
