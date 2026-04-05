export function getTeamTag(name: string, tla?: string | null): string {
  if (tla?.trim()) return tla.trim().toUpperCase()

  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 3)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}
