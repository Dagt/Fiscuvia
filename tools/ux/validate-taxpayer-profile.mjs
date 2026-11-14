import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'

const root = fileURLToPath(new URL('../../', import.meta.url))
const folder = resolve(root, 'docs/ux/evidence/issue-36')
const manifest = JSON.parse(readFileSync(resolve(folder, 'capture-manifest.json'), 'utf8'))
const scenarios = ['01-ficha-resumen','02-informacion-alcance-pendiente','03-formulario-administrativo',
  '04-conflicto-version','05-edicion-confirmada','06-confirmacion-archivado','07-ficha-archivada',
  '08-error-recuperable','09-sin-permiso','10-contador-con-grant','11-referencias-vacias']
if (!/^[a-f0-9]{40}$/.test(manifest.sourceSha) || !manifest.chromium
  || !Number.isFinite(Date.parse(manifest.capturedAt))) throw Error('Missing provenance')
const expected = new Set(scenarios.flatMap(s => [1440,390].map(width => `${s}-${width}.png`)))
if (manifest.screenshots.length !== expected.size) throw Error('Incomplete screenshot scenarios')
for (const shot of manifest.screenshots) {
  if (!expected.delete(shot.file) || shot.axeViolations !== 0 || shot.horizontalOverflow !== false
    || ![1440,390].includes(shot.width) || shot.height !== (shot.width === 1440 ? 900 : 844)) throw Error('Invalid screenshot metadata')
  const bytes = readFileSync(resolve(folder, shot.file))
  if (bytes.subarray(0,8).toString('hex') !== '89504e470d0a1a0a'
    || bytes.readUInt32BE(16) !== shot.width || bytes.readUInt32BE(20) !== shot.height
    || createHash('sha256').update(bytes).digest('hex') !== shot.sha256) throw Error('Image dimensions/hash mismatch')
}
if (readdirSync(folder).filter(f => f.endsWith('.png')).length !== scenarios.length * 2) throw Error('Unexpected screenshots')
for (const [key,path] of [['applicationTree','web'],['backendTree','backend/src/main'],['fixtureTree','backend/src/test']]) {
  const sourceTree = execFileSync('git',['rev-parse',`${manifest.sourceSha}:${path}`],{cwd:root,encoding:'utf8'}).trim()
  if (manifest[key] !== sourceTree) throw Error(`Manifest source provenance differs: ${path}`)
}
if (process.argv.includes('--current')) {
  for (const [key,path] of [['applicationTree','web'],['backendTree','backend/src/main'],['fixtureTree','backend/src/test']]) {
    const tree = execFileSync('git',['rev-parse',`HEAD:${path}`],{cwd:root,encoding:'utf8'}).trim()
    if (manifest[key] !== tree) throw Error(`Capture source differs: ${path}`)
  }
}
console.log(`C03: ${scenarios.length * 2} actual PNGs with dimensions, SHA-256 and provenance verified`)
