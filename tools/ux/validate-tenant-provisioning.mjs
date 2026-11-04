// Evidence provenance/dimensions/links, not a pixel-diff gate or a security authorization.
import { readFileSync,readdirSync,existsSync } from 'node:fs'
import { resolve,dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
const root=fileURLToPath(new URL('../../',import.meta.url))
const dir=resolve(root,'docs/ux/evidence/issue-31')
const manifest=JSON.parse(readFileSync(resolve(dir,'capture-manifest.json'),'utf8'))
const git=args=>execFileSync('git',args,{cwd:root,encoding:'utf8'}).trim()
const digest=bytes=>createHash('sha256').update(bytes).digest('hex')
for(const [field,path]of[['applicationTree','web'],['backendTree','backend/src/main']]){
 assert.equal(manifest[field],git(['rev-parse',`${manifest.captureSourceCommit}:${path}`]))
 assert.equal(manifest[field],git(['rev-parse',`HEAD:${path}`]),'Current implementation differs from capture source')
}
assert.equal(manifest.fixtureTree,git(['rev-parse',`${manifest.captureSourceCommit}:backend/src/test`]))
// Other test assertions may evolve after capture; every fixture actually used for capture must match.
for(const file of ['LocalOidcHarness.java','PostgresTestDatabase.java','KeycloakTestProvider.java','LocalTls.java']){
 const path=`backend/src/test/java/com/fiscuvia/${file}`
 assert.equal(digest(readFileSync(resolve(root,path))),digest(execFileSync('git',['show',`${manifest.captureSourceCommit}:${path}`],{cwd:root})))
}
assert.equal(manifest.captureScriptSha256,digest(readFileSync(resolve(root,'tools/ux/capture-tenant-provisioning.mjs'))))
assert.equal(manifest.harnessRunnerSha256,digest(readFileSync(resolve(root,'tools/ci/auth-browser.mjs'))))
assert.equal(manifest.screenshots.length,28);assert.equal(manifest.checks.length,6);assert(manifest.checks.every(c=>c.passed))
for(const shot of manifest.screenshots){
 assert(/^[a-z-]+-(1440|1280|768|390)\.png$/.test(shot.file));const bytes=readFileSync(resolve(dir,shot.file))
 assert.equal(digest(bytes),shot.sha256);assert.equal(bytes.readUInt32BE(16),shot.width);assert.equal(bytes.readUInt32BE(20),shot.height)
 assert([[1440,900],[1280,800],[768,1024],[390,844]].some(([w,h])=>shot.width===w&&shot.height===h))
 assert.equal(shot.axeViolations,0);assert.equal(shot.horizontalOverflow,false)
}
assert.equal(readdirSync(dir).filter(f=>f.endsWith('.png')).length,28)
let links=0
for(const path of ['docs/architecture/TENANT-PROVISIONING.md','docs/ux/evidence/issue-31/README.md','backend/VALIDATION-31.md']){
 for(const match of readFileSync(resolve(root,path),'utf8').matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)){
  const href=match[1].split('#')[0];if(!href||/^https?:|mailto:/.test(href))continue
  assert(existsSync(resolve(dirname(resolve(root,path)),decodeURIComponent(href))),`Broken local link: ${path} ${href}`);links++
 }
}
console.log(JSON.stringify({testedHead:git(['rev-parse','HEAD']),captureSource:manifest.captureSourceCommit,sameUiAndBackend:true,
 screenshots:28,checks:6,validLocalLinks:links,chromium:manifest.chromium,limitation:'Synthetic local policy; not approval of permanent permissions, production OIDC, logo storage or full WCAG certification'}))
