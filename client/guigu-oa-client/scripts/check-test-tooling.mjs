import { readdirSync, readFileSync } from 'node:fs'
import { dirname, extname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'))
const vitest = pkg.devDependencies?.vitest
const coverage = pkg.devDependencies?.['@vitest/coverage-v8']

if (!vitest || !coverage) {
  console.error('vitest 与 @vitest/coverage-v8 必须同时存在于 devDependencies')
  process.exit(1)
}

if (vitest !== coverage) {
  console.error(`@vitest/coverage-v8 (${coverage}) 必须与 vitest (${vitest}) 同版本`)
  process.exit(1)
}

// ADR 0070：verify 与 verify:ci 只允许末位单测不同（本地不带覆盖率，CI 带），
// 前置步骤必须逐字相同；否则以后只往一条链加检查，CI 与提交前门禁就会漂移。
const verifyStatic = pkg.scripts?.['verify:static']
const verifySteps = (pkg.scripts?.verify ?? '').split('&&').map((step) => step.trim())
const verifyCiSteps = (pkg.scripts?.['verify:ci'] ?? '').split('&&').map((step) => step.trim())
const staticSteps = verifySteps.slice(0, -1)
const staticCiSteps = verifyCiSteps.slice(0, -1)

if (
  !verifyStatic ||
  staticSteps.length === 0 ||
  staticSteps.join('&&') !== staticCiSteps.join('&&') ||
  verifySteps.at(-1) !== 'npm run test:unit' ||
  verifyCiSteps.at(-1) !== 'npm run test:unit:coverage'
) {
  console.error(
    'verify 与 verify:ci 必须共享同一段 verify:static 前置步骤，末位分别为 test:unit / test:unit:coverage（ADR 0070）',
  )
  process.exit(1)
}

// #226：办事列表行选择器只允许出现在共享 helper 里。security-xss 曾复制一份没有
// #169 首轮正信号的裸扫，在慢 runner 上间歇红；禁止第三次复制。
const WORK_LIST_SELECTOR = '.work-list-item'
const WORK_LIST_HELPER = 'tests/helpers/work-list.ts'
const TEST_EXTENSIONS = new Set(['.ts', '.js', '.vue'])
const SKIP_DIRS = new Set(['node_modules', 'test-results', 'playwright-report'])

const violations = []

function scan(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) scan(path)
      continue
    }
    if (!TEST_EXTENSIONS.has(extname(entry.name))) continue
    const relPath = relative(root, path).split('\\').join('/')
    if (relPath === WORK_LIST_HELPER) continue
    readFileSync(path, 'utf8')
      .split('\n')
      .forEach((line, index) => {
        if (line.includes(WORK_LIST_SELECTOR)) {
          violations.push(`${relPath}:${index + 1}: ${line.trim()}`)
        }
      })
  }
}

scan(join(root, 'tests'))

if (violations.length > 0) {
  console.error(
    `办事列表行选择器 ${WORK_LIST_SELECTOR} 只能出现在 ${WORK_LIST_HELPER}（#226），以下位置请改用共享 helper：\n${violations.join('\n')}`,
  )
  process.exit(1)
}

console.log(`test tooling ok (vitest ${vitest})`)
