import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 契约测试的负例要能指认是哪个分支拒绝的（bb2ee5a 的约定，ADR 0034）。
 *
 * 三条机械规则：
 * 1. 断言 201 的用例必须同链钉 `$.message`：201 有多种拒绝理由（校验、业务规则、兜底），只钉 code 时分支一换会以别的理由继续绿。205 不在此列——它的文案由两个 producer 统一为「没有操作权限」，码加上 `status().isOk()` 已经指认了分支。
 * 2. 控制器的 `@RequestBody` 不得是 MyBatis 实体（配置写请求体必须是 `vo/*Request`）。
 * 3. `*SaveRequest` 不得声明 `id`——save 只创建。
 */

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const serverJava = join(root, 'server/guigu-oa-parent')
const errors = []

function walk(dir, filter, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'target' || entry.name === 'node_modules') continue
      walk(path, filter, out)
    } else if (filter(path)) {
      out.push(path)
    }
  }
  return out
}

/** 按顶层 `;` 切 Java 语句，跳过字符串、字符、注释。只按圆/方括号计深：花括号是方法体，不该拦住语句结束。 */
function statements(source) {
  const out = []
  let start = 0
  let depth = 0
  let inString = false
  let inChar = false
  let lineComment = false
  let blockComment = false
  for (let i = 0; i < source.length; i += 1) {
    const char = source[i]
    const next = source[i + 1]
    if (lineComment) {
      if (char === '\n') lineComment = false
      continue
    }
    if (blockComment) {
      if (char === '*' && next === '/') {
        blockComment = false
        i += 1
      }
      continue
    }
    if (inString) {
      if (char === '\\') i += 1
      else if (char === '"') inString = false
      continue
    }
    if (inChar) {
      if (char === '\\') i += 1
      else if (char === "'") inChar = false
      continue
    }
    if (char === '/' && next === '/') {
      lineComment = true
      i += 1
      continue
    }
    if (char === '/' && next === '*') {
      blockComment = true
      i += 1
      continue
    }
    if (char === '"') {
      inString = true
      continue
    }
    if (char === "'") {
      inChar = true
      continue
    }
    if (char === '(' || char === '[') depth += 1
    else if (char === ')' || char === ']') depth -= 1
    else if (char === ';' && depth === 0) {
      out.push({ start, text: source.slice(start, i + 1) })
      start = i + 1
    }
  }
  return out
}

function lineOf(lineStarts, offset) {
  let low = 0
  let high = lineStarts.length - 1
  while (low < high) {
    const mid = Math.ceil((low + high) / 2)
    if (lineStarts[mid] <= offset) low = mid
    else high = mid - 1
  }
  return low + 1
}

function lineStartsOf(source) {
  const starts = [0]
  for (let i = 0; i < source.length; i += 1) if (source[i] === '\n') starts.push(i + 1)
  return starts
}

const CODE_ASSERTION = /jsonPath\("\$\.code"\)\.value\((201)\)/g
const MESSAGE_ASSERTION = /jsonPath\("\$\.message"\)/

// 规则 1：201/205 断言必须同链钉 message。
for (const path of walk(serverJava, (p) => p.endsWith('.java') && p.includes('/src/test/java/'))) {
  const source = readFileSync(path, 'utf8')
  if (!source.includes('$.code')) continue
  const lineStarts = lineStartsOf(source)
  for (const statement of statements(source)) {
    for (const match of statement.text.matchAll(CODE_ASSERTION)) {
      if (MESSAGE_ASSERTION.test(statement.text)) continue
      const offset = statement.start + match.index
      errors.push(
        `${relative(root, path)}:${lineOf(lineStarts, offset)} 断言 code=${match[1]} 但没钉 message：分支一换会以别的理由继续绿`,
      )
    }
  }
}

// 规则 2：控制器不得把 MyBatis 实体当请求体。
const entityNames = new Set()
for (const path of walk(join(serverJava, 'model/src/main/java'), (p) => p.endsWith('.java'))) {
  const source = readFileSync(path, 'utf8')
  if (!source.includes('@TableName(')) continue
  const declaration = source.match(/public class (\w+)/)
  if (declaration) entityNames.add(declaration[1])
}

for (const path of walk(join(serverJava, 'service-oa/src/main/java'), (p) => p.endsWith('.java'))) {
  const source = readFileSync(path, 'utf8')
  if (!source.includes('@RequestBody')) continue
  const lineStarts = lineStartsOf(source)
  for (const match of source.matchAll(/@RequestBody\s+([\w<>,.\s]+?)\s+\w+\s*[,)]/g)) {
    const type = match[1].replace(/\s+/g, '')
    const simple = type.split('.').pop().replace(/<.*$/, '')
    if (!entityNames.has(simple)) continue
    errors.push(
      `${relative(root, path)}:${lineOf(lineStarts, match.index)} @RequestBody 用了实体 ${type}：配置写请求体收在 vo/*Request（ADR 0034）`,
    )
  }
}

// 规则 3：Save 请求体不含 id。
for (const path of walk(join(serverJava, 'model/src/main/java'), (p) => p.endsWith('SaveRequest.java'))) {
  const source = readFileSync(path, 'utf8')
  const lineStarts = lineStartsOf(source)
  const declaration = /private\s+[\w<>,.\s]+\s+id\s*;/.exec(source)
  if (!declaration) continue
  errors.push(
    `${relative(root, path)}:${lineOf(lineStarts, declaration.index)} Save 请求体声明了 id：save 只创建，update 才带主键（ADR 0034）`,
  )
}

const checked = walk(serverJava, (p) => p.endsWith('.java') && p.includes('/src/test/java/')).length
if (errors.length) {
  console.error('契约检查失败：')
  for (const error of errors) console.error(`  - ${error}`)
  process.exit(1)
}
console.log(`contracts ok (${checked} server tests, ${entityNames.size} entities)`)
