package com.huluwa.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 容器内 HLS 工具路径和媒体根目录；根目录通过部署配置限定。 */
@ConfigurationProperties(prefix = "huluwa.hls")
public class HlsProperties {
    private String mediaRoot = "/vol1/1000/downloads";
    private String legacyMediaRoot = "/media";
    private String binary = "/usr/local/bin/N_m3u8DL-RE";
    private String ffmpeg = "/usr/bin/ffmpeg";
    private String ffprobe = "/usr/bin/ffprobe";

    public String getMediaRoot() { return mediaRoot; }
    public void setMediaRoot(String value) { mediaRoot = value; }
    public String getLegacyMediaRoot() { return legacyMediaRoot; }
    public void setLegacyMediaRoot(String value) { legacyMediaRoot = value; }
    public String getBinary() { return binary; }
    public void setBinary(String value) { binary = value; }
    public String getFfmpeg() { return ffmpeg; }
    public void setFfmpeg(String value) { ffmpeg = value; }
    public String getFfprobe() { return ffprobe; }
    public void setFfprobe(String value) { ffprobe = value; }
}
