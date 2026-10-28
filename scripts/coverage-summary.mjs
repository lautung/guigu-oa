#!/usr/bin/env node
/**
 * Prints a coverage summary from the reports the suites already wrote.
 * Read-only: it never runs tests, so CI and local callers stay in control of what ran.
 *
 * Usage:
 *   node scripts/coverage-summary.mjs [--frontend|--backend] [--markdown]
 *
 * Defaults to both scopes and plain text. A missing report is reported as such and does not
 * fail the process: coverage stays report-only.
 */
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const args = new Set(process.argv.slice(2))
const markdown = args.has('--markdown')
const explicitScope = args.has('--frontend') || args.has('--client') || args.has('--backend') || args.has('--server')
const wantFrontend = !explicitScope || args.has('--frontend') || args.has('--client')
const wantBackend = !explicitScope || args.has('--backend') || args.has('--server')

const frontendReport = join(root, 'client/guigu-oa-client/coverage/coverage-summary.json')
const backendReport = join(
  root,
  'server/guigu-oa-parent/coverage-report/target/site/jacoco-aggregate/jacoco.csv',
)

function percent(covered, total) {
  return total === 0 ? '0.0' : ((covered / total) * 100).toFixed(1)
}

function readFrontend() {
  if (!existsSync(frontendReport)) return null
  const { total } = JSON.parse(readFileSync(frontendReport, 'utf8'))
  return { lines: total.lines.pct.toFixed(1), branches: total.branches.pct.toFixed(1) }
}

function readBackend() {
  if (!existsSync(backendReport)) return null
  const rows = readFileSync(backendReport, 'utf8').trim().split('\n').slice(1)
  let lineCovered = 0
  let lineMissed = 0
  let branchCovered = 0
  let branchMissed = 0
  for (const row of rows) {
    const column = row.split(',')
    lineMissed += Number(column[7])
    lineCovered += Number(column[8])
    branchMissed += Number(column[5])
    branchCovered += Number(column[6])
  }
  return {
    lines: percent(lineCovered, lineCovered + lineMissed),
    branches: percent(branchCovered, branchCovered + branchMissed),
  }
}

const scopes = []
if (wantFrontend) scopes.push(['Frontend', readFrontend, 'coverage/'])
if (wantBackend) scopes.push(['Backend', readBackend, 'coverage-report/target/site/jacoco-aggregate/'])

if (markdown) {
  console.log('### Coverage (report only, not a gate)')
  console.log('')
  console.log('| Scope | Lines | Branches |')
  console.log('| --- | --- | --- |')
  for (const [name, read, path] of scopes) {
    const summary = read()
    if (!summary) {
      console.log(`| ${name} | report missing | report missing |`)
      console.log('')
      console.log(`> ${name} report missing under \`${path}\`; tests may not have completed.`)
      console.log('')
      continue
    }
    console.log(`| ${name} | ${summary.lines}% | ${summary.branches}% |`)
  }
} else {
  for (const [name, read] of scopes) {
    const summary = read()
    console.log(
      summary
        ? `${name}: lines ${summary.lines}% branches ${summary.branches}%`
        : `${name}: coverage report missing`,
    )
  }
}
