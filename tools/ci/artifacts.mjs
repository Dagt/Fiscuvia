import { readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
import { images } from './security.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url))
const kind = process.argv[2]
if (!['backend', 'web', 'security'].includes(kind)) throw Error('Artifact desconocido')
const dir = resolve(root, '.ci-artifacts', kind)
for (const name of readdirSync(dir)) {
  if (!['summary.json', ...(kind === 'backend' ? ['junit.xml'] : [])].includes(name)) throw Error('Archivo no autorizado en artifact')
}
const scan = spawnSync('docker', ['run', '--rm', '--network=none', '--cap-drop=ALL', '--security-opt=no-new-privileges',
  '-v', `${dir}:/artifact:ro`, images.gitleaks, 'dir', '/artifact', '--redact=100', '--no-banner'], { stdio: 'inherit' })
process.exitCode = scan.status ?? 1
