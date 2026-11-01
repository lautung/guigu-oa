import { execFileSync } from 'node:child_process'
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const dirs = [join(root, 'scripts'), join(root, 'perf/ops')]

// 变量名后紧跟非 ASCII 字符（例如中文全角括号）时，bash 会把该字符并进变量名。
// macOS 自带的 bash 3.2 在 set -u 下直接报 unbound variable，而 bash -n 查不出来。
const VAR_REFERENCE = /\$[A-Za-z_][A-Za-z0-9_]*/g

const errors = []
const files = []

for (const dir of dirs) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isFile()) continue
    const path = join(dir, entry.name)

    if (entry.name.endsWith('.sh')) {
      files.push(path)

      try {
        execFileSync('bash', ['-n', path], { stdio: 'pipe' })
      } catch (error) {
        const detail = error.stderr?.toString().trim() || error.message
        errors.push(`${relative(root, path)} 语法检查失败：${detail}`)
      }

      const lines = readFileSync(path, 'utf8').split('\n')
      lines.forEach((line, index) => {
        if (line.trimStart().startsWith('#')) return
        for (const match of line.matchAll(VAR_REFERENCE)) {
          const next = line[match.index + match[0].length]
          if (!next || next.charCodeAt(0) <= 0x7f) continue
          errors.push(
            `${relative(root, path)}:${index + 1} 变量名后紧跟非 ASCII 字符（写成 \${var}）：${match[0]}${next}`,
          )
        }
      })
    } else if (entry.name.endsWith('.mjs')) {
      // 仓库根脚本工具（安全扫描、合同断言检查等）只在特定轮次执行，语法错误
      // 若不在门禁拦截，就要到打真栈的那一轮才炸（retro 2026-11-01，#184 轮）。
      files.push(path)

      try {
        execFileSync(process.execPath, ['--check', path], { stdio: 'pipe' })
      } catch (error) {
        const detail = error.stderr?.toString().trim() || error.message
        errors.push(`${relative(root, path)} 语法检查失败：${detail}`)
      }
    }
  }
}

if (errors.length) {
  console.error('Shell 检查失败：')
  for (const error of errors) console.error(`  - ${error}`)
  process.exit(1)
}

console.log(`shell ok (${files.length} scripts)`)
