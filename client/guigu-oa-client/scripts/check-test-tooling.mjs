import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
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

console.log(`test tooling ok (vitest ${vitest})`)
