import { useCallback, useSyncExternalStore } from 'react'
export function useCompactLayout(below = 699) {
  const query = `(max-width: ${below}px)`
  const subscribe = useCallback(
    (callback: () => void) => {
      if (!window.matchMedia) return () => {}
      const media = window.matchMedia(query)
      media.addEventListener('change', callback)
      return () => media.removeEventListener('change', callback)
    },
    [query],
  )
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia?.(query).matches ?? false,
    () => false,
  )
}
