import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
import { parse } from 'yaml'

export function checkLocalRefs(document) {
  function visit(value) {
    if (!value || typeof value !== 'object') return
    for (const [key, child] of Object.entries(value)) {
      if (key === '$ref' && (typeof child !== 'string' || !child.startsWith('#/'))) {
        throw new Error('Sólo se permiten referencias internas; no se descargan contratos externos.')
      }
      visit(child)
    }
  }
  visit(document)
}

export function validate(path) {
  checkLocalRefs(parse(readFileSync(path, 'utf8'), { uniqueKeys: true }))
  const result = spawnSync(process.execPath, [
    fileURLToPath(new URL('./node_modules/@redocly/cli/bin/cli.js', import.meta.url)),
    'lint', path, '--config', fileURLToPath(new URL('./redocly.yaml', import.meta.url)),
  ], { stdio: 'inherit', env: { ...process.env, REDOCLY_TELEMETRY: 'off' } })
  return result.status ?? 1
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = validate(resolve(process.argv[2] ?? '../../docs/api/openapi-v1-draft.yaml'))
}
