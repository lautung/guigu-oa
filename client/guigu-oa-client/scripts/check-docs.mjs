import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const evidenceRoot = join(root, 'docs/test/evidence')

// #122：入仓证据单文件 ≤100KB，evidence 目录整体 ≤5MB
const EVIDENCE_FILE_LIMIT = 100 * 1024
const EVIDENCE_DIR_LIMIT = 5 * 1024 * 1024
const SKIP_DIRS = new Set([
  '.git',
  '.zcode',
  '.agents',
  'node_modules',
  'target',
  'dist',
  'coverage',
  'coverage-report',
  'test-results',
])
const SKIP_PATHS = new Set(['perf/results', 'perf/snapshot'])

const errors = []

function walk(dir, onFile) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    const rel = relative(root, path).split('\\').join('/')
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name) || SKIP_PATHS.has(rel)) continue
      walk(path, onFile)
      continue
    }
    if (entry.isFile()) onFile(path)
  }
}

function mask(segment) {
  return segment.replace(/[^\n]/g, ' ')
}

function maskCode(text) {
  return text
    .replace(/^```[^\n]*\n[\s\S]*?^```[^\n]*$/gm, mask)
    .replace(/^~~~[^\n]*\n[\s\S]*?^~~~[^\n]*$/gm, mask)
    .replace(/`[^`\n]*`/g, mask)
}

function isExternal(target) {
  return (
    target === '' ||
    target.startsWith('#') ||
    target.startsWith('//') ||
    target.includes('://') ||
    target.startsWith('mailto:')
  )
}

function lineOf(text, index) {
  return text.slice(0, index).split('\n').length
}

function checkTarget(markdownPath, rawTarget, line) {
  let target = rawTarget.trim()
  if (target.startsWith('<')) {
    const end = target.indexOf('>')
    target = end === -1 ? target.slice(1) : target.slice(1, end)
  } else {
    target = target.split(/\s+/)[0]
  }
  if (isExternal(target)) return false

  target = target.split('#')[0].split('?')[0]
  if (!target) return false
  try {
    target = decodeURIComponent(target)
  } catch {
    // 保留原样，交给 existsSync 判定
  }

  const base = target.startsWith('/') ? root : dirname(markdownPath)
  const resolved = resolve(base, target.replace(/^\//, ''))
  if (!existsSync(resolved)) {
    errors.push(`${relative(root, markdownPath)}:${line} 链接目标不存在：${rawTarget}`)
  }
  return true
}

function checkMarkdownLinks(markdownPath) {
  const text = readFileSync(markdownPath, 'utf8')
  const masked = maskCode(text)
  let checked = 0

  for (const match of masked.matchAll(/\[[^\]]*\]\(([^)\n]+)\)/g)) {
    if (checkTarget(markdownPath, match[1], lineOf(masked, match.index))) checked += 1
  }
  for (const match of masked.matchAll(/^[ \t]{0,3}\[[^\]]+\]:[ \t]*(?:<([^>\n]+)>|(\S+))/gm)) {
    if (checkTarget(markdownPath, match[1] ?? match[2], lineOf(masked, match.index))) {
      checked += 1
    }
  }
  return checked
}

function formatSize(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`
  return `${Math.round(bytes / 1024)}KB`
}

function checkEvidenceSize() {
  if (!existsSync(evidenceRoot)) return 0
  let total = 0
  walk(evidenceRoot, (path) => {
    const size = statSync(path).size
    total += size
    if (size > EVIDENCE_FILE_LIMIT) {
      errors.push(
        `${relative(root, path)} 超过单文件上限：${formatSize(size)} > ${formatSize(EVIDENCE_FILE_LIMIT)}`,
      )
    }
  })
  if (total > EVIDENCE_DIR_LIMIT) {
    errors.push(
      `docs/test/evidence 超过目录上限：${formatSize(total)} > ${formatSize(EVIDENCE_DIR_LIMIT)}`,
    )
  }
  return total
}

// 执行记录里用行内代码写的证据路径不会走 Markdown 链接检查；这里补上，
// 并禁止 .log（被 .gitignore 忽略，会静默漏提交，证据日志用 .txt）。
function checkEvidencePaths(markdownPath) {
  const text = readFileSync(markdownPath, 'utf8')
  let checked = 0

  for (const match of text.matchAll(/`(docs\/test\/evidence\/[^`\n]+)`/g)) {
    const target = match[1].trim()
    if (/[<>*…]/.test(target)) continue
    const line = lineOf(text, match.index)
    if (target.endsWith('.log')) {
      errors.push(
        `${relative(root, markdownPath)}:${line} 证据日志用 .txt（*.log 被 .gitignore 忽略）：${target}`,
      )
      continue
    }
    if (!existsSync(join(root, target))) {
      errors.push(`${relative(root, markdownPath)}:${line} 证据路径不存在：${target}`)
    }
    checked += 1
  }
  return checked
}

let markdownFiles = 0
let linkCount = 0
let evidencePathCount = 0

walk(root, (path) => {
  if (!path.endsWith('.md')) return
  markdownFiles += 1
  linkCount += checkMarkdownLinks(path)
  const rel = relative(root, path).split('\\').join('/')
  if (rel.startsWith('docs/test/')) evidencePathCount += checkEvidencePaths(path)
})

const evidenceSize = checkEvidenceSize()

if (errors.length) {
  console.error('文档检查失败：')
  for (const error of errors) console.error(`  - ${error}`)
  process.exit(1)
}

console.log(
  `docs ok (${markdownFiles} markdown files, ${linkCount} relative links, ${evidencePathCount} evidence paths, evidence ${formatSize(
    evidenceSize,
  )})`,
)
