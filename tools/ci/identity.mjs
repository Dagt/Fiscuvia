import { execFileSync } from 'node:child_process'
import { appendFileSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const requiredNode = readFileSync(new URL('../../web/.nvmrc', import.meta.url), 'utf8').trim()
const npm = execFileSync(process.platform === 'win32' ? 'cmd.exe' : 'npm',
  process.platform === 'win32' ? ['/d', '/c', 'npm --version'] : ['--version'], { encoding: 'utf8' }).trim()
if (process.version !== `v${requiredNode}` || npm !== '11.8.0') throw Error('Versiones Node/npm distintas de las fijadas')
const tested = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
const text = `SHA probado: ${tested}\nSHA origen: ${process.env.SOURCE_SHA ?? tested}\nNode: ${process.version}; npm: ${npm}\nRunner: ${process.env.RUNNER_OS ?? process.platform}; imagen: ${process.env.ImageOS ?? 'local'} ${process.env.ImageVersion ?? ''}\n`
console.log(text)
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n\`\`\`text\n${text}\`\`\`\n`)
