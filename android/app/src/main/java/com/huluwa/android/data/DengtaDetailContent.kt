package com.huluwa.android.data

import kotlinx.serialization.json.JsonObject

/** 官方详情与“查看全部”接口共用的营销、物料和演职资料。 */
data class DengtaContentOption(val code: String, val name: String) {
    companion object {
        fun from(data: JsonObject) = DengtaContentOption(data.text("code"), data.text("name"))
    }
}

data class DengtaMarketingEvent(
    val type: String, val typeCode: String, val title: String, val dateText: String, val date: String,
    val sourceType: String, val images: List<String>, val targetUrl: String,
) {
    companion object {
        fun from(data: JsonObject) = DengtaMarketingEvent(
            data.text("eventType"), data.text("eventTypeCode"), data.text("title"), data.text("dateText"), data.text("bizDate"),
            data.text("dataSourceType"), data.rows("photoList").map { dengtaImageUrl(it.asObject().text("previewPath")) }
                .filter(String::isNotBlank).ifEmpty { listOfNotNull(dengtaImageUrl(data.text("previewPath")).takeIf(String::isNotBlank)) },
            dengtaImageUrl(data.text("toUrl")),
        )
    }
}

data class DengtaMarketingContent(
    val events: List<DengtaMarketingEvent> = emptyList(), val options: List<DengtaContentOption> = emptyList(),
) {
    companion object {
        fun from(data: JsonObject) = DengtaMarketingContent(
            data.rows("eventList").map { DengtaMarketingEvent.from(it.asObject()) }.filter { it.title.isNotBlank() },
            data.rows("menuList").map { DengtaContentOption.from(it.asObject()) },
        )
    }
}

data class DengtaMaterial(
    val id: String, val type: String, val category: String, val title: String,
    val preview: String, val count: Int, val targetUrl: String,
) {
    companion object {
        fun from(data: JsonObject) = DengtaMaterial(
            data.text("materialId"), data.text("type"), data.text("materialTypeCode"), data.text("title"),
            dengtaImageUrl(data.text("previewPath")), data.number("count")?.toInt() ?: 0, dengtaImageUrl(data.text("toUrl")),
        )
    }
}

data class DengtaMaterialContent(
    val items: List<DengtaMaterial> = emptyList(), val options: List<DengtaContentOption> = emptyList(),
    val tabs: List<String> = emptyList(), val selectedTab: String = "PHOTO",
) {
    companion object {
        fun from(data: JsonObject) = DengtaMaterialContent(
            data.rows("materialList").map { DengtaMaterial.from(it.asObject()) }.filter { it.preview.isNotBlank() },
            data.rows("menuList").map { DengtaContentOption.from(it.asObject()) },
            data.stringList("tabList"), data.text("selectTab", fallback = "PHOTO"),
        )
    }
}

data class DengtaPerson(
    val id: String, val name: String, val type: String, val role: String,
    val portrait: String, val rolePortrait: String, val act: String,
) {
    val roleLabel: String get() = if (act.isNotBlank() && role.isNotBlank()) "$act : $role" else role
    companion object {
        fun from(data: JsonObject) = DengtaPerson(
            data.text("artisteId"), data.text("name"), data.text("artisteType"), data.text("roleName"),
            dengtaImageUrl(data.text("headImageUrl")), dengtaImageUrl(data.text("roleHeadImgSrc", "roleHeadImg")), data.text("act"),
        )
    }
}

data class DengtaCompany(val id: String, val name: String, val logo: String) {
    companion object {
        fun from(data: JsonObject) = DengtaCompany(data.text("companyId"), data.text("companyName"), dengtaImageUrl(data.text("logoUrl")))
    }
}
data class DengtaCompanyGroup(val name: String, val companies: List<DengtaCompany>)
data class DengtaHighlight(val html: String, val timestamp: Long) {
    companion object {
        fun from(data: JsonObject) = DengtaHighlight(data.text("contentHtml", "simpleContentHtml"), data.number("publishTimestamp")?.toLong() ?: 0L)
    }
}
data class DengtaHighlightContent(val items: List<DengtaHighlight> = emptyList(), val nextTime: String = "") {
    companion object {
        fun from(data: JsonObject) = DengtaHighlightContent(
            data.rows("timeline").map { DengtaHighlight.from(it.asObject()) }.filter { it.html.isNotBlank() },
            data.text("lastTime").takeUnless { it == "0" }.orEmpty(),
        )
    }
}
data class DengtaInformation(
    val description: String = "", val materials: List<DengtaMaterial> = emptyList(), val singleMaterialType: Boolean = true,
    val people: List<DengtaPerson> = emptyList(), val companies: List<DengtaCompany> = emptyList(),
    val companyGroups: List<DengtaCompanyGroup> = emptyList(),
) {
    companion object {
        fun from(sections: List<JsonObject>): DengtaInformation {
            val material = sections.firstOrNull { "materialList" in it }
            val credits = sections.firstOrNull { "showMembers" in it || "studioVO" in it || "description" in it }
            val studio = credits?.child("studioVO")
            val relations = studio?.child("relMap")
            val groupNames = (studio?.stringList("studioTypeList").orEmpty() + relations?.keys.orEmpty()).distinct()
            return DengtaInformation(
                credits?.text("description").orEmpty(), material?.rows("materialList").orEmpty().map { DengtaMaterial.from(it.asObject()) },
                material?.flag("singleType", fallback = true) ?: true,
                credits?.rows("showMembers").orEmpty().map { DengtaPerson.from(it.asObject()) }.filter { it.name.isNotBlank() },
                studio?.rows("studioList").orEmpty().map { DengtaCompany.from(it.asObject()) }.filter { it.name.isNotBlank() },
                groupNames.map { name -> DengtaCompanyGroup(name, relations?.rows(name).orEmpty().map { DengtaCompany.from(it.asObject()) }) }
                    .filter { it.companies.isNotEmpty() },
            )
        }
    }
}
