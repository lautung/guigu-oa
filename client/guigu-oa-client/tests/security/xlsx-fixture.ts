/**
 * 最小 xlsx 生成器（#263）：安全套件要发真实可解析的 xlsx（稀疏远单元格、列数超限），
 * 仓库不引 zip 依赖，这里用 STORE 方式的 ZIP 头 + 最小 OOXML 部件手工拼。
 * 只服务拒绝型用例：不写用户行，也不复刻模板以外的业务语义。
 */

const IMPORT_HEADERS = ['用户名', '姓名', '部门', '岗位', '手机', '状态', '创建时间']

const CONTENT_TYPES = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`

const ROOT_RELS = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`

const WORKBOOK = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="用户" sheetId="1" r:id="rId1"/></sheets>
</workbook>`

const WORKBOOK_RELS = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`

const STYLES = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>`

interface SheetCell {
  column: number
  value: string
}

interface SheetRow {
  row: number
  cells: SheetCell[]
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let index = 0; index < 256; index += 1) {
    let value = index
    for (let bit = 0; bit < 8; bit += 1) {
      value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1
    }
    table[index] = value >>> 0
  }
  return table
})()

function crc32(bytes: Buffer): number {
  let crc = 0xffffffff
  for (const byte of bytes) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function uint16(value: number): Buffer {
  const buffer = Buffer.alloc(2)
  buffer.writeUInt16LE(value & 0xffff)
  return buffer
}

function uint32(value: number): Buffer {
  const buffer = Buffer.alloc(4)
  buffer.writeUInt32LE(value >>> 0)
  return buffer
}

/** STORE（不压缩）的最小 ZIP：xlsx 只要求 OOXML 部件齐全，压缩方式不影响 POI 读取。 */
function zip(entries: Array<{ name: string; content: string }>): Buffer {
  const locals: Buffer[] = []
  const centrals: Buffer[] = []
  let offset = 0
  for (const entry of entries) {
    const name = Buffer.from(entry.name, 'utf8')
    const data = Buffer.from(entry.content, 'utf8')
    const crc = crc32(data)
    const local = Buffer.concat([
      uint32(0x04034b50),
      uint16(20),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(crc),
      uint32(data.length),
      uint32(data.length),
      uint16(name.length),
      uint16(0),
      name,
      data,
    ])
    const central = Buffer.concat([
      uint32(0x02014b50),
      uint16(20),
      uint16(20),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(crc),
      uint32(data.length),
      uint32(data.length),
      uint16(name.length),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(0),
      uint32(offset),
      name,
    ])
    locals.push(local)
    centrals.push(central)
    offset += local.length
  }
  const directory = Buffer.concat(centrals)
  return Buffer.concat([
    ...locals,
    directory,
    uint32(0x06054b50),
    uint16(0),
    uint16(0),
    uint16(entries.length),
    uint16(entries.length),
    uint32(directory.length),
    uint32(offset),
    uint16(0),
  ])
}

function columnName(index: number): string {
  let name = ''
  let value = index + 1
  while (value > 0) {
    name = String.fromCharCode(65 + ((value - 1) % 26)) + name
    value = Math.floor((value - 1) / 26)
  }
  return name
}

function escapeXml(value: string): string {
  return value.replace(/[<>&"]/g, (character) => {
    switch (character) {
      case '<':
        return '&lt;'
      case '>':
        return '&gt;'
      case '&':
        return '&amp;'
      default:
        return '&quot;'
    }
  })
}

function sheetXml(rows: SheetRow[]): string {
  const body = rows
    .map((row) => {
      const cells = row.cells
        .map((cell) => {
          const reference = `${columnName(cell.column)}${row.row}`
          return `<c r="${reference}" t="inlineStr"><is><t>${escapeXml(cell.value)}</t></is></c>`
        })
        .join('')
      return `<row r="${row.row}">${cells}</row>`
    })
    .join('')
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${body}</sheetData></worksheet>`
}

function workbook(rows: SheetRow[]): Buffer {
  return zip([
    { name: '[Content_Types].xml', content: CONTENT_TYPES },
    { name: '_rels/.rels', content: ROOT_RELS },
    { name: 'xl/workbook.xml', content: WORKBOOK },
    { name: 'xl/_rels/workbook.xml.rels', content: WORKBOOK_RELS },
    { name: 'xl/styles.xml', content: STYLES },
    { name: 'xl/worksheets/sheet1.xml', content: sheetXml(rows) },
  ])
}

function headerRow(columns: number): SheetRow {
  return {
    row: 1,
    cells: Array.from({ length: columns }, (_, column) => ({
      column,
      value: column < IMPORT_HEADERS.length ? IMPORT_HEADERS[column] : `扩展${column}`,
    })),
  }
}

/** 表头正确、带一行可导入数据的最小 xlsx。 */
export function importWorkbook(): Buffer {
  return workbook([
    headerRow(IMPORT_HEADERS.length),
    {
      row: 2,
      cells: [
        { column: 0, value: 't263-security-import' },
        { column: 1, value: '安全用例' },
      ],
    },
  ])
}

/** 表头 + 正常首行 + 最后一行最后一列的极远稀疏单元格。 */
export function sparseWorkbook(): Buffer {
  return workbook([
    headerRow(IMPORT_HEADERS.length),
    {
      row: 2,
      cells: [
        { column: 0, value: 't263-security-sparse' },
        { column: 1, value: '稀疏用例' },
      ],
    },
    { row: 1_048_576, cells: [{ column: 16_383, value: 'far' }] },
  ])
}

/** 前 7 列是模板表头，其余列填扩展值：列数上限按最大列号判定。 */
export function wideWorkbook(columns: number): Buffer {
  return workbook([
    headerRow(columns),
    {
      row: 2,
      cells: [
        { column: 0, value: 't263-security-wide' },
        { column: 1, value: '宽表用例' },
        { column: columns - 1, value: '扩展值' },
      ],
    },
  ])
}
