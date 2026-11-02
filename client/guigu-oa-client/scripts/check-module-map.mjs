import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
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

// 目录即模块：这些目录里的每个 .ts 都必须在地图中有一行，避免“路径存在但模块没登记”。
// router/index.ts 是接线，不算模块；config 下只有 permission.ts 是跨域模块。
const moduleDirectories = ['src/request', 'src/list']
const requiredPaths = new Set([
  ...moduleDirectories.flatMap((directory) =>
    readdirSync(join(clientRoot, directory))
      .filter((name) => name.endsWith('.ts'))
      .map((name) => `${directory}/${name}`),
  ),
  'src/router/guard.ts',
  'src/router/paths.ts',
  'src/router/routes.ts',
  'src/config/permission.ts',
])
const missingFromMap = [...requiredPaths].filter((path) => !paths.includes(path))

if (missingFromMap.length) {
  console.error('模块地图缺少这些模块路径：')
  for (const path of missingFromMap) console.error(`  - ${path}`)
  process.exit(1)
}

console.log(`module map ok (${paths.length} client paths, ${requiredPaths.size} required)`)
