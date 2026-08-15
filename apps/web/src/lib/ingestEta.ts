export function isInFlight(status: string) {
  return status === 'UPLOADED' || status === 'PROCESSING' || status === 'EMBEDDING'
}

export function formatEta(
  seconds: number | null | undefined,
  style: 'long' | 'short' = 'long',
): string | null {
  if (seconds == null || seconds < 0) return null
  if (seconds >= 90) {
    const min = Math.round(seconds / 60)
    return style === 'short' ? `~${min} min` : `About ${min} min left`
  }
  if (seconds >= 30) {
    return style === 'short' ? '~1 min' : 'About 1 min left'
  }
  return style === 'short' ? '<30 sec' : 'Less than 30 sec left'
}
