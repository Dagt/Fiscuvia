import { execFileSync, spawnSync } from 'node:child_process'
import { readFileSync, mkdirSync, mkdtempSync, cpSync, existsSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

const root = fileURLToPath(new URL('../../', import.meta.url))
export const images = {
  gitleaks: 'ghcr.io/gitleaks/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f',
  actionlint: 'rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667',
  zizmor: 'ghcr.io/zizmorcore/zizmor:1.31.1@sha256:42d409dbb851d4c5d35a058cf042b828bea1e3f2884378c526eb0974780e18d9',
  trivy: 'aquasec/trivy:0.76.1@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c',
}

export function checkPath(path) {
  const name = path.split('/').at(-1)
  if ((/^\.env(?:\.|$)/i.test(name) && name !== '.env.example')
    || /\.(pem|p12|pfx|key|cer|crt|jks|keystore|secret|sqlite|db|dump|bak|sql\.gz|zip)$/i.test(name)
    || /\.(xml|pdf)$/i.test(name) && path !== 'backend/pom.xml') {
    throw Error(`Archivo sensible prohibido: ${path}`)
  }
}

export function checkWorkflow(text) {
  const workflow = parse(text, { uniqueKeys: true })
  if (workflow.on?.pull_request_target || !workflow.on?.pull_request || !workflow.on?.push
    || !('workflow_dispatch' in workflow.on) || workflow.permissions?.contents !== 'read'
    || Object.keys(workflow.permissions).length !== 1) throw Error('Triggers o permisos inseguros')
  if (/\bsecrets\s*[.[]|\bwrite-all\b|\bcontinue-on-error\s*:\s*true/.test(text)) throw Error('Permisos o secretos no permitidos')
  if (/paths(?:-ignore)?\s*:/.test(text) || /pull_request\.draft/.test(text)) throw Error('No omitir Draft ni rutas')
  if (!workflow.concurrency || !workflow.jobs) throw Error('Falta concurrencia o jobs')
  const names = { backend: 'Backend CI', web: 'Web CI', openapi: 'OpenAPI CI', security: 'Security CI', required: 'CI Required' }
  for (const [id, name] of Object.entries(names)) {
    if (workflow.jobs[id]?.name !== name) throw Error(`Check obligatorio ausente: ${name}`)
    if (id !== 'required' && workflow.jobs[id].if !== undefined) throw Error('No condicionar jobs obligatorios')
  }
  if (workflow.jobs.required.if !== '${{ always() }}'
    || [...workflow.jobs.required.needs].sort().join(',') !== 'backend,openapi,security,web') throw Error('Gate final incompleto')
  for (const job of Object.values(workflow.jobs)) {
    if (job['runs-on'] !== 'ubuntu-24.04' || !job['timeout-minutes'] || job.permissions) throw Error('Job sin límites seguros')
    for (const step of job.steps ?? []) {
      if (step.uses && !/^actions\/[a-z-]+@[a-f0-9]{40}$/.test(step.uses)) throw Error('Acción sin SHA completo')
      if (step.uses?.startsWith('actions/checkout@') && step.with?.['persist-credentials'] !== false) throw Error('Credenciales persistentes')
    }
  }
}

function run(image, args, { network = false, mounts = [], environment = [], capture = false } = {}) {
  const result = spawnSync('docker', ['run', '--rm', '--cap-drop=ALL', '--security-opt=no-new-privileges',
    ...(!network ? ['--network=none'] : []), ...environment.flatMap(e => ['-e', e]), ...mounts.flatMap(m => ['-v', m]), image, ...args],
  { cwd: root, encoding: 'utf8', stdio: capture ? 'pipe' : 'inherit' })
  if (result.status !== 0) {
    // Scanner output is redacted by Gitleaks. Do not dump arbitrary source files.
    if (capture) console.error(result.stdout, result.stderr)
    throw Error(`Falló ${image.split('@')[0]} (${result.status})`)
  }
  return result.stdout
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const files = execFileSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'], { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean)
  mkdirSync(resolve(root, '.ci-work'), { recursive: true })
  const scan = mkdtempSync(resolve(root, '.ci-work/source-'))
  for (const path of files) {
    checkPath(path)
    if (/\.(java|[cm]?[jt]sx?)$/.test(path) && !path.startsWith('tools/ci/')) {
      const text = readFileSync(resolve(root, path), 'utf8')
      if (/@Disabled\b|@Ignore\b|disabledWithoutDocker\s*=\s*true|\b(?:test|it|describe)\s*\.\s*(?:skip|todo|only)\s*\(|\btest\.(?:fixme|fail)\s*\(/.test(text)) throw Error(`Prueba omitida o exclusiva: ${path}`)
    }
    const dest = resolve(scan, path)
    cpSync(resolve(root, path), dest, { recursive: true })
  }
  for (const path of files.filter(f => /^\.github\/workflows\/.*\.ya?ml$/.test(f))) {
    checkWorkflow(readFileSync(resolve(root, path), 'utf8'))
  }
  run(images.gitleaks, ['git', '/repo', '--log-opts=--all', '--redact=100', '--no-banner'], {
    mounts: [`${root}:/repo:ro`],
    // The read-only bind mount belongs to the runner UID, not container root.
    environment: ['GIT_CONFIG_COUNT=1', 'GIT_CONFIG_KEY_0=safe.directory', 'GIT_CONFIG_VALUE_0=/repo'],
  })
  run(images.gitleaks, ['dir', '/source', '--redact=100', '--no-banner'], { mounts: [`${scan}:/source:ro`] })
  run(images.actionlint, ['-color', '/repo/.github/workflows/ci.yml'], { mounts: [`${scan}:/repo:ro`], capture: true })
  run(images.zizmor, ['--offline', '--no-progress', '--format=plain', '/repo/.github/workflows'], { mounts: [`${scan}:/repo:ro`] })
  const cache = resolve(root, '.ci-work/trivy-cache')
  mkdirSync(cache, { recursive: true })
  const mounts = [`${scan}:/source:ro`, `${cache}:/cache`]
  const common = ['--cache-dir', '/cache', '--quiet']
  const privateScan = ['--offline-scan', '--disable-telemetry', '--skip-version-check']
  run(images.trivy, [...common, 'fs', ...privateScan, '--scanners=vuln', '--include-dev-deps', '--severity=HIGH,CRITICAL', '--exit-code=1', '/source'], { mounts, network: true })
  const bom = resolve(root, 'backend/target/bom.json')
  if (!existsSync(bom)) throw Error('Generar SBOM Maven incluyendo dependencias test antes de security')
  run(images.trivy, [...common, 'sbom', ...privateScan, '--severity=HIGH,CRITICAL', '--exit-code=1', '/bom.json'], { mounts: [...mounts, `${bom}:/bom.json:ro`], network: true })
  const sha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
  const dir = resolve(root, '.ci-artifacts/security')
  mkdirSync(dir, { recursive: true })
  writeFileSync(resolve(dir, 'summary.json'), JSON.stringify({ testedSha: sha, status: 'passed', images, severityGate: ['HIGH', 'CRITICAL'], exclusions: [] }, null, 2) + '\n')
}
