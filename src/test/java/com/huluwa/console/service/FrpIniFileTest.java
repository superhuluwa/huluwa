package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * frpc.ini 行级编辑器：解析、原地改写、启停注释、增删段与换行符保持。
 *
 * <p>这些能力直接决定线上 frpc.ini 会不会被改坏，因此覆盖较细。</p>
 */
class FrpIniFileTest {

    private static final String SAMPLE = """
            # top comment
            [common]
            server_addr = 1.2.3.4

            [a-ssh]
            type = tcp
            local_ip = 127.0.0.1
            local_port = 22
            remote_port = 6000

            [b-web]
            type = tcp
            local_port = 80
            remote_port = 8080
            """;

    @Test
    void parseKeepsEverythingByteForByte() {
        FrpIniFile ini = FrpIniFile.parse(SAMPLE);
        assertEquals(SAMPLE, ini.render());
        assertEquals(2, ini.tunnels().size());
        assertEquals("1.2.3.4", ini.common().keys.get("server_addr"));
        assertEquals("22", ini.find("a-ssh").keys.get("local_port"));
        assertNull(ini.findStrict("A-SSH"), "findStrict 区分大小写");
        assertNotNull(ini.find("A-SSH"), "find 大小写不敏感");
        assertTrue(ini.find("a-ssh").disabled == false);
    }

    @Test
    void updateRewritesOnlyTargetKeys() {
        FrpIniFile ini = FrpIniFile.parse(SAMPLE);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("local_port", "2222");
        values.put("remote_port", "7000");
        ini.update("a-ssh", null, values, null);

        assertTrue(ini.render().contains("local_port = 2222"));
        assertTrue(ini.render().contains("remote_port = 7000"));
        assertTrue(ini.render().contains("type = tcp"), "未提交的键保持原样");
        assertTrue(ini.render().contains("# top comment"), "注释保持原样");
        assertEquals("2222", FrpIniFile.parse(ini.render()).find("a-ssh").keys.get("local_port"));
    }

    @Test
    void updateInsertsMissingKeyAndDeletesWhenNull() {
        FrpIniFile ini = FrpIniFile.parse(SAMPLE);
        Map<String, String> insert = new LinkedHashMap<>();
        insert.put("local_ip", "10.0.0.5");
        ini.update("a-ssh", null, insert, null);
        assertEquals("10.0.0.5", FrpIniFile.parse(ini.render()).find("a-ssh").keys.get("local_ip"));

        Map<String, String> remove = new LinkedHashMap<>();
        remove.put("remote_port", null);
        ini.update("a-ssh", null, remove, null);
        assertNull(FrpIniFile.parse(ini.render()).find("a-ssh").keys.get("remote_port"),
                "值为 null 表示删除该键");
        assertEquals("22", FrpIniFile.parse(ini.render()).find("a-ssh").keys.get("local_port"));
        assertEquals("8080", FrpIniFile.parse(ini.render()).find("b-web").keys.get("remote_port"),
                "删除只影响目标段");
    }

    @Test
    void toggleCommentsWholeSectionWithoutBreakingSyntax() {
        FrpIniFile ini = FrpIniFile.parse(SAMPLE);
        ini.setEnabled("b-web", false);

        assertTrue(ini.render().contains("#[b-web]"));
        assertTrue(ini.render().contains("#type = tcp"));

        FrpIniFile disabled = FrpIniFile.parse(ini.render());
        assertTrue(disabled.find("b-web").disabled);
        assertEquals("8080", disabled.find("b-web").keys.get("remote_port"), "停用后键值仍可读取");

        disabled.setEnabled("b-web", true);
        assertFalse(disabled.render().contains("#[b-web]"));
        FrpIniFile enabled = FrpIniFile.parse(disabled.render());
        assertFalse(enabled.find("b-web").disabled);
        assertEquals(3, enabled.find("b-web").keys.size());
    }

    @Test
    void renameReplaceAddAndRemove() {
        FrpIniFile ini = FrpIniFile.parse(SAMPLE);
        ini.update("b-web", "b-web2", null, null);
        assertNotNull(FrpIniFile.parse(ini.render()).find("b-web2"));
        assertNull(FrpIniFile.parse(ini.render()).findStrict("b-web"));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", "tcp");
        values.put("local_ip", "127.0.0.1");
        values.put("local_port", "3306");
        values.put("remote_port", "13306");
        ini.add("mysql", values);
        assertEquals(3, FrpIniFile.parse(ini.render()).tunnels().size());

        ini.remove("mysql");
        assertNull(FrpIniFile.parse(ini.render()).find("mysql"));
        assertFalse(ini.render().contains("\n\n\n"), "删除后不残留连续空行");
    }

    @Test
    void rejectsDuplicateAndMissingSections() {
        FrpIniFile ini = FrpIniFile.parse(SAMPLE);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", "tcp");
        assertThrows(IllegalArgumentException.class, () -> ini.add("a-ssh", values));
        assertThrows(IllegalArgumentException.class, () -> ini.update("nope", null, values, null));
        assertThrows(IllegalArgumentException.class, () -> ini.remove("nope"));
        assertThrows(IllegalArgumentException.class,
                () -> ini.update("a-ssh", "b[ad]", null, null));
    }

    @Test
    void keepsCrlfLineEndings() {
        String crlf = "[common]\r\nserver_addr = 1.1.1.1\r\n\r\n[x]\r\nlocal_port = 1\r\n";
        FrpIniFile ini = FrpIniFile.parse(crlf);
        assertEquals(crlf, ini.render());

        Map<String, String> values = new LinkedHashMap<>();
        values.put("local_port", "2");
        ini.update("x", null, values, null);
        assertTrue(ini.render().contains("local_port = 2\r\n"), "改写沿用原换行符");

        ini.add("y", values);
        assertTrue(ini.render().contains("[y]\r\n"));
    }

    /** 空值 / null 值都不能写进配置文件：新增时要直接跳过，而不是留下 custom_domains = null。 */
    @Test
    void addSkipsBlankValues() {
        FrpIniFile ini = FrpIniFile.parse("[common]\nserver_addr = 1.1.1.1\n");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", "tcp");
        values.put("local_port", "65001");
        values.put("custom_domains", null);
        values.put("remark", "");
        ini.add("fresh", values);

        String rendered = ini.render();
        assertFalse(rendered.contains("custom_domains"), "null 值不应写入");
        assertFalse(rendered.contains("remark ="), "空字符串不应写入");
        assertTrue(rendered.contains("type = tcp"));
        assertTrue(rendered.contains("local_port = 65001"));
    }

    /** 字面量 null 也要按删除处理，避免把 "null" 当合法值写进配置。 */
    @Test
    void updateTreatsLiteralNullAsDelete() {
        FrpIniFile ini = FrpIniFile.parse("[common]\n\n[x]\ntype = tcp\ncustom_domains = null\nremote_port = 1\n");
        Map<String, String> remove = new LinkedHashMap<>();
        remove.put("custom_domains", "null");
        ini.update("x", null, remove, null);

        assertNull(FrpIniFile.parse(ini.render()).find("x").keys.get("custom_domains"));
        assertEquals("1", FrpIniFile.parse(ini.render()).find("x").keys.get("remote_port"));
    }

    @Test
    void handlesEmptyAndCommonOnlyFiles() {
        assertTrue(FrpIniFile.parse("").isEmpty());
        assertEquals("", FrpIniFile.parse("").render());
        FrpIniFile ini = FrpIniFile.parse("[common]\nserver_addr = 1.1.1.1\n");
        assertEquals(0, ini.tunnels().size());
        assertNotNull(ini.common());
    }
}
