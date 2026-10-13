package com.huluwa.console.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 电影天堂（dyttzyw.com）域名解析修正：JDK 18+ 的 {@link InetAddressResolverProvider} SPI。
 *
 * <p><b>背景</b>：dyttzyw.com 在部分运营商 / 内网 DNS 下被投毒，解析到 221.228.32.13
 * （江苏反诈拦截页），表现为 HTTP 302 跳转、HTTPS 直接拒连；而站点真实 IP 直连完全正常。
 * 该 IP 是 DNS 层面的劫持，改 Hosts 或换网络才能绕过，应用侧无法通过重试恢复。</p>
 *
 * <p><b>策略</b>（自愈优先，避免站点换 IP 后写死的地址失效）：
 * <ol>
 *   <li>先用系统解析器的结果；</li>
 *   <li>结果为空、或命中已知投毒地址 / 保留地址（回环、私网、0.0.0.0）时，
 *       改用 {@link #pinnedIps()} 里的真实地址；</li>
 *   <li>只接管 dyttzyw.com 与其 www 子域，其余域名一律走系统解析（绝不影响其它站点）。</li>
 * </ol>
 *
 * <p>真实地址可用系统属性 {@code huluwa.dytt.ips} 或环境变量 {@code HULUWA_DYTT_IPS}
 * （英文逗号分隔）覆盖，站点更换 CDN 时无需改代码。</p>
 */
public class DyttDnsResolver extends InetAddressResolverProvider {

    private static final Logger log = LoggerFactory.getLogger(DyttDnsResolver.class);

    /** 需要接管解析的域名（小写） */
    private static final Set<String> MANAGED = Set.of("dyttzyw.com", "www.dyttzyw.com");

    /** 已知的 DNS 投毒地址（江苏反诈拦截页） */
    private static final Set<String> POISONED = Set.of("221.228.32.13");

    /** 站点真实地址（DoH 解析结果，可用配置覆盖） */
    private static final String DEFAULT_PINNED = "27.124.10.41,23.224.149.4";

    private static final String CONFIG_KEY = "huluwa.dytt.ips";
    private static final String ENV_KEY = "HULUWA_DYTT_IPS";

    @Override
    public String name() {
        return "huluwa-dytt-resolver";
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        List<String> pinned = pinnedIps();
        return new InetAddressResolver() {

            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
                if (host == null || !MANAGED.contains(host.toLowerCase(Locale.ROOT))) {
                    return builtin.lookupByName(host, lookupPolicy);
                }
                Stream<InetAddress> resolved;
                try {
                    resolved = builtin.lookupByName(host, lookupPolicy);
                } catch (UnknownHostException e) {
                    resolved = Stream.empty();
                }
                List<InetAddress> candidates = resolved.toList();
                if (trustworthy(candidates)) {
                    return candidates.stream();
                }
                List<InetAddress> fallback = new ArrayList<>();
                for (String ip : pinned) {
                    try {
                        fallback.add(InetAddress.getByAddress(host, literal(ip)));
                    } catch (UnknownHostException e) {
                        log.warn("电影天堂备用地址无效，已跳过：{}", ip);
                    }
                }
                if (fallback.isEmpty()) {
                    return candidates.stream();
                }
                log.info("电影天堂域名解析修正：{} 的系统解析结果 {} 不可用，改用真实地址 {}",
                        host, candidates.stream().map(InetAddress::getHostAddress).toList(),
                        fallback.stream().map(InetAddress::getHostAddress).toList());
                return fallback.stream();
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    /** 判断系统解析结果是否可用：非空、且不含投毒地址与保留地址 */
    static boolean trustworthy(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return false;
        }
        for (InetAddress address : addresses) {
            if (isSuspicious(address)) {
                return false;
            }
        }
        return true;
    }

    /** 投毒地址、回环、链路本地、私网、通配地址都视为不可信 */
    static boolean isSuspicious(InetAddress address) {
        if (address == null) {
            return true;
        }
        String ip = address.getHostAddress();
        if (POISONED.contains(ip)) {
            return true;
        }
        return address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress();
    }

    /** 备用真实地址：优先系统属性，其次环境变量，都没有时用内置默认值 */
    static List<String> pinnedIps() {
        String configured = System.getProperty(CONFIG_KEY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENV_KEY);
        }
        String source = configured == null || configured.isBlank() ? DEFAULT_PINNED : configured;
        List<String> list = new ArrayList<>();
        for (String part : Arrays.asList(source.split(","))) {
            String ip = part.trim();
            if (!ip.isEmpty()) {
                list.add(ip);
            }
        }
        return list.isEmpty() ? Arrays.asList(DEFAULT_PINNED.split(",")) : list;
    }

    /** 点分十进制字面量 → 字节数组（不做任何 DNS 查询） */
    private static byte[] literal(String ip) throws UnknownHostException {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new UnknownHostException("不是合法的 IPv4 地址：" + ip);
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int value;
            try {
                value = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new UnknownHostException("不是合法的 IPv4 地址：" + ip);
            }
            if (value < 0 || value > 255) {
                throw new UnknownHostException("不是合法的 IPv4 地址：" + ip);
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }
}