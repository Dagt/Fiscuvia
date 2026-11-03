import { useId, type ComponentProps } from 'react'

export function Input({ className = '', ...props }: ComponentProps<'input'>) {
  return <input className={`input ${className}`} {...props} />
}

/** Label, help and error are connected without relying on color or placeholder. */
export function Field({
  label,
  description,
  error,
  id: providedId,
  ...props
}: ComponentProps<'input'> & {
  label: string
  description?: string
  error?: string
}) {
  const generated = useId()
  const id = providedId ?? generated
  const describedBy =
    [
      props['aria-describedby'],
      description && `${id}-help`,
      error && `${id}-error`,
    ]
      .filter(Boolean)
      .join(' ') || undefined
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}
        {props.required && <span aria-hidden="true"> *</span>}
      </label>
      <Input
        {...props}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {description && (
        <p className="field-help" id={`${id}-help`}>
          {description}
        </p>
      )}
      {error && (
        <p className="field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
