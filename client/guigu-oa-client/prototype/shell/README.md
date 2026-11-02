# 原型：多标签外壳与工作台观感（#209）

一次性原型，**不合并、不进产品代码**。回答的问题：在「顶部一级 + 左侧二级 + 多标签 + 工作台」已定的前提下，观感怎么落。

## 跑起来

从仓库根目录：

```bash
cd client/guigu-oa-client
npx vite prototype/shell --port 5199
```

打开 <http://127.0.0.1:5199/?variant=A>。

## 三个变体

| key | 名称 | 结构差异 |
| --- | --- | --- |
| A | 经典后台 | 深色左侧树 + 顶栏一级 + 卡片式标签；查询表单常驻、卡片化工作台 |
| B | 收件箱优先 | 浅色上下文侧栏 + Chrome 式标签 + 面包屑进内容头；大数字待处理 + 收件箱列表 |
| C | 紧凑指挥台 | 窄侧栏 + 全宽下划线标签条 + 密集列表；筛选 chips + 紧凑分页 |

底部浮动条切换变体（← / → 或方向键）；「原型设置」里切换页面、数据状态（正常 / 加载中 / 空态）、亮暗、全局尺寸、主色、窄屏布局和顶栏件开关。

URL 参数可直接分享某个状态：

```text
?variant=A|B|C
&page=workbench|instanceList
&state=ready|loading|empty
&theme=light|dark
&size=small|default|large
&palette=blue|teal|indigo
&narrow=1
&breadcrumb=0&fullscreen=0&search=0
```

## 边界

- 数据是假的，动作不写后端；标签、列设置、筛选只在内存里。
- 窄屏布局可用「原型设置 → 窄屏预览」，但把浏览器窗口拖到 900px 以下更真实（抽屉、表格横向滚动都按真实视口走）。
- 只验证观感与密度，不验证权限、路由、请求。
