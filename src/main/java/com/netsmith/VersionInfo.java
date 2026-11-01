package com.netsmith;

import java.io.InputStream;
import java.util.Properties;

/**
 * 版本信息工具类
 * <p>
 * 从 classpath 下的 version.properties 读取版本号，该文件由 Gradle 在
 * processResources 阶段用 build.gradle 中的 cfgVersion 填充，确保打包后的
 * 应用、安装包版本号与构建脚本中的版本号完全一致。
 * <p>
 * 这样整个项目只有一个版本号来源 (build.gradle 的 cfgVersion)，避免多处硬编码
 * 不同步的问题，更新检查也基于此版本号进行。
 */
public final class VersionInfo {

    private static final String VERSION;
    private static final String APP_NAME = "网匠";

    static {
        Properties props = new Properties();
        try (InputStream is = VersionInfo.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            System.err.println("VersionInfo: 加载版本信息失败: " + e.getMessage());
        }
        String v = props.getProperty("app.version");
        VERSION = (v != null && !v.isEmpty()) ? v : "0.0.0";
    }

    private VersionInfo() {
    }

    /** 获取纯版本号，例如 "1.5" */
    public static String getVersion() {
        return VERSION;
    }

    /** 获取带 v 前缀的显示版本号，例如 "v1.5" */
    public static String getDisplayVersion() {
        return "v" + VERSION;
    }

    /** 获取应用显示名称 */
    public static String getAppName() {
        return APP_NAME;
    }
}
