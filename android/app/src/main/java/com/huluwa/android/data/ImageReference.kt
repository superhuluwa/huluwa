package com.huluwa.android.data

data class ImageReference(val name: String, val tag: String) {
    companion object {
        fun parse(input: String): ImageReference {
            val value = input.trim()
            require(value.isNotBlank() && value.none(Char::isWhitespace)) { "请输入有效的镜像名称" }
            require('@' !in value) { "请使用镜像名称与标签，例如 nginx:stable" }
            val separator = value.lastIndexOf(':').takeIf { it > value.lastIndexOf('/') }
            val name = if (separator != null) value.substring(0, separator) else value
            val tag = if (separator != null) value.substring(separator + 1) else "latest"
            require(name.isNotBlank() && Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}").matches(tag)) { "镜像标签格式不正确" }
            return ImageReference(name, tag)
        }
    }
}
