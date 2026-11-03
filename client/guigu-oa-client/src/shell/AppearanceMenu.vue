<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <button type="button" class="shell-icon-btn appearance-trigger" title="外观" aria-label="外观">
      <el-icon><Brush /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="theme:light" :disabled="shell.theme === 'light'">
          亮色
        </el-dropdown-item>
        <el-dropdown-item command="theme:dark" :disabled="shell.theme === 'dark'">
          暗色
        </el-dropdown-item>
        <el-dropdown-item divided command="density:small" :disabled="shell.density === 'small'">
          紧凑
        </el-dropdown-item>
        <el-dropdown-item command="density:default" :disabled="shell.density === 'default'">
          默认
        </el-dropdown-item>
        <el-dropdown-item command="density:large" :disabled="shell.density === 'large'">
          宽松
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { Brush } from '@element-plus/icons-vue'
import { useShellStore } from './shell-state'
import type { ShellDensity, ShellTheme } from './shell-preferences'

const shell = useShellStore()

function handleCommand(command: string): void {
  const [kind, value] = command.split(':')
  if (kind === 'theme') shell.setTheme(value as ShellTheme)
  if (kind === 'density') shell.setDensity(value as ShellDensity)
}

defineExpose({ handleCommand })
</script>

<style lang="scss" scoped>
.appearance-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--el-text-color-regular);
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.appearance-trigger:hover,
.appearance-trigger:focus-visible {
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  outline: none;
}
</style>
