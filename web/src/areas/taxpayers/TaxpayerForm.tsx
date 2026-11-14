import { useState } from 'react'
import { Plus, Trash2, ShieldCheck } from 'lucide-react'
import type {
  Taxpayer,
  CreateTaxpayer,
  Receipt,
} from '../../shared/api/taxpayer-client'
import { Field } from '../../shared/ui/Field'
import { Button } from '../../shared/ui/button'
import { useTaxpayerCommand, commandMessage } from './useTaxpayerCommand'
import {
  emptyProfile,
  newRegime,
  profileErrors,
  regimeInput,
  type RegimeDraft,
} from './validation'

export function TaxpayerForm({
  tenant,
  initial,
  onSaved,
  onUnavailable,
  onReload,
  onRetain,
}: {
  tenant: string
  initial?: Taxpayer
  onSaved: (r: Receipt) => void
  onUnavailable: (s: number) => void
  onReload?: () => void
  onRetain?: ((retain: boolean) => void) | undefined
}) {
  const [value, setValue] = useState<CreateTaxpayer>(() =>
    initial
      ? {
          personType: initial.personType,
          rfc: initial.rfc,
          legalName: initial.legalName,
          tradeName: initial.tradeName,
          regimes: [],
        }
      : emptyProfile(),
  )
  const [regimes, setRegimes] = useState<RegimeDraft[]>(
    () =>
      initial?.regimes.map((r) => ({
        ...newRegime(),
        code: r.code,
        validFrom: r.validFrom || '',
        validTo: r.validTo || '',
      })) ?? [],
  )
  const [errors, setErrors] = useState<Record<string, string>>({}),
    command = useTaxpayerCommand(tenant, onSaved, onUnavailable, onRetain)
  const locked = command.busy || command.uncertain
  function update(
    row: RegimeDraft,
    field: 'code' | 'validFrom' | 'validTo',
    v: string,
  ) {
    setRegimes((old) =>
      old.map((r) => (r.key === row.key ? { ...r, [field]: v } : r)),
    )
  }
  return (
    <form
      className="taxpayer-form"
      autoComplete="off"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (command.uncertain) {
          void command.run()
          return
        }
        const validation = profileErrors(value, regimes, !initial)
        setErrors(validation)
        if (Object.keys(validation).length) {
          const form = event.currentTarget
          requestAnimationFrame(() =>
            form.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
          )
          return
        }
        const body = {
          legalName: value.legalName,
          tradeName: value.tradeName,
          regimes: regimeInput(regimes),
        }
        void command.run(
          initial
            ? {
                action: 'edit',
                id: initial.id,
                body: { ...body, expectedVersion: initial.version },
              }
            : {
                action: 'create',
                body: { ...body, rfc: value.rfc, personType: value.personType },
              },
        )
      }}
    >
      <fieldset disabled={locked}>
        <legend>Identificación del contribuyente</legend>
        <div className="taxpayer-field-grid">
          <div className="field">
            <label htmlFor="taxpayer-person">Tipo de persona</label>
            <select
              id="taxpayer-person"
              className="input"
              disabled={!!initial}
              value={value.personType}
              onChange={(e) =>
                setValue({
                  ...value,
                  personType: e.target.value as CreateTaxpayer['personType'],
                })
              }
            >
              <option value="LEGAL_ENTITY">Persona moral</option>
              <option value="INDIVIDUAL">Persona física</option>
            </select>
          </div>
          <Field
            label="RFC"
            value={value.rfc}
            required
            maxLength={32}
            readOnly={!!initial}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            error={errors.rfc}
            onChange={(e) => setValue({ ...value, rfc: e.target.value })}
          />
        </div>
        <Field
          label="Nombre o razón social"
          required
          value={value.legalName}
          maxLength={600}
          autoComplete="off"
          error={errors.legalName}
          onChange={(e) => setValue({ ...value, legalName: e.target.value })}
        />
        <Field
          label="Nombre comercial"
          description="Opcional. Se envía como dato desconocido si lo dejas vacío."
          value={value.tradeName ?? ''}
          autoComplete="off"
          error={errors.tradeName}
          onChange={(e) =>
            setValue({ ...value, tradeName: e.target.value || null })
          }
        />
        <p className="taxpayer-disclaimer">
          <ShieldCheck size={18} aria-hidden="true" />
          La validación del RFC es local y no confirma su inscripción ante el
          SAT.
        </p>
        {initial && (
          <p className="field-help">
            El RFC y el tipo de persona se conservan. Su corrección requiere
            otro procedimiento autorizado.
          </p>
        )}
      </fieldset>
      <fieldset disabled={locked}>
        <legend>Referencias manuales de régimen</legend>
        <p className="field-help">
          Información capturada manualmente, pendiente de revisión. Hasta diez
          referencias; deja ambas fechas vacías si su vigencia es desconocida.
        </p>
        {regimes.length === 0 && (
          <p className="taxpayer-no-regimes">
            Sin referencias registradas. Puedes continuar sin agregar una.
          </p>
        )}
        {regimes.map((row, i) => (
          <div className="taxpayer-regime-editor" key={row.key}>
            <div className="taxpayer-regime-heading">
              <strong>Referencia {i + 1}</strong>
              <Button
                type="button"
                variant="ghost"
                iconOnly
                aria-label={`Eliminar referencia ${i + 1}`}
                onClick={() =>
                  setRegimes((old) => old.filter((r) => r.key !== row.key))
                }
              >
                <Trash2 size={18} aria-hidden="true" />
              </Button>
            </div>
            <div className="taxpayer-regime-fields">
              <Field
                label={`Código de régimen ${i + 1}`}
                inputMode="numeric"
                maxLength={3}
                value={row.code}
                error={errors[`${row.key}-code`]}
                onChange={(e) => update(row, 'code', e.target.value)}
              />
              <Field
                label={`Inicio ${i + 1}`}
                type="date"
                value={row.validFrom}
                error={errors[`${row.key}-from`]}
                onChange={(e) => update(row, 'validFrom', e.target.value)}
              />
              <Field
                label={`Término ${i + 1}`}
                description="Fecha exclusiva: este día no forma parte de la vigencia."
                type="date"
                value={row.validTo}
                error={errors[`${row.key}-to`]}
                onChange={(e) => update(row, 'validTo', e.target.value)}
              />
            </div>
          </div>
        ))}
        {errors.regimes && (
          <p className="field-error" role="alert">
            {errors.regimes}
          </p>
        )}
        <Button
          type="button"
          variant="secondary"
          disabled={regimes.length >= 10}
          onClick={() => setRegimes((old) => [...old, newRegime()])}
        >
          <Plus size={18} aria-hidden="true" />
          Agregar referencia
        </Button>
      </fieldset>
      {command.error && (
        <div className="taxpayer-command-notice" role="alert">
          <p>{commandMessage(command.error, !initial)}</p>
          {command.error.code === 'STALE_VERSION' && onReload && (
            <Button type="button" variant="secondary" onClick={onReload}>
              Actualizar datos para revisar
            </Button>
          )}
        </div>
      )}
      <div className="taxpayer-form-actions">
        <Button type="submit" pending={command.busy}>
          {command.uncertain
            ? 'Recuperar resultado'
            : initial
              ? 'Guardar cambios'
              : 'Registrar contribuyente'}
        </Button>
      </div>
    </form>
  )
}
