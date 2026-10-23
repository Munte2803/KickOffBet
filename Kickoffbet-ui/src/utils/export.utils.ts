import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import { formatDateShort } from '@/utils/date.utils'

export interface ExportColumn<T> {
  header: string
  accessor: (row: T) => string | number | null | undefined
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** Exported dates: local time, with date and time ("01.07.2026, 12:30"), like the rest of the UI. */
export function formatExportDate(iso: string | null | undefined): string {
  return iso ? formatDateShort(iso) : ''
}

// A text cell starting with one of these is run as a formula by spreadsheet apps (CSV injection)
const FORMULA_PREFIX = /^[=+\-@\t\r]/

export function escapeCsvCell(value: unknown): string {
  if (value === null || value === undefined) return ''
  let str = String(value)
  // Only text can carry a formula; numbers (e.g. a negative amount) stay numeric
  if (typeof value === 'string' && FORMULA_PREFIX.test(str)) {
    str = `'${str}`
  }
  if (str.includes('"') || str.includes(',') || str.includes('\n') || str.includes('\r')) {
    return `"${str.replace(/"/g, '""')}"`
  }
  return str
}

function timestamp(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}`
}

export function buildCsv<T>(rows: T[], columns: ExportColumn<T>[]): string {
  const headerLine = columns.map((c) => escapeCsvCell(c.header)).join(',')
  const dataLines = rows.map((row) =>
    columns.map((c) => escapeCsvCell(c.accessor(row))).join(','),
  )
  return '﻿' + [headerLine, ...dataLines].join('\r\n')
}

export function exportToCsv<T>(rows: T[], columns: ExportColumn<T>[], filename: string): void {
  const blob = new Blob([buildCsv(rows, columns)], { type: 'text/csv;charset=utf-8' })
  const finalName = filename.endsWith('.csv') ? filename : `${filename}_${timestamp()}.csv`
  downloadBlob(blob, finalName)
}

export const PDF_FONT = 'DejaVuSans'
let pdfFontsPromise: Promise<{ regular: string; bold: string }> | null = null

function dataUrlToBase64(dataUrl: string): string {
  return dataUrl.slice(dataUrl.indexOf(',') + 1)
}

/**
 * jsPDF's built-in Helvetica only covers Latin-1, so names such as "Ștefan Brașov" came out garbled.
 * The PDF embeds a DejaVu Sans subset (Latin, Latin Extended-A, ș/ț) instead, loaded only when a PDF
 * is exported.
 */
function loadPdfFonts() {
  pdfFontsPromise ??= Promise.all([
    import('@/assets/fonts/DejaVuSans-subset.ttf?inline'),
    import('@/assets/fonts/DejaVuSans-Bold-subset.ttf?inline'),
  ])
    .then(([regular, bold]) => ({
      regular: dataUrlToBase64(regular.default),
      bold: dataUrlToBase64(bold.default),
    }))
    .catch((error) => {
      pdfFontsPromise = null
      throw error
    })
  return pdfFontsPromise
}

export async function buildPdf<T>(rows: T[], columns: ExportColumn<T>[], title: string): Promise<jsPDF> {
  const fonts = await loadPdfFonts()
  const doc = new jsPDF({ orientation: 'landscape' })
  doc.addFileToVFS(`${PDF_FONT}.ttf`, fonts.regular)
  doc.addFont(`${PDF_FONT}.ttf`, PDF_FONT, 'normal')
  doc.addFileToVFS(`${PDF_FONT}-Bold.ttf`, fonts.bold)
  doc.addFont(`${PDF_FONT}-Bold.ttf`, PDF_FONT, 'bold')
  doc.setFont(PDF_FONT, 'normal')

  doc.setFontSize(14)
  doc.text(title, 14, 15)
  doc.setFontSize(9)
  doc.setTextColor(120)
  doc.text(`Generat: ${formatExportDate(new Date().toISOString())}  ·  ${rows.length} randuri`, 14, 21)
  doc.setTextColor(0)

  autoTable(doc, {
    startY: 26,
    head: [columns.map((c) => c.header)],
    body: rows.map((row) =>
      columns.map((c) => {
        const v = c.accessor(row)
        return v === null || v === undefined ? '' : String(v)
      }),
    ),
    styles: { font: PDF_FONT, fontSize: 8, cellPadding: 2 },
    headStyles: { fillColor: [41, 128, 185], textColor: 255 },
    alternateRowStyles: { fillColor: [245, 245, 245] },
    margin: { left: 14, right: 14 },
  })

  return doc
}

export async function exportToPdf<T>(
  rows: T[],
  columns: ExportColumn<T>[],
  filename: string,
  title: string,
): Promise<void> {
  const doc = await buildPdf(rows, columns, title)
  const finalName = filename.endsWith('.pdf') ? filename : `${filename}_${timestamp()}.pdf`
  doc.save(finalName)
}
