import type {
  CreateTaxpayer,
  RegimeInput,
} from '../../shared/api/taxpayer-client'
export type RegimeDraft = {
  key: string
  code: string
  validFrom: string
  validTo: string
}
export const emptyProfile = (): CreateTaxpayer => ({
  personType: 'LEGAL_ENTITY',
  rfc: '',
  legalName: '',
  tradeName: null,
  regimes: [],
})
export const newRegime = (): RegimeDraft => ({
  key: crypto.randomUUID(),
  code: '',
  validFrom: '',
  validTo: '',
})
export function validDate(value: string) {
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.startsWith('0000'))
    return false
  const d = new Date(`${value}T00:00:00Z`)
  return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === value
}
export function validRfc(input: string, person: CreateTaxpayer['personType']) {
  const normalized = input.replace(/^ +| +$/g, '').normalize('NFC')
  if (!/^[A-Za-zÑñ&0-9]+$/.test(normalized)) return false
  const rfc = normalized.toLocaleUpperCase('en-US'),
    prefix = person === 'INDIVIDUAL' ? 4 : 3
  if (
    !new RegExp(`^[A-ZÑ&]{${prefix}}[0-9]{6}[A-Z0-9]{3}$`).test(rfc) ||
    ['XAXX010101000', 'XEXX010101000'].includes(rfc)
  )
    return false
  return validDate(
    `20${rfc.slice(prefix, prefix + 2)}-${rfc.slice(prefix + 2, prefix + 4)}-${rfc.slice(prefix + 4, prefix + 6)}`,
  )
}
export function profileErrors(
  value: CreateTaxpayer,
  rows: RegimeDraft[],
  creating: boolean,
) {
  const errors: Record<string, string> = {}
  if (creating && !validRfc(value.rfc, value.personType))
    errors.rfc =
      'Revisa longitud, caracteres y fecha del RFC para el tipo de persona elegido.'
  const controls = (s: string) =>
    Array.from(s).some((c) => {
      const n = c.codePointAt(0)!
      return n < 32 || (n >= 127 && n <= 159)
    })
  if (
    !value.legalName.trim() ||
    [...value.legalName.trim().normalize('NFC')].length > 300 ||
    controls(value.legalName)
  )
    errors.legalName =
      'Escribe un nombre o razón social de 1 a 300 caracteres, sin controles.'
  if (
    value.tradeName !== null &&
    (!value.tradeName.trim() ||
      [...value.tradeName.trim().normalize('NFC')].length > 200 ||
      controls(value.tradeName))
  )
    errors.tradeName = 'Usa hasta 200 caracteres o deja el campo vacío.'
  if (rows.length > 10)
    errors.regimes = 'Puedes registrar hasta diez referencias.'
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]!
    if (!/^[0-9]{3}$/.test(row.code))
      errors[`${row.key}-code`] = 'Usa exactamente tres dígitos.'
    if (row.validFrom && !validDate(row.validFrom))
      errors[`${row.key}-from`] = 'Revisa la fecha de inicio.'
    if (
      row.validTo &&
      (!validDate(row.validTo) ||
        !row.validFrom ||
        row.validTo <= row.validFrom)
    )
      errors[`${row.key}-to`] =
        'El término requiere un inicio y debe ser posterior.'
    for (let j = 0; j < i; j++) {
      const prior = rows[j]!
      if (
        row.code === prior.code &&
        (!row.validFrom ||
          !prior.validFrom ||
          ((!row.validTo || prior.validFrom < row.validTo) &&
            (!prior.validTo || row.validFrom < prior.validTo)))
      )
        errors.regimes =
          'Hay referencias duplicadas o períodos que se solapan para el mismo código.'
    }
  }
  return errors
}
export function regimeInput(rows: RegimeDraft[]): RegimeInput[] {
  return rows.map((r) => ({
    code: r.code,
    validFrom: r.validFrom || null,
    validTo: r.validTo || null,
  }))
}
