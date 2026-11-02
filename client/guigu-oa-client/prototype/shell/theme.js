export const palettes = [
  { key: 'blue', label: '拂晓蓝', primary: '#409eff' },
  { key: 'teal', label: '松石青', primary: '#12a594' },
  { key: 'indigo', label: '靛青', primary: '#5b6ef5' },
]

function mix(hex, target, weight) {
  const parse = (value) => [
    parseInt(value.slice(1, 3), 16),
    parseInt(value.slice(3, 5), 16),
    parseInt(value.slice(5, 7), 16),
  ]
  const [r1, g1, b1] = parse(hex)
  const [r2, g2, b2] = parse(target)
  const channel = (a, b) => Math.round(a * (1 - weight) + b * weight)
  return `#${[channel(r1, r2), channel(g1, g2), channel(b1, b2)]
    .map((n) => n.toString(16).padStart(2, '0'))
    .join('')}`
}

export function applyPalette(key) {
  const palette = palettes.find((item) => item.key === key) || palettes[0]
  const root = document.documentElement
  root.style.setProperty('--el-color-primary', palette.primary)
  ;[3, 5, 7, 8, 9].forEach((level) => {
    root.style.setProperty(
      `--el-color-primary-light-${level}`,
      mix(palette.primary, '#ffffff', level / 10),
    )
  })
  root.style.setProperty('--el-color-primary-dark-2', mix(palette.primary, '#000000', 0.2))
}
