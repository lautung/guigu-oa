<template>
  <li class="sidebar-node">
    <router-link
      :to="item.path"
      class="menu-link"
      :class="{ active: isActive }"
    >
      {{ item.title }}
    </router-link>
    <ul v-if="item.children && item.children.length" class="submenu-list">
      <sidebar-node
        v-for="child in item.children"
        :key="child.path"
        :item="child"
        :active-path="activePath"
      />
    </ul>
  </li>
</template>

<script>
export default {
  name: 'SidebarNode',
  props: {
    item: {
      type: Object,
      required: true
    },
    activePath: {
      type: String,
      required: true
    }
  },
  computed: {
    isActive() {
      return this.activePath === this.item.path || this.activePath.startsWith(`${this.item.path}/`)
    }
  }
}
</script>

<style lang="scss" scoped>
.menu-link {
  display: block;
  padding: 10px 16px 10px 24px;
  color: #606266;
  text-decoration: none;
  font-size: 14px;

  &:hover {
    color: #409eff;
    background: #ecf5ff;
  }

  &.active {
    color: #409eff;
    background: #ecf5ff;
    font-weight: 600;
  }
}

.submenu-list {
  list-style: none;
  margin: 0;
  padding: 0;

  .menu-link {
    padding-left: 36px;
    font-size: 13px;
  }
}
</style>
