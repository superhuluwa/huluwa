package com.huluwa.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置，前缀 huluwa。
 */
@ConfigurationProperties(prefix = "huluwa")
public class AppProperties {

    /** 数据目录：SQLite 库文件、运行期文件都放这里 */
    private String dataDir = "./data";

    private Token token = new Token();
    private Frp frp = new Frp();
    private Docker docker = new Docker();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Token getToken() {
        return token;
    }

    public void setToken(Token token) {
        this.token = token;
    }

    public Frp getFrp() {
        return frp;
    }

    public void setFrp(Frp frp) {
        this.frp = frp;
    }

    public Docker getDocker() {
        return docker;
    }

    public void setDocker(Docker docker) {
        this.docker = docker;
    }

    public static class Token {
        /** 留空则由应用首次启动生成并写入数据库 */
        private String secret = "";
        private int expireHours = 72;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getExpireHours() {
            return expireHours;
        }

        public void setExpireHours(int expireHours) {
            this.expireHours = expireHours;
        }
    }

    public static class Frp {
        /** frpc 容器名：状态查询、热加载与重启都作用于它（docker restart frpc）。 */
        private String container = "frpc";

        /** 容器内 frpc 可执行文件路径，用于配置语法校验与热加载。 */
        private String binary = "frpc";

        /** 本容器（huluwa-console）内可见的配置文件路径：由宿主机 /root/frp 目录绑定挂载而来。 */
        private String configPath = "/app/frp/frpc.ini";

        /** frpc 容器内的等价路径，用于在 frpc 容器里执行 verify / reload。 */
        private String containerConfigPath = "/etc/frp/frpc.ini";

        /** 宿主机上的真实路径，仅用于界面展示。 */
        private String hostPath = "/root/frp/frpc.ini";

        public String getContainer() {
            return container;
        }

        public void setContainer(String container) {
            this.container = container;
        }

        public String getBinary() {
            return binary;
        }

        public void setBinary(String binary) {
            this.binary = binary;
        }

        public String getConfigPath() {
            return configPath;
        }

        public void setConfigPath(String configPath) {
            this.configPath = configPath;
        }

        public String getContainerConfigPath() {
            return containerConfigPath;
        }

        public void setContainerConfigPath(String containerConfigPath) {
            this.containerConfigPath = containerConfigPath;
        }

        public String getHostPath() {
            return hostPath;
        }

        public void setHostPath(String hostPath) {
            this.hostPath = hostPath;
        }
    }

    public static class Docker {
        /**
         * docker 可执行文件路径。仅保留用于兼容历史配置；当前基于 docker-java SDK 直连守护进程，
         * 不再调用 docker CLI。
         */
        private String binary = "";

        /**
         * Docker 守护进程地址。留空时依次回退到 DOCKER_HOST 环境变量与系统默认值
         * （Linux：unix:///var/run/docker.sock，Windows：npipe:////./pipe/docker_engine）。
         */
        private String host = "";

        /**
         * Docker API 版本。留空时应用自行从守护进程 /version 协商可用版本，
         * 兼容 CentOS 上常见的 19.03 / 20.10 等旧版 Docker。
         */
        private String apiVersion = "";

        /** 建立 Docker 连接的超时（秒）。 */
        private int connectionTimeoutSeconds = 10;

        /** Docker API 响应超时（秒）；镜像拉取等长耗时可单独调大。 */
        private int responseTimeoutSeconds = 300;

        public String getBinary() {
            return binary;
        }

        public void setBinary(String binary) {
            this.binary = binary;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds <= 0 ? 10 : connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }

        public int getResponseTimeoutSeconds() {
            return responseTimeoutSeconds <= 0 ? 300 : responseTimeoutSeconds;
        }

        public void setResponseTimeoutSeconds(int responseTimeoutSeconds) {
            this.responseTimeoutSeconds = responseTimeoutSeconds;
        }
    }
}