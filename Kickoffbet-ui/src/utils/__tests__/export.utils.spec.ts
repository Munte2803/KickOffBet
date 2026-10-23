import { describe, expect, it } from 'vitest'
import { buildCsv, buildPdf, escapeCsvCell, formatExportDate, PDF_FONT, type ExportColumn } from '@/utils/export.utils'

// Tests run with TZ=Europe/Bucharest (see vitest.config.ts)

describe('escapeCsvCell', () => {
  it('quotes cells with separators, quotes or line breaks', () => {
    expect(escapeCsvCell('plain')).toBe('plain')
    expect(escapeCsvCell('a,b')).toBe('"a,b"')
    expect(escapeCsvCell('say "hi"')).toBe('"say ""hi"""')
    expect(escapeCsvCell('two\nlines')).toBe('"two\nlines"')
    expect(escapeCsvCell(null)).toBe('')
    expect(escapeCsvCell(undefined)).toBe('')
  })

  it.each(['=HYPERLINK("http://x")', '+1+1', '-2+3', '@SUM(A1)', '\tcmd', '\rcmd'])(
    'neutralises the formula-looking text %j with a leading quote',
    (value) => {
      expect(escapeCsvCell(value).replace(/^"/, '').startsWith("'")).toBe(true)
    },
  )

  it('leaves numbers alone, negative ones included', () => {
    expect(escapeCsvCell(-50)).toBe('-50')
    expect(escapeCsvCell(12.5)).toBe('12.5')
  })
})

describe('formatExportDate', () => {
  it('shows the UTC timestamps from the API in local time, with date and time', () => {
    expect(formatExportDate('2026-01-15T10:05:00')).toBe('15.01.2026, 12:05')
    expect(formatExportDate('2026-07-01T09:30:00Z')).toBe('01.07.2026, 12:30')
    expect(formatExportDate(null)).toBe('')
  })
})

interface Row {
  name: string
  createdAt: string
  amount: number
}

const columns: ExportColumn<Row>[] = [
  { header: 'Nume', accessor: (r) => r.name },
  { header: 'Creat la', accessor: (r) => formatExportDate(r.createdAt) },
  { header: 'Suma', accessor: (r) => r.amount },
]

describe('buildCsv', () => {
  it('writes a BOM, the header and one CRLF-separated line per row', () => {
    const csv = buildCsv([{ name: 'Ștefan Brașov', createdAt: '2026-01-15T10:05:00', amount: -20 }], columns)
    expect(csv).toBe('﻿Nume,Creat la,Suma\r\nȘtefan Brașov,"15.01.2026, 12:05",-20')
  })
})

describe('buildPdf', () => {
  it('embeds a Unicode font so Romanian diacritics render', async () => {
    const doc = await buildPdf([{ name: 'Ștefan Brașov', createdAt: '2026-01-15T10:05:00', amount: 20 }], columns, 'Utilizatori')

    expect(doc.getFont().fontName).toBe(PDF_FONT)
    const output = doc.output()
    expect(output).toContain('/FontFile2')
    // The ToUnicode map lists Ș (U+0218) and ș (U+0219): the glyphs exist in the embedded subset
    expect(output).toMatch(/<0218>/i)
    expect(output).toMatch(/<0219>/i)
    expect(output).toMatch(new RegExp(`/BaseFont /${PDF_FONT}`))
    // Every text run (title, header row in bold, cells) is set in the embedded font, none in Helvetica
    const fontId = (style: string) => String(doc.setFont(PDF_FONT, style).getFont().id) // e.g. "F15"
    const usedFonts = new Set([...output.matchAll(/\/(F\d+) [\d.]+ Tf/g)].map((match) => match[1]))
    expect(usedFonts).toEqual(new Set([fontId('normal'), fontId('bold')]))
  })
})
