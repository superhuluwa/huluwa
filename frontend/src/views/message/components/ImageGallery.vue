<template>
    <div class="image-gallery" role="region" aria-label="解析图集">
        <div class="gallery-viewport">
            <n-carousel v-model:current-index="currentIndex" class="gallery-carousel" :loop="false"
                :show-dots="false" draggable keyboard>
                <n-carousel-item v-for="(image, index) in images" :key="index">
                    <VideoBubble v-if="image.livePhotoUrl && index === currentIndex" class="gallery-live"
                        :message="liveMessage(image, index)" />
                    <button v-else class="gallery-image" type="button" :aria-label="`查看第 ${index + 1} 张大图`"
                        @click="openImage(image, index)">
                        <span v-if="failedImages[index]" class="gallery-fallback">
                            <i class="fa-solid fa-image"></i>图片加载失败
                        </span>
                        <img v-else :src="image.url" :alt="`第 ${index + 1} 张图片`" loading="lazy" draggable="false"
                            @error="failedImages[index] = true" />
                    </button>
                </n-carousel-item>
            </n-carousel>
        </div>
        <div class="gallery-navigation">
            <button class="gallery-arrow" type="button" aria-label="上一张" :disabled="currentIndex === 0"
                @click="currentIndex--"><i class="fa-solid fa-chevron-left"></i></button>
            <div class="gallery-track" aria-hidden="true">
                <span class="gallery-position" :style="{
                    width: `${100 / images.length}%`,
                    transform: `translateX(${currentIndex * 100}%)`
                }"></span>
            </div>
            <span class="gallery-count" aria-live="polite">{{ currentIndex + 1 }} / {{ images.length }}</span>
            <button class="gallery-arrow" type="button" aria-label="下一张" :disabled="currentIndex === images.length - 1"
                @click="currentIndex++"><i class="fa-solid fa-chevron-right"></i></button>
        </div>
        <RichText v-if="caption" class="gallery-caption" :text="caption" :support-fold="false" />
    </div>
</template>

<script>
import { NCarousel, NCarouselItem } from 'naive-ui'
import RichText from './RichText.vue'
import VideoBubble from './VideoBubble.vue'

export default {
    name: 'ImageGallery',
    components: { NCarousel, NCarouselItem, RichText, VideoBubble },
    props: {
        images: { type: Array, required: true },
        caption: { type: String, default: '' }
    },
    emits: ['open-cover'],
    data() {
        return { currentIndex: 0, failedImages: {} }
    },
    methods: {
        openImage(image, index) {
            this.$emit('open-cover', { url: image.url, title: `第 ${index + 1} / ${this.images.length} 张` })
        },
        liveMessage(image, index) {
            return {
                id: index,
                content: image.livePhotoUrl,
                payload: JSON.stringify({ poster: image.url })
            }
        }
    }
}
</script>

<style scoped>
.image-gallery {
    min-width: 0;
}

.gallery-viewport {
    width: 100%;
    aspect-ratio: 3 / 4;
    max-height: 400px;
    overflow: hidden;
    border-radius: 11px;
    background: rgba(148, 163, 184, .08);
}

.gallery-carousel {
    height: 100%;
}

.gallery-image {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    padding: 0;
    border: 0;
    background: none;
    cursor: zoom-in;
}

.gallery-image img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    user-select: none;
}

.gallery-fallback {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: var(--text-3);
    font-size: 13px;
}

.gallery-live,
.gallery-live :deep(.video-cover),
.gallery-live :deep(.inline-video),
.gallery-live :deep(.video-el) {
    width: 100%;
    height: 100%;
    max-height: none;
}

.gallery-live :deep(.video-cover img) {
    object-fit: contain;
}

.gallery-navigation {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 4px 0 0;
}

.gallery-arrow {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: none;
    width: 32px;
    height: 36px;
    padding: 0;
    border: 0;
    border-radius: 8px;
    background: none;
    color: var(--pine);
    cursor: pointer;
}

.gallery-arrow:disabled {
    opacity: .3;
    cursor: default;
}

.gallery-arrow:hover:not(:disabled) {
    background: var(--soft-green);
}

.gallery-track {
    flex: 1;
    height: 4px;
    overflow: hidden;
    border-radius: 999px;
    background: var(--line-2);
}

.gallery-position {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: var(--pine);
    transition: transform .2s ease;
}

.gallery-count {
    flex: none;
    color: var(--text-2);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
}

.gallery-caption {
    padding: 0 6px 4px;
    font-size: 13px;
}
</style>
