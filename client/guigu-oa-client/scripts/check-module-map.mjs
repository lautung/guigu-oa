import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const mapPath = join(root, 'docs/agents/client-modules.md')
const clientRoot = join(root, 'client/guigu-oa-client')

const text = readFileSync(mapPath, 'utf8')
// 「基座补齐（计划）」段落预记尚未落地的 module，路径存在性只对已实现段落生效；
// 计划项在落地票里搬进上表时再被本检查接管（ADR 0062）。
const plannedIndex = text.indexOf('## 基座补齐')
const implementedText = plannedIndex === -1 ? text : text.slice(0, plannedIndex)
const paths = [
  ...new Set([...implementedText.matchAll(/`(src\/[^`]+)`/g)].map((match) => match[1])),
]
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

// 服务端地图同一个约定：模块即目录，地图里写成路径的反引号内容必须存在。
// 反引号里还有包名、SQL、类名，所以只认 `lautung-oa-<模块>/…` 与 `server/…` 两种形状；
// 带省略号或通配的示意路径跳过（`lautung-oa-security/.../security/`、`…/V*.sql`）。
const serverRoot = join(root, 'server/lautung-oa-parent')
const serverText = readFileSync(join(root, 'docs/agents/server-modules.md'), 'utf8')
// 计划模块（字典/参数等，ADR 0062）预记在地图里但目录尚未创建，存在性只对已落地行生效。
const implementedServerText = serverText
  .split('\n')
  .filter((line) => !line.includes('计划'))
  .join('\n')
const serverPaths = [
  ...new Set(
    [...implementedServerText.matchAll(/`((?:lautung-oa-[a-z-]+|server)\/[^`]*)`/g)].map(
      (match) => match[1],
    ),
  ),
].filter((path) => !/[*…]|\.\.\.|[<>]/.test(path))

const missingServerPaths = serverPaths.filter(
  (path) => !existsSync(path.startsWith('server/') ? join(root, path) : join(serverRoot, path)),
)

const serverModules = readdirSync(serverRoot, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && entry.name.startsWith('lautung-oa-'))
  .map((entry) => entry.name)
const missingModules = serverModules.filter((name) => !serverText.includes(`\`${name}\``))

if (missingServerPaths.length || missingModules.length) {
  console.error('服务端模块地图与服务端目录不一致：')
  for (const path of missingServerPaths) console.error(`  - 路径不存在：${path}`)
  for (const name of missingModules) console.error(`  - 模块缺少地图行：${name}`)
  process.exit(1)
}

console.log(
  `module map ok (${paths.length} client paths, ${requiredPaths.size} required, ` +
    `${serverPaths.length} server paths, ${serverModules.length} server modules)`,
)
