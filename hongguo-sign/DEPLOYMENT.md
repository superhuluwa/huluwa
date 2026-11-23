# 当前部署记录

部署日期：2026-11-23（UTC）。源码位于 huluwa 仓库的 `hongguo-sign/`。

| 项目 | 当前值 |
| --- | --- |
| 宿主机 | Debian 13，`192.168.3.83` |
| 部署目录 | `/opt/hongguo-frida` |
| 容器名称 | `hongguo-frida` |
| 镜像 | `hongguo-frida:single-amd64` |
| 内网入口 | `http://192.168.3.83:8902` |
| 公网入口 | `http://taihe.678120.cn:8056` |
| 转发 | 飞牛 `frpc-caiji`，`红果sign服务` → `192.168.3.83:8902` |
| 当前数据卷 | `hongguo-frida-data`，从停止写入的原卷复制 |
| 原数据卷 | `hongguo-native-data`，继续保留 |
| 旧配置备份 | `/root/hongguo-frida-migration/20261123-233539` |
| FRP 配置备份 | 飞牛 `/vol1/1000/docker/frpc-caiji/frpc.ini.bak-hongguo-frida-20261123-235028` |

原 `hongguo-signer-native` 和 `hongguo-android-native` 两个容器已在公网验证通过后删除。FRP 使用管理接口热更新，没有重启整个转发容器。

## 播放与重启记录

使用当前可用的普通短剧分集 `7706107565024758808` 检查，按客户端的单集 `video_tab/video_model/v` 路由取址：

- 最终镜像启动后连续检查三次，全部成功。
- 执行 `docker restart hongguo-frida`，未手动启动 App 或 Frida，恢复后连续检查三次，全部成功。
- 公网映射迁移后检查两次，删除旧容器后再检查一次，均成功。
- 每次取得十个媒体地址，并从 CDN 成功读取 1024 字节，HTTP 状态为 206。
- 重启前后设备身份指纹保持一致。

运行记录位于服务器 `/opt/hongguo-frida/runtime/`：`before-restart.jsonl`、`after-restart.jsonl`、`restart-verification.json`、`public-playback.jsonl`、`final-public-playback.jsonl`、`recovery-timing.json`。

本次容器于 `2026-11-23T23:48:37.879506626Z` 启动；Android 在约 6.4 秒后完成引导，签名 HTTP 服务在约 40.7 秒后启动，完整设备档案和六头签名在 **42.3 秒**后就绪。配置保留了 30 秒 App 预热，普通重启建议预留约一分钟。本次只验证容器重启，未重启宿主机。

## TV 模拟器状态

宿主机未启用 BinderFS，TV 的 `redroid` 与签名 Android 共用 Binder 设备，造成 Android 服务注册冲突。经用户确认，`redroid` 已停止，容器和 `/opt/redroid/data` 保留。继续启动它会再次发生冲突；如需并存，先准备独立 Binder 设备或启用 BinderFS。

## 日常操作

```bash
cd /opt/hongguo-frida
docker compose up -d
docker restart hongguo-frida
docker logs --tail 100 hongguo-frida
```
