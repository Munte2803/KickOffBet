/**
 * Romanian versions of the TICKET_PLACEMENT_ERROR messages raised by the backend
 * (services/tickets/TicketPlacementValidator.java). The server formats odds with `%.2f`, so the
 * decimal separator may be a dot or a comma.
 */
const ODDS = '(\\d+[.,]\\d+)'

const ticketPlacementMessages: Array<[RegExp, (match: RegExpMatchArray) => string]> = [
  [
    new RegExp(`^Odds changed: ${ODDS}\\s*(?:→|->)\\s*${ODDS}$`),
    (m) =>
      `Cota s-a modificat: ${m[1].replace(',', '.')} → ${m[2].replace(',', '.')}. Verifica biletul si plaseaza-l din nou.`,
  ],
  [/^Offer \S+ is no longer active$/, () => 'O cota din bilet nu mai este activa. Elimin-o si incearca din nou.'],
  [/^Match \S+ is no longer available$/, () => 'Un meci din bilet nu mai este disponibil.'],
  [/^Match \S+ has already started$/, () => 'Un meci din bilet a inceput deja. Elimina selectia si incearca din nou.'],
  [/^Duplicate offers in selections$/, () => 'Aceeasi cota apare de mai multe ori pe bilet.'],
  [/^One or more offers not found$/, () => 'Una sau mai multe cote din bilet nu mai exista.'],
  [/^Duplicate market type \S+ for match \S+$/, () => 'Biletul contine doua selectii din aceeasi piata pentru acelasi meci.'],
  [
    /^H2H and DOUBLE_CHANCE cannot be combined for match \S+$/,
    () => 'Rezultatul final si sansa dubla nu pot fi combinate pentru acelasi meci.',
  ],
]

export const TICKET_PLACEMENT_FALLBACK = 'Nu am putut plasa biletul. Verifica selectiile si incearca din nou.'

/** The specific reason a ticket was rejected, in Romanian when the backend message is a known one. */
export function translateTicketPlacementError(serverMessage: string | undefined): string {
  const message = serverMessage?.trim()
  if (!message) return TICKET_PLACEMENT_FALLBACK

  for (const [pattern, translate] of ticketPlacementMessages) {
    const match = message.match(pattern)
    if (match) return translate(match)
  }

  // An unknown (newer) validator message is still more useful than the generic text
  return `${TICKET_PLACEMENT_FALLBACK} (${message})`
}

function blobText(blob: Blob): Promise<string> {
  if (typeof blob.text === 'function') return blob.text()
  // Older engines (and jsdom) only offer FileReader
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob)
  })
}

/**
 * A request made with `responseType: 'blob'` (file previews) receives its JSON error body as a Blob;
 * this turns it back into the `{ errorCode, error }` object the error mapping expects.
 */
export async function readErrorBody(data: unknown): Promise<{ errorCode?: string; error?: string } | undefined> {
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      return JSON.parse(await blobText(data))
    } catch {
      return undefined
    }
  }
  return data && typeof data === 'object' ? (data as { errorCode?: string; error?: string }) : undefined
}
