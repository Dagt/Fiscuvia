import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { parse } from 'yaml'
import openapiTS, { astToString } from 'openapi-typescript'
import Ajv2020 from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import { format } from 'prettier'
import { checkLocalRefs } from './openapi.mjs'

const root = fileURLToPath(new URL('../../', import.meta.url))
const contractUrl = new URL('../../docs/api/openapi-v1.yaml', import.meta.url)
export const contract = parse(readFileSync(contractUrl, 'utf8'))
checkLocalRefs(contract)

export function validator(name) {
  const schemas = JSON.parse(JSON.stringify(contract.components.schemas).replaceAll('#/components/schemas/', '#/$defs/'))
  const ajv = new Ajv2020({ strict: true, allErrors: true })
  // OpenAPI 3.1 Schema Object annotation, not a validation rule. Keep all other strict checks.
  ajv.addKeyword({ keyword: 'example', valid: true })
  addFormats(ajv)
  return ajv.compile({ $defs: schemas, $ref: `#/$defs/${name}` })
}

export function checkHttpResponses(responses) {
  const info = validator('PlatformInfo')
  const problem = validator('ProblemDetails')
  const seen = new Set()
  for (const response of responses) {
    const check = response.status === 200 ? info : problem
    if (!check(response.body)) throw Error(`Contrato HTTP inválido: ${response.scenario}: ${JSON.stringify(check.errors)}`)
    if (response.status === 200 && response.contentType !== 'application/json') throw Error('Content-Type de éxito inválido')
    if (response.status !== 200) {
      if (response.contentType !== 'application/problem+json' || response.status !== response.body.status
        || contract['x-error-statuses'][response.body.code] !== response.status
        || response.body.type !== `urn:fiscuvia:problem:${response.body.code.toLowerCase().replaceAll('_', '-')}`
        || response.body.traceId !== response.correlationId || response.body.instance !== `urn:uuid:${response.correlationId}`) throw Error('Metadatos Problem Details inconsistentes')
      seen.add(response.body.code)
    }
  }
  if (!responses.some(r => r.status === 200)) throw Error('Falta evidencia HTTP pública')
  for (const code of contract.components.schemas.ProblemDetails.properties.code.enum) {
    if (!seen.has(code)) throw Error(`Falta evidencia HTTP del código ${code}`)
  }
  return responses.length
}

export async function generatedTypes(write = false) {
  const options = {
    ...JSON.parse(readFileSync(new URL('../../web/.prettierrc.json', import.meta.url), 'utf8')),
    parser: 'typescript',
  }
  const info = Object.fromEntries(Object.entries(contract.components.schemas.PlatformInfo.properties).map(([key, value]) => [key, value.const]))
  const outputs = {
    'schema.d.ts': astToString(await openapiTS(contractUrl, { alphabetize: true })),
    'contract-metadata.ts': `// Generado desde docs/api/openapi-v1.yaml. No editar manualmente.\nimport type {components} from './schema'\nexport const PLATFORM_INFO = ${JSON.stringify(info)} as const satisfies components['schemas']['PlatformInfo']\nexport const ERROR_STATUS = ${JSON.stringify(contract['x-error-statuses'])} as const satisfies Record<components['schemas']['ProblemDetails']['code'], number>\n`,
  }
  for (const [name, text] of Object.entries(outputs)) {
    const result = await format(text, options)
    const path = new URL(`../../web/src/shared/api/${name}`, import.meta.url)
    if (write) writeFileSync(path, result)
    else if (readFileSync(path, 'utf8').replaceAll('\r\n', '\n') !== result) throw Error('Tipos OpenAPI desactualizados: npm run generate:api --prefix tools/ci')
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.includes('--generate')) await generatedTypes(true)
  else {
    await generatedTypes()
    const i = process.argv.indexOf('--responses')
    if (i >= 0) {
      const count = checkHttpResponses(JSON.parse(readFileSync(resolve(root, process.argv[i + 1]), 'utf8')))
      console.log(`Contrato: ${count} respuestas HTTP reales válidas`)
    }
  }
}
