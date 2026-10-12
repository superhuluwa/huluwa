package com.huluwa.console.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * frpc.ini 行级编辑器。
 *
 * <p>只改写目标隧道涉及的行，其它注释、空行、未知键与原始顺序全部原样保留，
 * 避免把用户手工维护的配置文件重排成机器格式。</p>
 *
 * <p>「停用隧道」沿用 frp 社区通用的整段注释写法：段内每行加一个 {@code #} 前缀，
 * 恢复时再去掉该前缀；因此停用不影响 frpc 校验，也不会丢失任何配置。</p>
 */
final class FrpIniFile {

    /** 启用段：{@code [name]} */
    private static final Pattern HEADER = Pattern.compile("^\\s*\\[([^\\[\\]]+)]\\s*$");
    /** 停用段：{@code #[name]} 或 {@code # [name]} */
    private static final Pattern DISABLED_HEADER = Pattern.compile("^\\s*#\\s*\\[([^\\[\\]]+)]\\s*$");
    /** 键值行：{@code key = value}（值可含等号） */
    private static final Pattern KEY = Pattern.compile("^\\s*([A-Za-z0-9_.\\-]+)\\s*=\\s*(.*)$");

    /** 一个配置段（common 或隧道）。 */
    static final class Section {

        String name;
        boolean disabled;
        /** 段头所在行号 */
        int headerIndex;
        /** 段结束行号（不含） */
        int endIndex;
        final LinkedHashMap<String, String> keys = new LinkedHashMap<>();

        boolean isCommon() {
            return "common".equalsIgnoreCase(name);
        }
    }

    private final List<String> lines;
    private final Map<String, Section> sections = new LinkedHashMap<>();

    private FrpIniFile(List<String> lines) {
        this.lines = lines;
        reindex();
    }

    /** 按行切分并保留每行原始换行符，保证渲染后与原文逐字节一致。 */
    static FrpIniFile parse(String content) {
        List<String> lines = new ArrayList<>();
        if (content != null && !content.isEmpty()) {
            int start = 0;
            for (int i = 0; i < content.length(); i++) {
                char ch = content.charAt(i);
                if (ch == '\n') {
                    lines.add(content.substring(start, i + 1));
                    start = i + 1;
                }
            }
            if (start < content.length()) {
                lines.add(content.substring(start));
            }
        }
        return new FrpIniFile(lines);
    }

    String render() {
        return String.join("", lines);
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }

    Section find(String name) {
        if (name == null) {
            return null;
        }
        Section exact = sections.get(name);
        if (exact != null) {
            return exact;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (Section section : sections.values()) {
            if (section.name.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return section;
            }
        }
        return null;
    }

    Section findStrict(String name) {
        return name == null ? null : sections.get(name);
    }

    Section common() {
        for (Section section : sections.values()) {
            if (section.isCommon()) {
                return section;
            }
        }
        return null;
    }

    /** 全部隧道段（不含 common），按文件出现顺序。 */
    Collection<Section> tunnels() {
        List<Section> result = new ArrayList<>();
        for (Section section : sections.values()) {
            if (!section.isCommon()) {
                result.add(section);
            }
        }
        return result;
    }

    /** 新增隧道段（追加到文件末尾）。 */
    void add(String name, Map<String, String> values) {
        if (find(name) != null) {
            throw new IllegalArgumentException("隧道名称已存在：" + name);
        }
        String newline = newline();
        if (!lines.isEmpty() && !body(lines.get(lines.size() - 1)).isBlank()) {
            lines.add(newline);
        }
        lines.add("[" + name + "]" + newline);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            // 空值 / 空白值表示「不写这个键」：新增隧道时不要留下 custom_domains = null 之类的脏数据。
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            lines.add(entry.getKey() + " = " + entry.getValue() + newline);
        }
        reindex();
    }

    /**
     * 修改隧道。
     *
     * @param values  需要写入的键；值为 {@code null} 表示删除该键；未出现的键保持原样
     * @param enabled 是否启用；{@code null} 表示不改动
     */
    void update(String name, String newName, Map<String, String> values, Boolean enabled) {
        Section section = require(name);
        if (newName != null && !newName.isBlank() && !newName.equals(section.name)) {
            Section existing = find(newName);
            if (existing != null && existing != section) {
                throw new IllegalArgumentException("隧道名称已存在：" + newName);
            }
            if (!newName.equals(newName.trim()) || newName.contains("[") || newName.contains("]")
                    || newName.contains("#") || newName.contains(newline())
                    || newName.contains("\n") || newName.contains("\r")) {
                throw new IllegalArgumentException("隧道名称不能包含方括号、井号或换行符，且首尾不能有空格");
            }
            lines.set(section.headerIndex, headerLine(newName, section.disabled, section.headerIndex));
            reindex();
        }
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                setKey(name, entry.getKey(), entry.getValue());
            }
        }
        if (enabled != null && enabled != !require(name).disabled) {
            setEnabled(name, enabled);
        }
        reindex();
    }

    /** 删除隧道段，并顺手收掉多余的空行，避免留下双空行。 */
    void remove(String name) {
        Section section = require(name);
        deleteRange(section.headerIndex, section.endIndex);
        int position = section.headerIndex;
        if (position > 0 && position < lines.size()
                && body(lines.get(position - 1)).isBlank() && body(lines.get(position)).isBlank()) {
            lines.remove(position);
        }
        reindex();
    }

    /** 启用 / 停用隧道：整段加或去掉一个 {@code #} 前缀。 */
    void setEnabled(String name, boolean enabled) {
        Section section = require(name);
        if (section.isCommon()) {
            throw new IllegalArgumentException("common 段不支持停用");
        }
        if (section.disabled == !enabled) {
            return;
        }
        for (int i = section.headerIndex; i < section.endIndex; i++) {
            String raw = lines.get(i);
            String text = body(raw);
            String ending = ending(raw);
            if (text.isBlank()) {
                continue;
            }
            lines.set(i, enabled ? stripComment(text) + ending : "#" + text + ending);
        }
        reindex();
    }

    // ------------------------------------------------------------------
    // 内部：键值改写
    // ------------------------------------------------------------------

    private void setKey(String name, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        Section section = require(name);
        List<Integer> found = keyLines(section, key);
        String normalized = value == null ? "" : value.trim();
        // 值为空或字面量 null 都表示删除该键，避免把 null 写进配置文件。
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            deleteDescending(found);
            reindex();
            return;
        }
        if (found.isEmpty()) {
            String prefix = section.disabled ? "#" : "";
            String newline = newline();
            lines.add(section.headerIndex + 1, prefix + key.trim() + " = " + normalized + newline);
        } else {
            int first = found.get(0);
            String quoted = body(lines.get(first)).trim().startsWith("#") ? "#" : "";
            lines.set(first, quoted + key.trim() + " = " + normalized + ending(lines.get(first)));
            deleteDescending(found.subList(1, found.size()));
        }
        reindex();
    }

    private List<Integer> keyLines(Section section, String key) {
        List<Integer> found = new ArrayList<>();
        for (int i = section.headerIndex + 1; i < section.endIndex; i++) {
            String candidate = body(lines.get(i));
            if (section.disabled && candidate.trim().startsWith("#")) {
                candidate = stripComment(candidate);
            }
            if (candidate.trim().startsWith("#")) {
                continue;
            }
            Matcher matcher = KEY.matcher(candidate);
            if (matcher.matches() && matcher.group(1).equalsIgnoreCase(key.trim())) {
                found.add(i);
            }
        }
        return found;
    }

    /** 倒序删除，保证下标不失效。 */
    private void deleteDescending(List<Integer> indexes) {
        for (int i = indexes.size() - 1; i >= 0; i--) {
            int index = indexes.get(i);
            if (index >= 0 && index < lines.size()) {
                lines.remove(index);
            }
        }
    }

    private void deleteRange(int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (i >= 0 && i < lines.size()) {
                lines.remove(i);
            }
        }
    }

    private Section require(String name) {
        Section section = find(name);
        if (section == null) {
            throw new IllegalArgumentException("隧道不存在：" + name);
        }
        return section;
    }

    private String headerLine(String name, boolean disabled, int index) {
        String ending = ending(lines.get(index));
        return (disabled ? "#" : "") + "[" + name + "]" + ending;
    }

    // ------------------------------------------------------------------
    // 内部：解析
    // ------------------------------------------------------------------

    private void reindex() {
        sections.clear();
        Section current = null;
        for (int i = 0; i < lines.size(); i++) {
            String text = body(lines.get(i));
            String name = null;
            boolean disabled = false;
            if (!text.trim().startsWith("#")) {
                Matcher matcher = HEADER.matcher(text);
                if (matcher.matches()) {
                    name = matcher.group(1).trim();
                }
            } else {
                Matcher matcher = DISABLED_HEADER.matcher(text);
                if (matcher.matches()) {
                    name = matcher.group(1).trim();
                    disabled = true;
                }
            }
            if (name == null) {
                if (current != null) {
                    addKey(current, text);
                }
                continue;
            }
            if (current != null) {
                current.endIndex = i;
            }
            current = new Section();
            current.name = name;
            current.disabled = disabled;
            current.headerIndex = i;
            current.endIndex = lines.size();
            sections.put(name, current);
        }
    }

    private void addKey(Section section, String text) {
        String candidate = text;
        if (section.disabled) {
            if (!candidate.trim().startsWith("#")) {
                return;
            }
            candidate = stripComment(candidate);
        }
        if (candidate.trim().startsWith("#")) {
            return;
        }
        Matcher matcher = KEY.matcher(candidate);
        if (matcher.matches()) {
            section.keys.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).trim());
        }
    }

    /** 去掉行尾换行，得到可视文本。 */
    private static String body(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) {
            end--;
        }
        return line.substring(0, end);
    }

    /** 取行尾换行符（
 / 
 / 空）。 */
    private static String ending(String line) {
        if (line.endsWith("\r\n")) {
            return "\r\n";
        }
        if (line.endsWith("\n")) {
            return "\n";
        }
        if (line.endsWith("\r")) {
            return "\r";
        }
        return "";
    }

    private static String stripComment(String text) {
        int index = text.indexOf('#');
        if (index < 0) {
            return text;
        }
        return text.substring(0, index) + text.substring(index + 1);
    }

    private String newline() {
        for (String line : lines) {
            String ending = ending(line);
            if (!ending.isEmpty()) {
                return ending;
            }
        }
        return "\n";
    }
}
