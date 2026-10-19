import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'

const pdfMocks = vi.hoisted(() => ({
  instances: [] as Array<Record<string, ReturnType<typeof vi.fn>>>,
  autoTable: vi.fn(),
}))

vi.mock('jspdf', () => ({
  default: vi.fn(function (this: Record<string, unknown>) {
    const instance = {
      setFontSize: vi.fn(),
      setTextColor: vi.fn(),
      text: vi.fn(),
      save: vi.fn(),
    }
    pdfMocks.instances.push(instance)
    return instance
  }),
}))

vi.mock('jspdf-autotable', () => ({ default: pdfMocks.autoTable }))

import { downloadRemoteFile, getFilenameFromDisposition, toRemoteFile } from '@/utils/file.utils'
import { exportToCsv, exportToPdf, type ExportColumn } from '@/utils/export.utils'

let createdUrls: Blob[]
let clickedLinks: HTMLAnchorElement[]

beforeEach(() => {
  createdUrls = []
  clickedLinks = []
  pdfMocks.instances.length = 0
  pdfMocks.autoTable.mockReset()
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn((blob: Blob) => {
      createdUrls.push(blob)
      return 'blob:mock'
    }),
    revokeObjectURL: vi.fn(),
  })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    clickedLinks.push(this)
  })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('getFilenameFromDisposition', () => {
  it('returns null when the header is missing', () => {
    expect(getFilenameFromDisposition(undefined)).toBeNull()
    expect(getFilenameFromDisposition(null)).toBeNull()
    expect(getFilenameFromDisposition('')).toBeNull()
  })

  it('parses quoted and unquoted filenames', () => {
    expect(getFilenameFromDisposition('attachment; filename="id-card.png"')).toBe('id-card.png')
    expect(getFilenameFromDisposition('inline; filename=report.pdf')).toBe('report.pdf')
  })

  it('prefers the RFC 5987 filename* parameter and decodes it', () => {
    expect(
      getFilenameFromDisposition(`attachment; filename="fallback.png"; filename*=UTF-8''buletin%20%C8%98tefan.png`),
    ).toBe('buletin Ștefan.png')
  })
})

describe('toRemoteFile', () => {
  const response = (data: Blob, headers: Record<string, string>) =>
    ({ data, headers }) as unknown as AxiosResponse<Blob>

  it('uses the response headers for content type and filename', () => {
    const blob = new Blob(['x'], { type: 'image/png' })
    const file = toRemoteFile(response(blob, { 'content-type': 'image/png', 'content-disposition': 'attachment; filename="a.png"' }))
    expect(file).toEqual({ blob, fileName: 'a.png', contentType: 'image/png' })
  })

  it('re-types the blob when the header content type differs from the blob type', () => {
    const blob = new Blob(['%PDF'], { type: '' })
    const file = toRemoteFile(response(blob, { 'content-type': 'application/pdf' }))
    expect(file.contentType).toBe('application/pdf')
    expect(file.blob.type).toBe('application/pdf')
    expect(file.blob.size).toBe(blob.size)
    expect(file.fileName).toBeNull()
  })

  it('falls back to the blob type when the header is absent', () => {
    const blob = new Blob(['x'], { type: 'image/jpeg' })
    const file = toRemoteFile(response(blob, {}))
    expect(file.contentType).toBe('image/jpeg')
    expect(file.blob).toBe(blob)
  })
})

describe('downloadRemoteFile', () => {
  it('clicks a temporary link using the file name, or the fallback name', () => {
    const blob = new Blob(['x'])
    downloadRemoteFile({ blob, fileName: 'id.png', contentType: 'image/png' })
    downloadRemoteFile({ blob, fileName: null, contentType: null }, 'buletin')

    expect(clickedLinks.map((link) => link.download)).toEqual(['id.png', 'buletin'])
    expect(clickedLinks[0].href).toBe('blob:mock')
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
  })
})

describe('exportToCsv', () => {
  interface Row { name: string; amount: number | null; note?: string }
  const columns: ExportColumn<Row>[] = [
    { header: 'Nume', accessor: (row) => row.name },
    { header: 'Suma', accessor: (row) => row.amount },
    { header: 'Nota', accessor: (row) => row.note },
  ]

  it('writes a BOM-prefixed CRLF CSV with escaped cells', async () => {
    exportToCsv<Row>(
      [
        { name: 'Ana', amount: 10.5, note: 'simplu' },
        { name: 'Pop, Ion', amount: null, note: 'a "citat"' },
        { name: 'multi\nline', amount: 0 },
      ],
      columns,
      'raport.csv',
    )

    expect(createdUrls).toHaveLength(1)
    const blob = createdUrls[0]
    expect(blob.type).toBe('text/csv;charset=utf-8')
    // jsdom's Blob has no arrayBuffer(); read it through FileReader instead.
    const buffer = await new Promise<ArrayBuffer>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as ArrayBuffer)
      reader.onerror = () => reject(reader.error)
      reader.readAsArrayBuffer(blob)
    })
    const bytes = new Uint8Array(buffer)
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf])
    const text = new TextDecoder().decode(bytes.slice(3))
    expect(text).toBe(
      ['Nume,Suma,Nota', 'Ana,10.5,simplu', '"Pop, Ion",,"a ""citat"""', '"multi\nline",0,'].join('\r\n'),
    )
    expect(clickedLinks[0].download).toBe('raport.csv')
  })

  it('appends a timestamp and extension when the filename has no .csv suffix', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 1, 3, 4, 5))
    exportToCsv<Row>([], columns, 'tranzactii')
    expect(clickedLinks[0].download).toBe('tranzactii_20260203_0405.csv')
  })
})

describe('exportToPdf', () => {
  it('renders a landscape table with headers and stringified cells, then saves', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 1, 3, 4, 5))

    exportToPdf(
      [{ a: 'x', b: 1 }, { a: null, b: undefined }],
      [
        { header: 'A', accessor: (row: { a: string | null; b?: number }) => row.a },
        { header: 'B', accessor: (row: { a: string | null; b?: number }) => row.b },
      ],
      'bilete',
      'Bilete',
    )

    expect(pdfMocks.instances).toHaveLength(1)
    const doc = pdfMocks.instances[0]
    expect(doc.text).toHaveBeenCalledWith('Bilete', 14, 15)
    expect(pdfMocks.autoTable).toHaveBeenCalledTimes(1)
    const options = pdfMocks.autoTable.mock.calls[0][1]
    expect(options.head).toEqual([['A', 'B']])
    expect(options.body).toEqual([['x', '1'], ['', '']])
    expect(doc.save).toHaveBeenCalledWith('bilete_20260203_0405.pdf')
  })

  it('keeps an explicit .pdf filename', () => {
    exportToPdf([], [], 'gata.pdf', 'T')
    expect(pdfMocks.instances[0].save).toHaveBeenCalledWith('gata.pdf')
  })
})
