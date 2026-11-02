import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { basename, dirname, extname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// 历史文档保留旧名（ADR/research/spec/prototypes/执行记录/证据/总结报告）。
const ALLOWED_PREFIXES = [
  'docs/adr/',
  'docs/research/',
  'docs/spec/',
  'docs/prototypes/',
  'docs/test/evidence/',
  'docs/test/execution/',
  'docs/test/report.md',
]

const TEXT_EXTENSIONS = new Set([
  '.java',
  '.xml',
  '.yml',
  '.yaml',
  '.md',
  '.sh',
  '.mjs',
  '.js',
  '.ts',
  '.vue',
  '.json',
  '.sql',
  '.properties',
  '.html',
  '.conf',
  '.template',
  '.toml',
  '.txt',
])
const TEXT_FILENAMES = new Set(['Dockerfile', 'compose.yaml', 'AGENTS.md', 'README.md'])

const LEGACY_TERMS = [
  { label: 'com.atguigu', pattern: /com\.atguigu|com\/atguigu/ },
  { label: 'GuiguException', pattern: /GuiguException/ },
  { label: 'ServiceAuthApplication', pattern: /ServiceAuthApplication/ },
  { label: 'guigu.oa / GUIGU_OA_ / guigu.jwt 配置前缀', pattern: /guigu\.oa\.|GUIGU_OA_|guigu\.jwt\./ },
  { label: 'guigu-oa-parent', pattern: /guigu-oa-parent/ },
  { label: 'service-oa 服务名', pattern: /(?<![A-Za-z0-9_-])service-oa(?![A-Za-z0-9_-])/ },
]

const files = execFileSync('git', ['ls-files', '--full-name', '-z'], { cwd: root, encoding: 'utf8' })
  .split('\0')
  .filter(Boolean)

const errors = []

for (const file of files) {
  // 检查器自身含旧词模式字面量，不参与扫描。
  if (file.endsWith('check-legacy-terms.mjs')) continue
  if (ALLOWED_PREFIXES.some((prefix) => file.startsWith(prefix))) continue
  const extension = extname(file)
  if (!TEXT_EXTENSIONS.has(extension) && !TEXT_FILENAMES.has(basename(file))) continue

  const text = readFileSync(resolve(root, file), 'utf8')
  text.split('\n').forEach((line, index) => {
    if (/@author\s+atguigu/.test(line)) return
    for (const term of LEGACY_TERMS) {
      if (!term.pattern.test(line)) continue
      errors.push(`${relative(root, file)}:${index + 1} 旧品牌词 ${term.label}：${line.trim().slice(0, 120)}`)
    }
  })
}

if (errors.length) {
  console.error('旧品牌词检查失败（历史文档已在 allowlist）：')
  for (const error of errors) console.error(`  - ${error}`)
  process.exit(1)
}

console.log(`legacy terms ok (${files.length} tracked files)`)
