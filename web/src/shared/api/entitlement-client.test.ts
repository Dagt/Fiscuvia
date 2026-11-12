import { beforeEach, expect, it, vi } from 'vitest'
import { resources, tenantUsage } from './entitlement-client'
import { sessionRequest } from './session-client'
vi.mock('./session-client', () => ({ sessionRequest: vi.fn() }))
const tenant = '00000000-0000-4000-8000-000000000001'
function usage() {
  return resources.map((resource) => ({
    resource,
    limit: null,
    version: null,
    validFrom: null,
    validUntil: null,
    measured: ['USERS', 'TAXPAYERS'].includes(resource) ? 0 : null,
    reserved: ['USERS', 'TAXPAYERS'].includes(resource) ? 0 : null,
    available: null,
    overLimit: null,
    integration: ['USERS', 'TAXPAYERS'].includes(resource)
      ? 'MEASURED'
      : 'NOT_INTEGRATED',
  }))
}
beforeEach(() => vi.clearAllMocks())
it('acepta contribuyentes medidos sin límite y conserva recursos futuros sin medir', async () => {
  vi.mocked(sessionRequest).mockResolvedValue(usage())
  expect((await tenantUsage(tenant))[1]).toMatchObject({
    resource: 'TAXPAYERS',
    measured: 0,
    reserved: 0,
    integration: 'MEASURED',
  })
})
it('rechaza reservas de usuarios aplicadas a contribuyentes o números no seguros', async () => {
  for (const patch of [
    { reserved: 1 },
    { measured: -1 },
    { measured: Number.MAX_SAFE_INTEGER + 1 },
  ]) {
    const values = usage()
    Object.assign(values[1]!, patch)
    vi.mocked(sessionRequest).mockResolvedValue(values)
    await expect(tenantUsage(tenant)).rejects.toThrow()
  }
})
it('rechaza integración fiscal ausente y recursos futuros presentados como medidos', async () => {
  for (const index of [1, 2]) {
    const values = usage()
    values[index]!.integration = index === 1 ? 'NOT_INTEGRATED' : 'MEASURED'
    vi.mocked(sessionRequest).mockResolvedValue(values)
    await expect(tenantUsage(tenant)).rejects.toThrow()
  }
})
