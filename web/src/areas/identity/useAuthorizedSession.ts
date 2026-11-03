import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { ApiClientError } from '../../shared/api/client'
import {
  authorizeTenant,
  currentIdentity,
  logout,
  type Membership,
} from '../../shared/api/session-client'

/** Presentation context only. Every protected backend operation must authorize again. */
export function useAuthorizedSession() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const identity = useQuery({
    queryKey: ['current-identity'],
    queryFn: currentIdentity,
    retry: false,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
  const [selection, setSelection] = useState<{
    userId: string
    member: Membership
  } | null>(null)
  const [busy, setBusy] = useState(false)
  const [denied, setDenied] = useState(false)
  const [failed, setFailed] = useState(false)
  const epoch = useRef(0)
  const identityRef = useRef(identity.data)
  useEffect(() => {
    identityRef.current = identity.data
  }, [identity.data])
  const selected =
    selection &&
    !identity.isFetching &&
    !identity.isError &&
    !busy &&
    selection.userId === identity.data?.userId &&
    identity.data?.memberships.some((m) => sameMembership(m, selection.member))
      ? selection.member
      : null
  async function clearContext() {
    const filters = {
      predicate: (query: { queryKey: readonly unknown[] }) =>
        !['current-identity', 'auth-status', 'login-csrf'].includes(
          String(query.queryKey[0]),
        ),
    }
    // Await cancellation before any new context can become visible.
    await client.cancelQueries(filters)
    client.removeQueries(filters)
  }
  useEffect(() => {
    if (
      identity.error instanceof ApiClientError &&
      identity.error.status === 401
    ) {
      epoch.current++
      client.clear()
      void navigate('/login?estado=expirada', { replace: true })
    }
  }, [identity.error, navigate, client])
  useEffect(
    () => () => {
      epoch.current++
    },
    [],
  )
  async function choose(tenantId: string) {
    const before = identityRef.current
    if (busy || !before?.memberships.some((m) => m.tenantId === tenantId))
      return
    const ticket = ++epoch.current
    setSelection(null)
    setBusy(true)
    setDenied(false)
    setFailed(false)
    try {
      await clearContext()
      const member = await authorizeTenant(tenantId)
      const fresh = await currentIdentity()
      if (ticket !== epoch.current) return
      client.setQueryData(['current-identity'], fresh)
      if (
        fresh.userId !== before.userId ||
        !fresh.memberships.some((m) => sameMembership(m, member))
      ) {
        setDenied(true)
      } else setSelection({ userId: fresh.userId, member })
    } catch (error) {
      if (ticket !== epoch.current) return
      if (error instanceof ApiClientError && error.status === 401) {
        client.clear()
        await navigate('/login?estado=expirada', { replace: true })
      } else if (error instanceof ApiClientError && error.status === 403) {
        setDenied(true)
        void identity.refetch()
      } else setFailed(true)
    } finally {
      if (ticket === epoch.current) setBusy(false)
    }
  }
  async function change() {
    epoch.current++
    setSelection(null)
    setBusy(true)
    setDenied(false)
    setFailed(false)
    try {
      await clearContext()
      await identity.refetch()
    } finally {
      setBusy(false)
    }
    document.getElementById('contenido')?.focus()
  }
  async function exit() {
    epoch.current++
    setSelection(null)
    setBusy(true)
    try {
      await clearContext()
      await logout()
      client.clear()
      await navigate('/login', { replace: true })
      window.location.reload()
    } catch {
      setFailed(true)
      setBusy(false)
    }
  }
  return { identity, selected, busy, denied, failed, choose, change, exit }
}
function sameMembership(a: Membership, b: Membership) {
  return (
    a.tenantId === b.tenantId &&
    a.membershipId === b.membershipId &&
    a.role === b.role
  )
}
