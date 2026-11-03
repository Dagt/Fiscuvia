import { useSyncExternalStore } from 'react'
const query = '(max-width: 699px)'
function subscribe(callback: () => void) {
  if (!window.matchMedia) return () => {}
  const media = window.matchMedia(query)
  media.addEventListener('change', callback)
  return () => media.removeEventListener('change', callback)
}
export function useCompactLayout() {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia?.(query).matches ?? false,
    () => false,
  )
}
