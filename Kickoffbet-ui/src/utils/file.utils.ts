import type { AxiosResponse } from 'axios'

export interface RemoteFile {
  blob: Blob
  fileName: string | null
  contentType: string | null
}

export function getFilenameFromDisposition(disposition?: string | null): string | null {
  if (!disposition) return null

  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }

  const fileNameMatch = disposition.match(/filename="?([^"]+)"?/i)
  return fileNameMatch?.[1] ?? null
}

export function toRemoteFile(response: AxiosResponse<Blob>): RemoteFile {
  const contentType = response.headers['content-type'] ?? response.data.type ?? null
  const fileName = getFilenameFromDisposition(response.headers['content-disposition'])
  const blob = contentType && response.data.type !== contentType
    ? response.data.slice(0, response.data.size, contentType)
    : response.data

  return {
    blob,
    fileName,
    contentType,
  }
}

export function downloadRemoteFile(file: RemoteFile, fallbackName = 'document') {
  const url = URL.createObjectURL(file.blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName ?? fallbackName
  link.click()
  URL.revokeObjectURL(url)
}
