import {
  AlarmClock,
  ArrowDown,
  Bell,
  Brush,
  CircleCheck,
  Close,
  Document,
  EditPen,
  Expand,
  Files,
  Fold,
  Folder,
  FullScreen,
  HomeFilled,
  Menu,
  Monitor,
  Promotion,
  Setting,
  Share,
  Tickets,
  Tools,
  User,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

/** 外壳只认这一张表；菜单 icon 字符串不认识时回退 Document，不把 DB 字符串当组件名。 */
const SHELL_ICONS: Record<string, Component> = {
  AlarmClock,
  ArrowDown,
  Bell,
  Brush,
  CircleCheck,
  Close,
  Document,
  EditPen,
  Expand,
  Files,
  Fold,
  Folder,
  FullScreen,
  HomeFilled,
  Menu,
  Monitor,
  Promotion,
  Setting,
  Share,
  Tickets,
  Tools,
  User,
}

export function shellIcon(name: string | null | undefined): Component {
  return (name && SHELL_ICONS[name]) || Document
}
