import { spawn, spawnSync } from 'node:child_process'
import { openSync, closeSync, existsSync, readFileSync, mkdirSync, rmSync } from 'node:fs'
import { resolve, delimiter } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as pause } from 'node:timers/promises'

const root = fileURLToPath(new URL('../../', import.meta.url))
const backend = resolve(root, 'backend')
const work = resolve(root, '.ci-work')
mkdirSync(work, { recursive: true })
const config = resolve(work, '27-browser.json')
const log = openSync(resolve(work, '27-browser-backend.log'), 'w', 0o600)
const windows = process.platform === 'win32'
const run = (command, args, options = {}) => spawn(command, args, { windowsHide: true, ...options })
const wait = child => new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', code => code === 0 ? resolve() : reject(Error(`Subprocess failed (${code}); inspect the private local log`))) })
// No suite is skipped: this phase compiles the test-only harness. clean verify remains a separate required gate.
const compile = windows
  ? run('cmd.exe', ['/d', '/c', 'mvnw.cmd', '-B', '-ntp', 'test-compile', 'org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath', '-Dmdep.outputFile=target/test-classpath.txt'], { cwd: backend, stdio: ['ignore', log, log] })
  : run('./mvnw', ['-B', '-ntp', 'test-compile', 'org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath', '-Dmdep.outputFile=target/test-classpath.txt'], { cwd: backend, stdio: ['ignore', log, log] })
await wait(compile)
rmSync(config, { force: true })
const cp = [resolve(backend, 'target/test-classes'), resolve(backend, 'target/classes'), readFileSync(resolve(backend, 'target/test-classpath.txt'), 'utf8').trim()].join(delimiter)
const probe = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8', windowsHide: true })
const javaHome = probe.stderr?.match(/^\s*java.home = (.+)$/m)?.[1]?.trim()
if (!javaHome) throw Error('Cannot resolve the effective JDK; no PATH shim may own the harness lifecycle')
const java = resolve(javaHome, 'bin', windows ? 'java.exe' : 'java')
const harness = run(java, ['-cp', cp, 'com.fiscuvia.LocalOidcHarness', config], { cwd: backend, stdio: ['pipe', log, log] })
let browser
async function cleanup() {
  if (browser?.exitCode === null) browser.kill()
  if (harness.exitCode === null) {
    harness.stdin.end('stop\n')
    const exited = new Promise(resolve => harness.once('exit', resolve))
    await Promise.race([exited, pause(30000)])
    if (harness.exitCode === null) { harness.kill(); await exited }
  }
  rmSync(config, { force: true }); closeSync(log)
}
process.once('SIGINT', () => { void cleanup().then(() => process.exit(130)) })
process.once('SIGTERM', () => { void cleanup().then(() => process.exit(143)) })
try {
  const deadline = Date.now() + 180000
  while (!existsSync(config)) {
    if (harness.exitCode !== null || Date.now() > deadline) throw Error('Local OIDC harness failed; inspect .ci-work/27-browser-backend.log')
    await pause(300)
  }
  const serve = process.argv.includes('--serve')
  const referenceAudit = process.argv.includes('--reference-audit')
  const designAudit = process.argv.includes('--design-system-audit')
  if (serve) console.log('Entorno local real: https://127.0.0.1:4174. Credenciales sintéticas privadas en .ci-work/27-browser.json; no compartir ese archivo. Ctrl+C limpia los recursos.')
  browser = run(process.execPath, serve ? ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '4174', '--strictPort'] : designAudit ? [resolve(root, 'tools/ux/capture-design-system.mjs')] : referenceAudit ? [resolve(root, 'tools/ux/capture-reference.mjs')] : ['node_modules/@playwright/test/cli.js', 'test', '--config', 'playwright.auth.config.ts', '--reporter=line,json'], { cwd: resolve(root, 'web'), env: { ...process.env, FISCUVIA_AUTH_HARNESS_FILE: config, PLAYWRIGHT_JSON_OUTPUT_FILE: resolve(work, 'playwright-auth.json') }, stdio: 'inherit' })
  await wait(browser)
} finally { await cleanup() }
