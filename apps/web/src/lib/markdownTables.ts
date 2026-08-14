const SEP = /^:?-+:?$/

/**
 * Models sometimes emit a GFM table as one long line:
 * `| A | B | | :--- | :--- | | row | …`
 * Split that into real rows so remark-gfm can render it.
 */
export function repairMarkdownTables(text: string): string {
  if (!text || !text.includes('|')) {
    return text
  }
  return text
    .split('\n')
    .map((line) => expandCollapsedTableLine(line))
    .join('\n')
}

function expandCollapsedTableLine(line: string): string {
  const pipeCount = (line.match(/\|/g) ?? []).length
  if (pipeCount < 10 || !line.includes('---')) {
    return line
  }
  const cells = line
    .split('|')
    .map((c) => c.trim())
    .filter((c, i, arr) => !(c === '' && (i === 0 || i === arr.length - 1)))
  const sepAt = cells.findIndex((c) => SEP.test(c))
  if (sepAt <= 0) {
    return line
  }
  const cols = sepAt
  const rows: string[][] = [cells.slice(0, cols)]
  let i = cols
  while (i < cells.length && SEP.test(cells[i])) {
    i += 1
  }
  while (i < cells.length) {
    rows.push(cells.slice(i, i + cols))
    i += cols
  }
  if (rows.length < 2) {
    return line
  }
  const sep = Array.from({ length: cols }, () => '---')
  const all = [rows[0], sep, ...rows.slice(1)]
  return all.map((r) => '| ' + r.join(' | ') + ' |').join('\n')
}
