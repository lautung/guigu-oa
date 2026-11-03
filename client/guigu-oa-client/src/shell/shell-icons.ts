import { h, type FunctionalComponent } from 'vue'

/**
 * 外壳图标只在这一处定义（#228）：内联 SVG，不引入图标包，菜单里的 icon 字符串
 * 只按名字查表，认不出时回退 Document，绝不把数据库字符串当组件名。
 */
interface IconSpec {
  paths: string[]
  circles?: [number, number, number][]
}

const ICONS: Record<string, IconSpec> = {
  HomeFilled: {
    paths: ['M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'],
  },
  Tickets: {
    paths: ['M4 5h16v4a2 2 0 0 0 0 6v4H4v-4a2 2 0 0 0 0-6z', 'M8 9h8M8 15h5'],
  },
  Setting: {
    paths: ['M4 7h6M14 7h6', 'M4 12h10M18 12h2', 'M4 17h4M12 17h8'],
    circles: [
      [12, 7, 2],
      [16, 12, 2],
      [10, 17, 2],
    ],
  },
  Monitor: {
    paths: ['M3 5h18v11H3z', 'M8 21h8', 'M12 16v5'],
  },
  Tools: {
    paths: ['M14 4a4 4 0 0 1 5 5l-9 9-5 1 1-5z', 'M5 19l3-3'],
  },
  EditPen: {
    paths: ['M4 20h4L20 8l-4-4L4 16z', 'M14 6l4 4'],
  },
  AlarmClock: {
    paths: ['M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z', 'M12 9v4l3 2'],
  },
  CircleCheck: {
    paths: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M8 12l3 3 5-6'],
  },
  Promotion: {
    paths: ['M3 11l18-7-7 18-3-7z', 'M11 15l4-4'],
  },
  Share: {
    paths: ['M8.5 10.5l7-3M8.5 13.5l7 3'],
    circles: [
      [6, 12, 2],
      [18, 6, 2],
      [18, 18, 2],
    ],
  },
  User: {
    paths: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'],
  },
  Bell: {
    paths: ['M18 16v-5a6 6 0 1 0-12 0v5l-1.6 2h15.2L18 16z', 'M10 21a2 2 0 0 0 4 0'],
  },
  Document: {
    paths: ['M6 3h8l4 4v14H6z', 'M14 3v4h4'],
  },
  Folder: {
    paths: ['M3 6h6l2 2h10v11H3z'],
  },
  Files: {
    paths: ['M8 3h7l4 4v14H8z', 'M15 3v4h4', 'M4 7v14h11'],
  },
  Menu: {
    paths: ['M4 6h16M4 12h16M4 18h16'],
  },
  ArrowDown: {
    paths: ['M6 9l6 6 6-6'],
  },
  Close: {
    paths: ['M6 6l12 12M18 6L6 18'],
  },
  Expand: {
    paths: ['M9 6l6 6-6 6'],
  },
  Fold: {
    paths: ['M15 6l-6 6 6 6'],
  },
  Search: {
    paths: ['M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16z', 'M21 21l-5.2-5.2'],
  },
  FullScreen: {
    paths: ['M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5'],
  },
  ExitFullScreen: {
    paths: ['M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5'],
  },
}

const cache = new Map<string, FunctionalComponent>()

function createIcon(spec: IconSpec): FunctionalComponent {
  return () =>
    h(
      'svg',
      {
        viewBox: '0 0 24 24',
        width: '1em',
        height: '1em',
        fill: 'none',
        stroke: 'currentColor',
        'stroke-width': 1.8,
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        'aria-hidden': 'true',
      },
      [
        ...spec.paths.map((d) => h('path', { d })),
        ...(spec.circles || []).map(([cx, cy, r]) => h('circle', { cx, cy, r })),
      ],
    )
}

export function shellIcon(name: string | null | undefined): FunctionalComponent {
  const key = name && ICONS[name] ? name : 'Document'
  let icon = cache.get(key)
  if (!icon) {
    icon = createIcon(ICONS[key])
    cache.set(key, icon)
  }
  return icon
}
