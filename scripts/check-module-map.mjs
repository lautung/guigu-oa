import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const mapPath = join(root, 'docs/agents/client-modules.md')
const clientRoot = join(root, 'client/guigu-oa-client')

const text = readFileSync(mapPath, 'utf8')
const paths = [...new Set([...text.matchAll(/`(src\/[^`]+)`/g)].map((match) => match[1]))]
const missing = paths.filter((path) => !existsSync(join(clientRoot, path)))

if (missing.length) {
  console.error('模块地图引用了不存在的文件：')
  for (const path of missing) console.error(`  - ${path}`)
  process.exit(1)
}

console.log(`module map ok (${paths.length} client paths)`)
