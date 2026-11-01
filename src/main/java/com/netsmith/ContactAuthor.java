package com.netsmith;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContactAuthor {

    private static final int DELETE_DELAY_MS = 120000; // 2分钟后删除，确保浏览器有充足时间打开

    /**
     * 打开联系作者页面
     * @return true 表示页面已成功打开，false 表示失败
     */
    public static boolean showContactInfo() {
        try {
            Path tempFile = Files.createTempFile("contact_author_", ".html");
            tempFile.toFile().deleteOnExit();
            Files.write(tempFile, getHtmlContent().getBytes(StandardCharsets.UTF_8));

            // 1. 尝试 Desktop.open() — 最标准的跨平台方式
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    try {
                        desktop.open(tempFile.toFile());

                        Thread deleteThread = new Thread(() -> {
                            try {
                                Thread.sleep(DELETE_DELAY_MS);
                                Files.deleteIfExists(tempFile);
                            } catch (Exception ignored) {}
                        });
                        deleteThread.setDaemon(true);
                        deleteThread.start();
                        return true;
                    } catch (IOException ignored) {
                        // Desktop.open 失败，继续尝试下一种方式
                    }
                }
            }

            // 2. 尝试 Windows cmd start 方式（最可靠的本地文件打开方式）
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("windows")) {
                try {
                    String path = tempFile.toAbsolutePath().toString();
                    // ProcessBuilder + 独立的空标题参数（start 的第一个引号参数是窗口标题），
                    // 路径含空格时由 ProcessBuilder 自动加引号，避免被 cmd 元字符（& ^ | 等）干扰
                    new ProcessBuilder("cmd", "/c", "start", "\"\"", path).start();
                    Thread deleteThread = new Thread(() -> {
                        try {
                            Thread.sleep(DELETE_DELAY_MS);
                            Files.deleteIfExists(tempFile);
                        } catch (Exception ignored) {}
                    });
                    deleteThread.setDaemon(true);
                    deleteThread.start();
                    return true;
                } catch (IOException ignored) {
                    // start 失败，继续下一种方式
                }
            }

            // 3. 都失败了：显示文件路径提示用户手动打开
            String path = tempFile.toAbsolutePath().toString();
            JOptionPane.showMessageDialog(null,
                    "无法自动打开浏览器。\n请手动打开以下文件查看联系信息：\n" + path,
                    "联系作者",
                    JOptionPane.INFORMATION_MESSAGE);
            return false;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "无法创建联系页面文件：" + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    private static String getHtmlContent() {
        return "<!DOCTYPE html>\n" +
"<html lang=\"zh-CN\">\n" +
"<head>\n" +
"    <meta charset=\"UTF-8\">\n" +
"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
"    <title>联系作者 | 网匠</title>\n" +
"    <style>\n" +
"        /* 全局设计变量 - 全新配色体系 */\n" +
"        :root {\n" +
"            /* 主色渐变系统 */\n" +
"            --gradient-primary: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);\n" +
"            --gradient-secondary: linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%);\n" +
"            --gradient-soft: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);\n" +
"\n" +
"            /* 中性色阶 */\n" +
"            --bg-page: #f1f5f9;\n" +
"            --bg-card: rgba(255, 255, 255, 0.7);\n" +
"            --border-card: rgba(255, 255, 255, 0.8);\n" +
"            --text-primary: #1e293b;\n" +
"            --text-secondary: #64748b;\n" +
"            --text-muted: #94a3b8;\n" +
"            --text-white: #ffffff;\n" +
"\n" +
"            /* 质感参数 */\n" +
"            --blur-strength: 20px;\n" +
"            --shadow-card: 0 8px 32px rgba(31, 38, 135, 0.07);\n" +
"            --shadow-card-hover: 0 20px 60px rgba(31, 38, 135, 0.15);\n" +
"            --radius-xl: 24px;\n" +
"            --radius-lg: 16px;\n" +
"            --radius-md: 12px;\n" +
"\n" +
"            /* 动效曲线 */\n" +
"            --ease-smooth: cubic-bezier(0.4, 0, 0.2, 1);\n" +
"            --transition-base: all 0.4s var(--ease-smooth);\n" +
"            --transition-fast: all 0.25s var(--ease-smooth);\n" +
"        }\n" +
"\n" +
"        /* 全局重置 */\n" +
"        * {\n" +
"            margin: 0;\n" +
"            padding: 0;\n" +
"            box-sizing: border-box;\n" +
"        }\n" +
"\n" +
"        body {\n" +
"            font-family: -apple-system, BlinkMacSystemFont, \"PingFang SC\", \"Microsoft YaHei\", sans-serif;\n" +
"            background: var(--bg-page);\n" +
"            color: var(--text-primary);\n" +
"            line-height: 1.8;\n" +
"            min-height: 100vh;\n" +
"            position: relative;\n" +
"            overflow-x: hidden;\n" +
"        }\n" +
"\n" +
"        /* 背景氛围装饰 - 渐变光斑 */\n" +
"        .bg-decoration {\n" +
"            position: fixed;\n" +
"            border-radius: 50%;\n" +
"            filter: blur(100px);\n" +
"            z-index: -1;\n" +
"            opacity: 0.4;\n" +
"            animation: float 20s ease-in-out infinite;\n" +
"        }\n" +
"        .decoration-1 {\n" +
"            width: 500px;\n" +
"            height: 500px;\n" +
"            background: #6366f1;\n" +
"            top: -200px;\n" +
"            left: -150px;\n" +
"        }\n" +
"        .decoration-2 {\n" +
"            width: 600px;\n" +
"            height: 600px;\n" +
"            background: #8b5cf6;\n" +
"            bottom: -250px;\n" +
"            right: -200px;\n" +
"            animation-delay: -10s;\n" +
"        }\n" +
"        @keyframes float {\n" +
"            0%, 100% { transform: translate(0, 0) scale(1); }\n" +
"            50% { transform: translate(30px, -30px) scale(1.05); }\n" +
"        }\n" +
"\n" +
"        /* 渐变文字通用类 */\n" +
"        .text-gradient {\n" +
"            background: var(--gradient-primary);\n" +
"            -webkit-background-clip: text;\n" +
"            -webkit-text-fill-color: transparent;\n" +
"            background-clip: text;\n" +
"            font-weight: 700;\n" +
"        }\n" +
"\n" +
"        /* 头部Hero区 - 通透渐变设计 */\n" +
"        header {\n" +
"            background: var(--gradient-primary);\n" +
"            color: var(--text-white);\n" +
"            padding: 6rem 2rem 5rem;\n" +
"            text-align: center;\n" +
"            position: relative;\n" +
"            overflow: hidden;\n" +
"        }\n" +
"        header::before {\n" +
"            content: \"\";\n" +
"            position: absolute;\n" +
"            top: 0;\n" +
"            left: 0;\n" +
"            width: 100%;\n" +
"            height: 100%;\n" +
"            background: url(\"data:image/svg+xml,%3Csvg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='%23ffffff' fill-opacity='0.05'%3E%3Cpath d='M20 20.5V18H0v-2h20v-2H0v-2h20v-2H0V8h20V6H0V4h20V2H0V0h22v20h2V0h2v20h2V0h2v20h2V0h2v20h2V0h2v22H20v-1.5zM0 20h2v20H0V20zm4 0h2v20H4V20zm4 0h2v20H8V20zm4 0h2v20h-2V20zm4 0h2v20h-2V20zm4 4h20v2H20v-2zm0 4h20v2H20v-2zm0 4h20v2H20v-2zm0 4h20v2H20v-2z'/%3E%3C/g%3E%3C/svg%3E\");\n" +
"            opacity: 0.3;\n" +
"        }\n" +
"        .header-content {\n" +
"            max-width: 800px;\n" +
"            margin: 0 auto;\n" +
"            position: relative;\n" +
"            z-index: 1;\n" +
"            animation: fadeInDown 0.8s var(--ease-smooth);\n" +
"        }\n" +
"        header h1 {\n" +
"            font-size: 3.2rem;\n" +
"            font-weight: 700;\n" +
"            margin-bottom: 1rem;\n" +
"            letter-spacing: 1px;\n" +
"        }\n" +
"        header p {\n" +
"            font-size: 1.2rem;\n" +
"            opacity: 0.9;\n" +
"            font-weight: 300;\n" +
"        }\n" +
"        @keyframes fadeInDown {\n" +
"            from { opacity: 0; transform: translateY(-30px); }\n" +
"            to { opacity: 1; transform: translateY(0); }\n" +
"        }\n" +
"\n" +
"        /* 主内容区 - 不对称网格布局 */\n" +
"        .container {\n" +
"            max-width: 1200px;\n" +
"            margin: -3rem auto 5rem;\n" +
"            padding: 0 24px;\n" +
"            position: relative;\n" +
"            z-index: 2;\n" +
"        }\n" +
"        .contact-wrapper {\n" +
"            display: grid;\n" +
"            grid-template-columns: 3fr 2fr;\n" +
"            gap: 2rem;\n" +
"            animation: fadeInUp 0.8s var(--ease-smooth) 0.2s both;\n" +
"        }\n" +
"        @keyframes fadeInUp {\n" +
"            from { opacity: 0; transform: translateY(30px); }\n" +
"            to { opacity: 1; transform: translateY(0); }\n" +
"        }\n" +
"\n" +
"        /* 玻璃拟态卡片 */\n" +
"        .software-info, .contact-info {\n" +
"            background: var(--bg-card);\n" +
"            backdrop-filter: blur(var(--blur-strength));\n" +
"            -webkit-backdrop-filter: blur(var(--blur-strength));\n" +
"            border: 1px solid var(--border-card);\n" +
"            border-radius: var(--radius-xl);\n" +
"            padding: 2.5rem;\n" +
"            box-shadow: var(--shadow-card);\n" +
"            transition: var(--transition-base);\n" +
"            position: relative;\n" +
"            overflow: hidden;\n" +
"        }\n" +
"        .software-info:hover, .contact-info:hover {\n" +
"            transform: translateY(-6px);\n" +
"            box-shadow: var(--shadow-card-hover);\n" +
"            border-color: rgba(255, 255, 255, 1);\n" +
"        }\n" +
"        /* 卡片顶部光效装饰 */\n" +
"        .software-info::after, .contact-info::after {\n" +
"            content: \"\";\n" +
"            position: absolute;\n" +
"            top: 0;\n" +
"            left: 0;\n" +
"            width: 100%;\n" +
"            height: 4px;\n" +
"            background: var(--gradient-primary);\n" +
"        }\n" +
"        .contact-info::after {\n" +
"            background: var(--gradient-secondary);\n" +
"        }\n" +
"\n" +
"        /* 卡片标题重构 */\n" +
"        .software-info h2, .contact-info h2 {\n" +
"            font-size: 1.6rem;\n" +
"            color: var(--text-primary);\n" +
"            margin-bottom: 2rem;\n" +
"            padding-bottom: 0.8rem;\n" +
"            position: relative;\n" +
"            font-weight: 600;\n" +
"            display: inline-block;\n" +
"        }\n" +
"        .software-info h2::after, .contact-info h2::after {\n" +
"            content: \"\";\n" +
"            position: absolute;\n" +
"            bottom: 0;\n" +
"            left: 0;\n" +
"            width: 60%;\n" +
"            height: 3px;\n" +
"            border-radius: 3px;\n" +
"            background: var(--gradient-primary);\n" +
"        }\n" +
"        .contact-info h2::after {\n" +
"            background: var(--gradient-secondary);\n" +
"        }\n" +
"\n" +
"        /* 信息项重构 - 图标圆形渐变背景 */\n" +
"        .software-item, .info-item {\n" +
"            display: flex;\n" +
"            align-items: flex-start;\n" +
"            margin-bottom: 1.8rem;\n" +
"            padding: 1rem;\n" +
"            border-radius: var(--radius-md);\n" +
"            transition: var(--transition-fast);\n" +
"        }\n" +
"        .software-item:last-child, .info-item:last-child {\n" +
"            margin-bottom: 0;\n" +
"        }\n" +
"        .software-item:hover, .info-item:hover {\n" +
"            background: var(--gradient-soft);\n" +
"            transform: translateX(4px);\n" +
"        }\n" +
"\n" +
"        .item-icon {\n" +
"            width: 48px;\n" +
"            height: 48px;\n" +
"            border-radius: 12px;\n" +
"            background: var(--gradient-primary);\n" +
"            display: flex;\n" +
"            align-items: center;\n" +
"            justify-content: center;\n" +
"            color: white;\n" +
"            font-size: 1.25rem;\n" +
"            margin-right: 1.25rem;\n" +
"            flex-shrink: 0;\n" +
"            transition: var(--transition-fast);\n" +
"        }\n" +
"        .contact-info .item-icon {\n" +
"            background: var(--gradient-secondary);\n" +
"        }\n" +
"        .software-item:hover .item-icon, .info-item:hover .item-icon {\n" +
"            transform: scale(1.1) rotate(-5deg);\n" +
"            box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);\n" +
"        }\n" +
"\n" +
"        .item-content {\n" +
"            flex: 1;\n" +
"            padding-top: 2px;\n" +
"        }\n" +
"        .item-content .label {\n" +
"            font-size: 0.9rem;\n" +
"            color: var(--text-secondary);\n" +
"            margin-bottom: 2px;\n" +
"            font-weight: 500;\n" +
"        }\n" +
"        .item-content .value, .item-content span {\n" +
"            font-size: 1.05rem;\n" +
"            color: var(--text-primary);\n" +
"            font-weight: 500;\n" +
"        }\n" +
"\n" +
"        /* 链接样式重构 */\n" +
"        .info-item a {\n" +
"            color: #6366f1;\n" +
"            text-decoration: none;\n" +
"            position: relative;\n" +
"            transition: var(--transition-fast);\n" +
"            font-weight: 500;\n" +
"        }\n" +
"        .info-item a::after {\n" +
"            content: \"\";\n" +
"            position: absolute;\n" +
"            bottom: -2px;\n" +
"            left: 0;\n" +
"            width: 0;\n" +
"            height: 2px;\n" +
"            background: var(--gradient-primary);\n" +
"            border-radius: 2px;\n" +
"            transition: var(--transition-base);\n" +
"        }\n" +
"        .info-item a:hover {\n" +
"            color: #8b5cf6;\n" +
"        }\n" +
"        .info-item a:hover::after {\n" +
"            width: 100%;\n" +
"        }\n" +
"\n" +
"        /* 页脚重构 */\n" +
"        footer {\n" +
"            background: var(--gradient-primary);\n" +
"            color: var(--text-white);\n" +
"            text-align: center;\n" +
"            padding: 2rem;\n" +
"            position: relative;\n" +
"            overflow: hidden;\n" +
"        }\n" +
"        footer::before {\n" +
"            content: \"\";\n" +
"            position: absolute;\n" +
"            top: 0;\n" +
"            left: 0;\n" +
"            width: 100%;\n" +
"            height: 100%;\n" +
"            background: url(\"data:image/svg+xml,%3Csvg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='%23ffffff' fill-opacity='0.05'%3E%3Cpath d='M20 20.5V18H0v-2h20v-2H0v-2h20v-2H0V8h20V6H0V4h20V2H0V0h22v20h2V0h2v20h2V0h2v20h2V0h2v20h2V0h2v22H20v-1.5zM0 20h2v20H0V20zm4 0h2v20H4V20zm4 0h2v20H8V20zm4 0h2v20h-2V20zm4 0h2v20h-2V20zm4 4h20v2H20v-2zm0 4h20v2H20v-2zm0 4h20v2H20v-2zm0 4h20v2H20v-2z'/%3E%3C/g%3E%3C/svg%3E\");\n" +
"            opacity: 0.3;\n" +
"        }\n" +
"        footer p {\n" +
"            position: relative;\n" +
"            z-index: 1;\n" +
"            font-size: 1rem;\n" +
"            opacity: 0.95;\n" +
"            font-weight: 300;\n" +
"        }\n" +
"        footer .text-gradient {\n" +
"            background: #ffffff;\n" +
"            -webkit-background-clip: text;\n" +
"            -webkit-text-fill-color: transparent;\n" +
"            background-clip: text;\n" +
"            font-weight: 600;\n" +
"        }\n" +
"\n" +
"        /* 响应式适配 */\n" +
"        @media (max-width: 992px) {\n" +
"            .contact-wrapper {\n" +
"                grid-template-columns: 1fr;\n" +
"                gap: 1.5rem;\n" +
"            }\n" +
"            header h1 {\n" +
"                font-size: 2.5rem;\n" +
"            }\n" +
"        }\n" +
"        @media (max-width: 576px) {\n" +
"            header {\n" +
"                padding: 4rem 1.5rem 3.5rem;\n" +
"            }\n" +
"            header h1 {\n" +
"                font-size: 2rem;\n" +
"            }\n" +
"            header p {\n" +
"                font-size: 1rem;\n" +
"            }\n" +
"            .software-info, .contact-info {\n" +
"                padding: 1.8rem 1.5rem;\n" +
"            }\n" +
"            .container {\n" +
"                margin-top: -2rem;\n" +
"            }\n" +
"        }\n" +
"    </style>\n" +
"    <link rel=\"stylesheet\" href=\"https://cdn.bootcdn.net/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n" +
"</head>\n" +
"<body>\n" +
"    <!-- 背景氛围装饰 -->\n" +
"    <div class=\"bg-decoration decoration-1\"></div>\n" +
"    <div class=\"bg-decoration decoration-2\"></div>\n" +
"\n" +
"    <header>\n" +
"        <div class=\"header-content\">\n" +
"            <h1>联系作者 <span class=\"text-gradient\" style=\"background: linear-gradient(135deg, #fff 0%, #e0e7ff 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent;\">Ivan</span></h1>\n" +
"            <p>有任何问题、建议或合作意向，欢迎随时与我取得联系</p>\n" +
"        </div>\n" +
"    </header>\n" +
"\n" +
"    <main class=\"container\">\n" +
"        <div class=\"contact-wrapper\">\n" +
"            <section class=\"software-info\">\n" +
"                <h2>软件信息</h2>\n" +
"                <div class=\"software-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fas fa-file-code\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">软件名称</div>\n" +
"                        <div class=\"value\">网匠</div>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"software-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fas fa-tag\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">软件版本</div>\n" +
"                        <div class=\"value\">" + VersionInfo.getDisplayVersion() + "</div>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"software-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fas fa-desktop\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">适配系统</div>\n" +
"                        <div class=\"value\">Windows 7/8/10/11（64位）</div>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"software-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fas fa-info-circle\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">软件说明</div>\n" +
"                        <div class=\"value\">基于 Java Swing 开发的桌面级网络计算工具，同时支持 IPv4 与 IPv6 双栈环境，面向网络工程师、系统管理员及运维人员设计，提供从基础地址解析到复杂网络规划的一站式计算能力，会持续优化和完善更多功能，敬请期待！</div>\n" +
"                    </div>\n" +
"                </div>\n" +
"            </section>\n" +
"\n" +
"            <section class=\"contact-info\">\n" +
"                <h2>联系方式</h2>\n" +
"                <div class=\"info-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fab fa-qq\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">QQ</div>\n" +
"                        <span><a href=\"https://wpa.qq.com/msgrd?v=3&uin=2195928963&site=qq&menu=yes\" target=\"_blank\">2195928963</a></span>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"info-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fab fa-weixin\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">微信</div>\n" +
"                        <span>Moore_Ivan</span>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"info-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fab fa-tiktok\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">抖音</div>\n" +
"                        <span>Moore_Ivan</span>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"info-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fab fa-github\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">GitHub</div>\n" +
"                        <span><a href=\"https://github.com/Moore-Ivan\" target=\"_blank\">github.com/Moore-Ivan</a></span>\n" +
"                    </div>\n" +
"                </div>\n" +
"                <div class=\"info-item\">\n" +
"                    <div class=\"item-icon\">\n" +
"                        <i class=\"fas fa-envelope\"></i>\n" +
"                    </div>\n" +
"                    <div class=\"item-content\">\n" +
"                        <div class=\"label\">邮箱</div>\n" +
"                        <span><a href=\"mailto:Moore_Ivan@foxmail.com\">Moore_Ivan@foxmail.com</a></span>\n" +
"                    </div>\n" +
"                </div>\n" +
"            </section>\n" +
"        </div>\n" +
"    </main>\n" +
"\n" +
"    <footer>\n" +
"        <p>&copy;  <span class=\"text-gradient\">Ivan</span> 的个人博客 - 保留所有权利</p>\n" +
"    </footer>\n" +
"</body>\n" +
"</html>";
    }
}