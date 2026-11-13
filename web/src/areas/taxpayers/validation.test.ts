import { expect, it } from 'vitest'
import {
  emptyProfile,
  newRegime,
  profileErrors,
  regimeInput,
  validRfc,
} from './validation'

it.each(['DEM000101001', '  dem000229ab1  ', 'D&M000101001', 'DEÑ000101001'])(
  'acepta estructura moral local: %s',
  (rfc) => expect(validRfc(rfc, 'LEGAL_ENTITY')).toBe(true),
)
it.each(['DEMO000229AB1', 'Ñ&MO000101001'])(
  'acepta estructura física local: %s',
  (rfc) => expect(validRfc(rfc, 'INDIVIDUAL')).toBe(true),
)
it.each([
  'DEM000230001',
  'DEM990229001',
  'DEM000431001',
  'DEM00010100',
  'DEMO000101001',
  'DÉM000101001',
  'DEM00010100ß',
  'DEM00010100１',
  '\tDEM000101001',
  'DEM000101001\n',
])('rechaza estructura moral inválida: %s', (rfc) =>
  expect(validRfc(rfc, 'LEGAL_ENTITY')).toBe(false),
)
it.each(['XAXX010101000', 'XEXX010101000'])(
  'no acepta RFC genérico como cliente individual: %s',
  (rfc) => expect(validRfc(rfc, 'INDIVIDUAL')).toBe(false),
)
const profile = {
  ...emptyProfile(),
  rfc: 'DEM000101001',
  legalName: 'Cliente DEMO',
}
it('conserva NULL explícito y cero referencias sin afirmar una validación SAT', () => {
  expect(profileErrors(profile, [], true)).toEqual({})
  expect(regimeInput([{ ...newRegime(), code: '000' }])).toEqual([
    { code: '000', validFrom: null, validTo: null },
  ])
})
it('valida límites, controles y nombre obligatorio', () => {
  expect(
    profileErrors(
      { ...profile, legalName: ' ', tradeName: 'x'.repeat(201) },
      [],
      true,
    ),
  ).toHaveProperty('legalName')
  expect(
    profileErrors({ ...profile, legalName: 'x'.repeat(301) }, [], false),
  ).toHaveProperty('legalName')
  expect(
    profileErrors({ ...profile, tradeName: '\u007f' }, [], false),
  ).toHaveProperty('tradeName')
})
it('detecta códigos, fechas imposibles y término sin inicio', () => {
  const r = {
    ...newRegime(),
    code: '01',
    validFrom: '2026-02-30',
    validTo: '2026-01-01',
  }
  const errors = profileErrors(profile, [r], true)
  expect(errors).toHaveProperty(`${r.key}-code`)
  expect(errors).toHaveProperty(`${r.key}-from`)
  expect(errors).toHaveProperty(`${r.key}-to`)
  expect(
    profileErrors(profile, [{ ...r, validFrom: '' }], true),
  ).toHaveProperty(`${r.key}-to`)
})
it('usa intervalos semiabiertos, impide duplicados/solapamiento y limita a diez', () => {
  const a = {
    ...newRegime(),
    code: '000',
    validFrom: '2026-01-01',
    validTo: '2026-02-01',
  }
  const b = {
    ...newRegime(),
    code: '000',
    validFrom: '2026-02-01',
    validTo: '',
  }
  expect(profileErrors(profile, [a, b], true)).toEqual({})
  expect(
    profileErrors(profile, [a, { ...b, validFrom: '2026-01-20' }], true),
  ).toHaveProperty('regimes')
  expect(
    profileErrors(profile, [a, { ...b, validFrom: '' }], true),
  ).toHaveProperty('regimes')
  expect(
    profileErrors(
      profile,
      Array.from({ length: 11 }, () => newRegime()),
      true,
    ),
  ).toHaveProperty('regimes')
})
