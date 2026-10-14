# -*- coding: utf-8 -*-
"""从各服务官方来源素材生成会话品牌图标（96×96 PNG）。

用法：python frontend/src/assets/brand-icons/build-icons.py
素材目录（按文件名约定）：
  official/  各服务官方下载的原始图标（见 README.md 的来源表）
  png/       生成结果（放入本目录供前端 import）

素材获取（下载到 official/）：
  curl -o official/movie-dygangs.gif     https://www.dygangs.net/images/logo.gif
  curl -o official/dytt-dyttzyw.png      https://dyttzyw.com/template/default/img/favicon.png
  curl -o official/magnet-qbittorrent.ico https://www.qbittorrent.org/favicon.ico
  curl -o official/video-douyin.ico      https://www.douyin.com/favicon.ico
  迅雷：飞牛迅雷（迅雷 NAS 版）官方 SPK 的 ui/images/pan-xunlei-com-256.png
        另存为 official/xunlei-pan-256.png
"""
import os
from PIL import Image, ImageFilter
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OFFICIAL = os.path.join(HERE, "official")
SIZE = 96


def fit_square(im, pad_ratio=0.0):
    """裁掉透明边后等比缩放居中到 SIZE×SIZE。"""
    im = im.convert("RGBA")
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    inner = int(SIZE * (1 - pad_ratio * 2))
    scale = min(inner / im.width, inner / im.height)
    im = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((SIZE - im.width) // 2, (SIZE - im.height) // 2))
    return canvas


def white_to_alpha(im, threshold=205, span=50):
    """白底转透明（站点 logo 多为白底 GIF / JPG，深色主题下需要透明底）。"""
    arr = np.array(im.convert("RGBA")).astype(int)
    r, g, b, alpha = arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3]
    whiteness = np.clip((np.minimum(np.minimum(r, g), b) - threshold) / span, 0, 1)
    out = arr.copy()
    out[..., 3] = (alpha * (1 - whiteness)).astype(np.uint8)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def ladder_upscale(im, target=SIZE, sharpen=True):
    """小尺寸 favicon 阶梯放大 + 轻锐化，比单步 LANCZOS 更锐利。"""
    im = im.convert("RGBA")
    while im.width * 2 <= target:
        im = im.resize((im.width * 2, im.height * 2), Image.LANCZOS)
    if im.width != target:
        im = im.resize((target, target), Image.LANCZOS)
    if sharpen:
        im = im.filter(ImageFilter.UnsharpMask(radius=1.0, percent=45, threshold=2))
    return im


def save(im, name):
    im.save(name, optimize=True)
    print(f"{os.path.basename(name):28s} {im.size} {os.path.getsize(name):>7d}B")


# 1) 电影港：官方头图只取左侧蓝色 G 形标识（x 8..74，其余是站名与网址）
dygangs = Image.open(os.path.join(OFFICIAL, "movie-dygangs.gif")).convert("RGBA")
mark = white_to_alpha(dygangs.crop((8, 14, 74, 85)))
save(fit_square(mark, pad_ratio=0.06), os.path.join(HERE, "movie-dygangs.png"))

# 2) 电影天堂：官方站点图标只有 16px，阶梯放大 + 轻锐化
dytt = Image.open(os.path.join(OFFICIAL, "dytt-dyttzyw.png")).convert("RGBA")
size = ladder_upscale(dytt, SIZE)
save(fit_square(size, pad_ratio=0.04), os.path.join(HERE, "dytt-dyttzyw.png"))

# 3) qBittorrent：官方站点图标
qb = Image.open(os.path.join(OFFICIAL, "magnet-qbittorrent.ico")).convert("RGBA")
save(fit_square(qb, pad_ratio=0.03), os.path.join(HERE, "magnet-qbittorrent.png"))

# 4) 视频解析：抖音官方图标（该会话解析的就是抖音等 App 的分享口令）
douyin = Image.open(os.path.join(OFFICIAL, "video-douyin.ico")).convert("RGBA")
save(fit_square(douyin, pad_ratio=0.05), os.path.join(HERE, "video-douyin.png"))

# 5) 迅雷下载：飞牛迅雷（迅雷 NAS 版）官方 SPK 里的应用图标（蜂鸟）
xunlei = Image.open(os.path.join(OFFICIAL, "xunlei-pan-256.png")).convert("RGBA")
save(fit_square(xunlei, pad_ratio=0.03), os.path.join(HERE, "xunlei.png"))

print("done")
