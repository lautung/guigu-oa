import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 守卫 ADR 0028/0055：全仓只有一个 v-html 渲染面（公告正文）。
 * ESLint 已对该文件关闭 `vue/no-v-html`；这里防止例外静默扩散到第二个文件。
 *
 * 只扫 `.vue` 模板（v-html 是模板指令），注释里提到它不算注入。
 */

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const srcRoot = join(root, 'src')
const ALLOWED = 'src/notice/NoticeContent.vue'

function vueFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return vueFiles(path)
    return entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
  })
}

const offenders = vueFiles(srcRoot)
  .map((path) => ({ path, text: readFileSync(path, 'utf8') }))
  .filter(({ path }) => relative(root, path) !== ALLOWED)
  .filter(({ text }) => text.includes('v-html'))
  .map(({ path }) => relative(root, path))

if (offenders.length) {
  console.error('v-html 守卫失败：公告正文之外出现了 v-html 渲染面：')
  for (const path of offenders) console.error(`  - ${path}`)
  console.error(`唯一允许的渲染面是 ${ALLOWED}（ADR 0028/0055）。`)
  process.exit(1)
}

console.log(`v-html ok (only ${ALLOWED})`)
