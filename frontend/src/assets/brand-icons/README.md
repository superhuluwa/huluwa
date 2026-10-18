# 会话品牌图标

消息中心「会话列表」与「消息头像」使用的官方品牌图标。`SessionIcon.vue` 按
`chat_session.kind` 映射到本目录的 PNG（会话 kind → 文件）。

| 会话 kind | 会话 | 图标文件 | 官方来源 |
| --- | --- | --- | --- |
| `movie` | 电影港 | `movie-dygangs.png` | https://www.dygangs.net/images/logo.gif （官方站头图，取左侧蓝色「G」标识） |
| `dytt` | 电影天堂 | `dytt-dyttzyw.png` | https://dyttzyw.com/template/default/img/favicon.png （官方站点图标） |
| `magnet` | qBittorrent 下载 | `magnet-qbittorrent.png` | https://www.qbittorrent.org/favicon.ico （官方站点图标） |
| `video` | 视频解析 | `video-douyin.png` | https://www.douyin.com/favicon.ico （抖音官方图标；该会话解析抖音等 App 的分享口令） |
| `xunlei` | 迅雷下载【飞牛】 | `xunlei.png` | 飞牛迅雷（迅雷 NAS 版）官方 SPK 的应用图标（蜂鸟）`ui/images/pan-xunlei-com-256.png` |
| `xunlei-win` | 迅雷下载【Win】 | `xunlei.png` | 与迅雷下载【飞牛】共用迅雷官方图标（蜂鸟） |

## 目录说明

- `*.png`：前端实际加载的 96×96 图标（由脚本生成，不要手改）。
- `official/`：从各服务官方下载的原始素材（网站 favicon / 站头图 / 官方安装包图标）：
  `movie-dygangs.gif`、`dytt-dyttzyw.png`、`magnet-qbittorrent.ico`、`video-douyin.ico`、
  `xunlei-pan-256.png`。
- `build-icons.py`：从 `official/` 生成上面 5 个 PNG。依赖 `pillow`、`numpy`：

  ```powershell
  python -m pip install pillow numpy
  python frontend/src/assets/brand-icons/build-icons.py
  ```

## 说明

- 「系统通知」等没有官方品牌的会话不走品牌图标，仍用 Font Awesome 图标字体
  （见 `SessionIcon.vue` 的 `fallbackClass`）。
- 图标是各品牌商标，仅用于标识本控制台对接的对应服务。
- 替换图标：把官方素材放进 `official/` 后重跑生成脚本；新增会话类型则在
  `SessionIcon.vue` 的 `BRAND_ICONS` 里补一条映射，并在此表登记来源。
