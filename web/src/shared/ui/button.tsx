// Adapted from shadcn/ui's MIT-licensed Radix button pattern; Fiscuvia variants.
import type { ComponentProps } from 'react'
import { Slot } from '@radix-ui/react-slot'
import { LoaderCircle } from 'lucide-react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../lib/cn'

export const buttonVariants = cva('button', {
  variants: {
    variant: {
      default: 'button-primary',
      primary: 'button-primary',
      secondary: 'button-outline',
      danger: 'button-danger',
      outline: 'button-outline',
      ghost: 'button-ghost',
    },
  },
  defaultVariants: { variant: 'default' },
})

export function Button({
  className,
  variant,
  asChild = false,
  type = 'button',
  pending = false,
  iconOnly = false,
  disabled,
  children,
  onClick,
  ...props
}: ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
    pending?: boolean
    iconOnly?: boolean
  }) {
  const Component = asChild ? Slot : 'button'
  return (
    <Component
      type={asChild ? undefined : type}
      className={cn(
        buttonVariants({ variant }),
        iconOnly && 'button-icon',
        className,
      )}
      disabled={disabled || pending}
      aria-busy={pending || undefined}
      {...props}
      aria-disabled={asChild && (disabled || pending) ? true : undefined}
      tabIndex={asChild && (disabled || pending) ? -1 : props.tabIndex}
      onClick={(event) => {
        if (disabled || pending) {
          event.preventDefault()
          return
        }
        onClick?.(event)
      }}
    >
      {asChild ? (
        children
      ) : (
        <>
          {pending && (
            <LoaderCircle
              size={18}
              className="loading-icon"
              aria-hidden="true"
            />
          )}
          {children}
        </>
      )}
    </Component>
  )
}
