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
        /** frpc 可执行文件路径，留空表示尚未配置 */
        private String binary = "";

        public String getBinary() {
            return binary;
        }

        public void setBinary(String binary) {
            this.binary = binary;
        }
    }

    public static class Docker {
        /** docker 可执行文件路径，留空表示自动探测 PATH */
        private String binary = "";

        public String getBinary() {
            return binary;
        }

        public void setBinary(String binary) {
            this.binary = binary;
        }
    }
}