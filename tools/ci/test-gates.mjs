import { spawnSync } from 'node:child_process'
import { readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

export function verifyGateTotals(tap) {
  const total = name => Number(new RegExp(`^# ${name} (\\d+)\\r?$`, 'm').exec(tap)?.[1] ?? NaN)
  if (total('tests') < 26 || !Number.isFinite(total('tests')) || total('pass') !== total('tests')
    || ['fail', 'cancelled', 'skipped', 'todo'].some(name => total(name) !== 0)) throw Error('Suite tools/ci reducida, incompleta u omitida')
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const cwd = fileURLToPath(new URL('./', import.meta.url))
  const tests = readdirSync(cwd).filter(name => name.endsWith('.test.mjs')).sort()
  const result = spawnSync(process.execPath, ['--test', '--test-reporter=tap', ...tests], { cwd, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 })
  process.stdout.write(result.stdout ?? ''); process.stderr.write(result.stderr ?? '')
  if (result.status !== 0) process.exitCode = result.status ?? 1
  else verifyGateTotals(result.stdout)
}
