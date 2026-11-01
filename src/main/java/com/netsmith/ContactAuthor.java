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
        // @@VERSION@@ 占位符注入动态版本号（HTML 中不含 @@ 字符，无冲突）
        return HTML_TEMPLATE.replace("@@VERSION@@", VersionInfo.getDisplayVersion());
    }

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>联系作者 | 网匠</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link href="https://fonts.googleapis.com/css2?family=Syne:wght@400;600;700;800&family=DM+Mono:wght@300;400;500&family=Noto+Sans+SC:wght@300;400;500;700&display=swap" rel="stylesheet">
                <link rel="stylesheet" href="https://cdn.bootcdn.net/ajax/libs/font-awesome/6.4.0/css/all.min.css">
                <style>
                    :root {
                        --glass-bg: rgba(255, 255, 255, 0.06);
                        --glass-bg-hover: rgba(255, 255, 255, 0.1);
                        --glass-border: rgba(255, 255, 255, 0.12);
                        --glass-border-hover: rgba(255, 255, 255, 0.25);
                        --glass-blur: 24px;
                        --glass-blur-heavy: 40px;
                        --glass-inner-glow: inset 0 1px 0 rgba(255,255,255,0.1);
                        --glass-shadow: 0 8px 32px rgba(0,0,0,0.25), 0 2px 8px rgba(0,0,0,0.15);
                        --glass-shadow-hover: 0 20px 60px rgba(0,0,0,0.35), 0 4px 12px rgba(0,0,0,0.2);
                        --text-bright: #f0f0f5;
                        --text-soft: rgba(240, 240, 245, 0.7);
                        --text-muted: rgba(240, 240, 245, 0.4);
                        --accent: #7dd3fc;
                        --radius-xl: 28px;
                        --radius-lg: 20px;
                        --radius-md: 14px;
                        --ease-liquid: cubic-bezier(0.22, 1, 0.36, 1);
                    }

                    * { margin: 0; padding: 0; box-sizing: border-box; }

                    body {
                        font-family: 'Noto Sans SC', 'DM Mono', sans-serif;
                        min-height: 100vh;
                        overflow-x: hidden;
                        color: var(--text-bright);
                        line-height: 1.7;
                        position: relative;
                    }

                    .liquid-bg {
                        position: fixed;
                        inset: 0;
                        z-index: -2;
                        background: #0a0a1a;
                    }

                    .liquid-blob {
                        position: absolute;
                        border-radius: 50%;
                        filter: blur(120px);
                        will-change: transform;
                    }
                    .blob-1 {
                        width: 600px; height: 600px;
                        background: radial-gradient(circle, #3b82f6 0%, #1e3a8a 70%, transparent 100%);
                        top: -15%; left: -10%;
                        animation: drift1 25s ease-in-out infinite;
                    }
                    .blob-2 {
                        width: 500px; height: 500px;
                        background: radial-gradient(circle, #8b5cf6 0%, #4c1d95 70%, transparent 100%);
                        top: 50%; right: -8%;
                        animation: drift2 30s ease-in-out infinite;
                    }
                    .blob-3 {
                        width: 450px; height: 450px;
                        background: radial-gradient(circle, #06b6d4 0%, #0e4a6b 70%, transparent 100%);
                        bottom: -10%; left: 30%;
                        animation: drift3 22s ease-in-out infinite;
                    }
                    .blob-4 {
                        width: 350px; height: 350px;
                        background: radial-gradient(circle, #a78bfa 0%, #4c1d95 70%, transparent 100%);
                        top: 20%; left: 50%;
                        animation: drift4 28s ease-in-out infinite;
                    }
                    .blob-5 {
                        width: 250px; height: 250px;
                        background: radial-gradient(circle, #22d3ee 0%, #155e75 70%, transparent 100%);
                        top: 70%; left: 10%;
                        animation: drift5 20s ease-in-out infinite;
                    }

                    @keyframes drift1 {
                        0%, 100% { transform: translate(0, 0) scale(1); }
                        33% { transform: translate(80px, 60px) scale(1.15); }
                        66% { transform: translate(-40px, 30px) scale(0.95); }
                    }
                    @keyframes drift2 {
                        0%, 100% { transform: translate(0, 0) scale(1); }
                        33% { transform: translate(-60px, -40px) scale(1.1); }
                        66% { transform: translate(40px, 50px) scale(0.9); }
                    }
                    @keyframes drift3 {
                        0%, 100% { transform: translate(0, 0) scale(1); }
                        33% { transform: translate(50px, -50px) scale(1.2); }
                        66% { transform: translate(-30px, -20px) scale(0.85); }
                    }
                    @keyframes drift4 {
                        0%, 100% { transform: translate(0, 0) scale(1); }
                        50% { transform: translate(-70px, 40px) scale(1.1); }
                    }
                    @keyframes drift5 {
                        0%, 100% { transform: translate(0, 0) scale(1); }
                        40% { transform: translate(60px, -30px) scale(1.15); }
                        70% { transform: translate(-20px, 50px) scale(0.9); }
                    }

                    .liquid-bg::after {
                        content: '';
                        position: absolute;
                        inset: 0;
                        background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E");
                        background-repeat: repeat;
                        opacity: 0.5;
                        pointer-events: none;
                    }

                    header {
                        position: relative;
                        padding: 7rem 2rem 6rem;
                        text-align: center;
                        overflow: hidden;
                    }

                    .header-glass {
                        position: absolute;
                        inset: 0;
                        backdrop-filter: blur(var(--glass-blur-heavy));
                        -webkit-backdrop-filter: blur(var(--glass-blur-heavy));
                        background: linear-gradient(180deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%);
                        border-bottom: 1px solid rgba(255,255,255,0.06);
                    }

                    header::before {
                        content: '';
                        position: absolute;
                        top: 0; left: 0;
                        width: 100%; height: 100%;
                        background: linear-gradient(135deg, rgba(59,130,246,0.08) 0%, rgba(139,92,246,0.08) 50%, rgba(6,182,212,0.05) 100%);
                        pointer-events: none;
                    }

                    .header-content {
                        position: relative;
                        z-index: 1;
                        max-width: 800px;
                        margin: 0 auto;
                        animation: revealDown 1s var(--ease-liquid) both;
                    }
                    @keyframes revealDown {
                        from { opacity: 0; transform: translateY(-40px) scale(0.97); filter: blur(8px); }
                        to { opacity: 1; transform: translateY(0) scale(1); filter: blur(0); }
                    }

                    header h1 {
                        font-family: 'Syne', 'Noto Sans SC', sans-serif;
                        font-size: clamp(2.2rem, 5vw, 3.6rem);
                        font-weight: 800;
                        letter-spacing: -0.02em;
                        margin-bottom: 1rem;
                        background: linear-gradient(135deg, #ffffff 0%, #7dd3fc 40%, #a78bfa 70%, #22d3ee 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        background-clip: text;
                        background-size: 200% 200%;
                        animation: shimmer 6s ease-in-out infinite;
                    }
                    @keyframes shimmer {
                        0%, 100% { background-position: 0% 50%; }
                        50% { background-position: 100% 50%; }
                    }

                    header p {
                        font-family: 'DM Mono', monospace;
                        font-size: clamp(0.85rem, 1.5vw, 1.05rem);
                        font-weight: 300;
                        color: var(--text-soft);
                        letter-spacing: 0.05em;
                    }

                    .header-line {
                        display: block;
                        width: 80px;
                        height: 3px;
                        margin: 1.5rem auto 0;
                        border-radius: 3px;
                        background: linear-gradient(90deg, #7dd3fc, #a78bfa, #22d3ee);
                        background-size: 200% 100%;
                        animation: shimmer 4s ease-in-out infinite;
                    }

                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        padding: 0 24px 4rem;
                    }

                    .contact-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 28px;
                        animation: revealUp 0.9s var(--ease-liquid) 0.2s both;
                    }
                    @keyframes revealUp {
                        from { opacity: 0; transform: translateY(50px); filter: blur(10px); }
                        to { opacity: 1; transform: translateY(0); filter: blur(0); }
                    }

                    .glass-card {
                        position: relative;
                        border-radius: var(--radius-xl);
                        overflow: hidden;
                        transition: all 0.5s var(--ease-liquid);
                    }
                    .glass-card:hover {
                        transform: translateY(-8px);
                    }

                    .glass-card::before {
                        content: '';
                        position: absolute;
                        inset: 0;
                        border-radius: var(--radius-xl);
                        border: 1px solid var(--glass-border);
                        backdrop-filter: blur(var(--glass-blur));
                        -webkit-backdrop-filter: blur(var(--glass-blur));
                        background: var(--glass-bg);
                        box-shadow: var(--glass-shadow), var(--glass-inner-glow);
                        transition: all 0.5s var(--ease-liquid);
                        z-index: 0;
                    }
                    .glass-card:hover::before {
                        background: var(--glass-bg-hover);
                        border-color: var(--glass-border-hover);
                        box-shadow: var(--glass-shadow-hover), var(--glass-inner-glow);
                    }

                    .glass-card::after {
                        content: '';
                        position: absolute;
                        top: 0; left: 0; right: 0;
                        height: 1px;
                        background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), rgba(125,211,252,0.3), rgba(167,139,250,0.2), rgba(255,255,255,0.2), transparent);
                        z-index: 1;
                        opacity: 0;
                        transition: opacity 0.5s var(--ease-liquid);
                    }
                    .glass-card:hover::after {
                        opacity: 1;
                    }

                    .glass-card .glow-spot {
                        position: absolute;
                        width: 300px;
                        height: 300px;
                        border-radius: 50%;
                        background: radial-gradient(circle, rgba(125,211,252,0.12) 0%, transparent 70%);
                        pointer-events: none;
                        opacity: 0;
                        transition: opacity 0.6s var(--ease-liquid);
                        z-index: 0;
                        top: -100px;
                        right: -100px;
                    }
                    .glass-card:hover .glow-spot {
                        opacity: 1;
                    }

                    .card-inner {
                        position: relative;
                        z-index: 1;
                        padding: 2.5rem;
                    }

                    .card-title {
                        font-family: 'Syne', 'Noto Sans SC', sans-serif;
                        font-size: 1.4rem;
                        font-weight: 700;
                        margin-bottom: 2rem;
                        display: flex;
                        align-items: center;
                        gap: 0.8rem;
                        color: var(--text-bright);
                    }
                    .card-title .title-bar {
                        display: inline-block;
                        width: 4px;
                        height: 1.4em;
                        border-radius: 4px;
                        background: linear-gradient(180deg, #7dd3fc, #a78bfa);
                    }

                    .info-row {
                        display: flex;
                        align-items: flex-start;
                        gap: 1rem;
                        padding: 1rem 1.1rem;
                        border-radius: var(--radius-md);
                        margin-bottom: 0.6rem;
                        transition: all 0.35s var(--ease-liquid);
                        position: relative;
                    }
                    .info-row:last-child { margin-bottom: 0; }
                    .info-row:hover {
                        background: rgba(255,255,255,0.04);
                        transform: translateX(6px);
                    }

                    .info-icon {
                        width: 44px;
                        height: 44px;
                        border-radius: 12px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.1rem;
                        flex-shrink: 0;
                        position: relative;
                        overflow: hidden;
                        transition: all 0.35s var(--ease-liquid);
                    }
                    .info-icon::before {
                        content: '';
                        position: absolute;
                        inset: 0;
                        border-radius: 12px;
                        background: rgba(125, 211, 252, 0.08);
                        border: 1px solid rgba(125, 211, 252, 0.15);
                        backdrop-filter: blur(10px);
                        -webkit-backdrop-filter: blur(10px);
                    }
                    .info-icon i {
                        position: relative;
                        z-index: 1;
                        color: #7dd3fc;
                    }
                    .info-row:hover .info-icon {
                        transform: scale(1.1) rotate(-3deg);
                    }
                    .info-row:hover .info-icon::before {
                        background: rgba(125, 211, 252, 0.15);
                        border-color: rgba(125, 211, 252, 0.3);
                        box-shadow: 0 0 20px rgba(125, 211, 252, 0.15);
                    }

                    .contact-card .info-icon::before {
                        background: rgba(167, 139, 250, 0.08);
                        border-color: rgba(167, 139, 250, 0.15);
                    }
                    .contact-card .info-icon i {
                        color: #a78bfa;
                    }
                    .contact-card .info-row:hover .info-icon::before {
                        background: rgba(167, 139, 250, 0.15);
                        border-color: rgba(167, 139, 250, 0.3);
                        box-shadow: 0 0 20px rgba(167, 139, 250, 0.15);
                    }

                    .info-body {
                        flex: 1;
                        min-width: 0;
                    }
                    .info-label {
                        font-family: 'DM Mono', monospace;
                        font-size: 0.75rem;
                        font-weight: 400;
                        color: var(--text-muted);
                        text-transform: uppercase;
                        letter-spacing: 0.1em;
                        margin-bottom: 2px;
                    }
                    .info-value {
                        font-size: 0.98rem;
                        font-weight: 400;
                        color: var(--text-bright);
                        word-break: break-all;
                    }

                    /* Clickable rows (copy-based) */
                    .info-row.clickable {
                        cursor: pointer;
                    }
                    .info-row.clickable:active {
                        transform: translateX(6px) scale(0.98);
                    }

                    .info-value a {
                        color: #7dd3fc;
                        text-decoration: none;
                        position: relative;
                        transition: all 0.3s var(--ease-liquid);
                    }
                    .info-value a::after {
                        content: '';
                        position: absolute;
                        bottom: -2px;
                        left: 0;
                        width: 0;
                        height: 1px;
                        background: linear-gradient(90deg, #7dd3fc, #a78bfa);
                        border-radius: 2px;
                        transition: width 0.4s var(--ease-liquid);
                    }
                    .info-value a:hover {
                        color: #a78bfa;
                    }
                    .info-value a:hover::after {
                        width: 100%;
                    }

                    .contact-card .info-value a {
                        color: #a78bfa;
                    }
                    .contact-card .info-value a::after {
                        background: linear-gradient(90deg, #a78bfa, #22d3ee);
                    }
                    .contact-card .info-value a:hover {
                        color: #22d3ee;
                    }

                    /* Toast */
                    .toast {
                        position: fixed;
                        top: 24px;
                        left: 50%;
                        transform: translateX(-50%) translateY(-80px);
                        padding: 0.75rem 1.6rem;
                        border-radius: 14px;
                        font-family: 'DM Mono', monospace;
                        font-size: 0.85rem;
                        color: var(--text-bright);
                        backdrop-filter: blur(24px);
                        -webkit-backdrop-filter: blur(24px);
                        background: rgba(167, 139, 250, 0.15);
                        border: 1px solid rgba(167, 139, 250, 0.25);
                        box-shadow: 0 8px 32px rgba(0,0,0,0.3);
                        z-index: 9999;
                        pointer-events: none;
                        opacity: 0;
                        transition: all 0.5s var(--ease-liquid);
                        white-space: nowrap;
                    }
                    .toast.show {
                        opacity: 1;
                        transform: translateX(-50%) translateY(0);
                    }

                    footer {
                        position: relative;
                        padding: 2.5rem 2rem;
                        text-align: center;
                        overflow: hidden;
                    }
                    .footer-glass {
                        position: absolute;
                        inset: 0;
                        backdrop-filter: blur(var(--glass-blur-heavy));
                        -webkit-backdrop-filter: blur(var(--glass-blur-heavy));
                        background: rgba(255,255,255,0.02);
                        border-top: 1px solid rgba(255,255,255,0.06);
                    }
                    footer p {
                        position: relative;
                        z-index: 1;
                        font-family: 'DM Mono', monospace;
                        font-size: 0.85rem;
                        font-weight: 300;
                        color: var(--text-muted);
                        letter-spacing: 0.05em;
                    }
                    footer .highlight {
                        background: linear-gradient(135deg, #7dd3fc, #a78bfa);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        background-clip: text;
                        font-weight: 500;
                    }

                    .liquid-divider {
                        position: relative;
                        height: 60px;
                        margin: -30px 0;
                        z-index: 3;
                        overflow: hidden;
                    }
                    .liquid-divider svg {
                        position: absolute;
                        bottom: 0;
                        width: 100%;
                        height: 60px;
                    }

                    @media (max-width: 900px) {
                        .contact-grid {
                            grid-template-columns: 1fr;
                            gap: 20px;
                        }
                        header { padding: 5rem 1.5rem 4.5rem; }
                        .card-inner { padding: 2rem 1.6rem; }
                    }
                    @media (max-width: 480px) {
                        header { padding: 4rem 1.2rem 3.5rem; }
                        .card-inner { padding: 1.6rem 1.2rem; }
                        .info-row { padding: 0.8rem; }
                        .info-icon { width: 38px; height: 38px; font-size: 0.95rem; }
                    }
                </style>
            </head>
            <body>

                <div class="liquid-bg">
                    <div class="liquid-blob blob-1"></div>
                    <div class="liquid-blob blob-2"></div>
                    <div class="liquid-blob blob-3"></div>
                    <div class="liquid-blob blob-4"></div>
                    <div class="liquid-blob blob-5"></div>
                </div>

                <div class="toast" id="toast"></div>

                <header>
                    <div class="header-glass"></div>
                    <div class="header-content">
                        <h1>联系作者 Ivan</h1>
                        <p>有任何问题、建议或合作意向，欢迎随时与我取得联系</p>
                        <span class="header-line"></span>
                    </div>
                </header>

                <div class="liquid-divider">
                    <svg viewBox="0 0 1440 60" preserveAspectRatio="none">
                        <defs>
                            <linearGradient id="wave-grad" x1="0%" y1="0%" x2="100%" y2="0%">
                                <stop offset="0%" style="stop-color:rgba(125,211,252,0.06)"/>
                                <stop offset="50%" style="stop-color:rgba(167,139,250,0.08)"/>
                                <stop offset="100%" style="stop-color:rgba(34,211,238,0.06)"/>
                            </linearGradient>
                        </defs>
                        <path d="M0,40 C360,60 720,0 1080,30 C1260,45 1380,20 1440,30 L1440,60 L0,60 Z" fill="url(#wave-grad)"/>
                        <path d="M0,45 C240,20 480,55 720,35 C960,15 1200,50 1440,30 L1440,60 L0,60 Z" fill="rgba(255,255,255,0.02)"/>
                    </svg>
                </div>

                <main class="container">
                    <div class="contact-grid">

                        <div class="glass-card software-card">
                            <div class="glow-spot"></div>
                            <div class="card-inner">
                                <div class="card-title">
                                    <span class="title-bar"></span>
                                    软件信息
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fas fa-file-code"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">Software Name</div>
                                        <div class="info-value">网匠</div>
                                    </div>
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fas fa-tag"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">Version</div>
                                        <div class="info-value">@@VERSION@@</div>
                                    </div>
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fas fa-desktop"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">Platform</div>
                                        <div class="info-value">Windows 7/8/10/11（64位）</div>
                                    </div>
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fas fa-info-circle"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">Description</div>
                                        <div class="info-value">基于 Java FlatLaf 开发的桌面级网络计算工具，同时支持 IPv4 与 IPv6 双栈环境，提供从基础地址解析到复杂网络规划的一站式计算能力，会持续优化和完善更多功能，敬请期待！</div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="glass-card contact-card">
                            <div class="glow-spot" style="background: radial-gradient(circle, rgba(167,139,250,0.12) 0%, transparent 70%);"></div>
                            <div class="card-inner">
                                <div class="card-title">
                                    <span class="title-bar" style="background: linear-gradient(180deg, #a78bfa, #22d3ee);"></span>
                                    联系方式
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fab fa-qq"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">QQ</div>
                                        <div class="info-value"><a href="https://wpa.qq.com/msgrd?v=3&uin=2195928963&site=qq&menu=yes" target="_blank">Moore_Ivan</a></div>
                                    </div>
                                </div>

                                <div class="info-row clickable" onclick="copyAndToast('Moore_Ivan', '微信')">
                                    <div class="info-icon"><i class="fab fa-weixin"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">WeChat</div>
                                        <div class="info-value"><a href="weixin://contacts/profile/Moore_Ivan">Moore_Ivan</a></div>
                                    </div>
                                </div>

                                <div class="info-row clickable" onclick="copyAndToast('Moore_Ivan', '抖音')">
                                    <div class="info-icon"><i class="fab fa-tiktok"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">抖音</div>
                                        <div class="info-value"><a href="https://www.douyin.com/user/MS4wLjABAAAAGcu2VmW1wKWUSpbGkLjilKfMymXaK54sxNPaChuPuLg?from_tab_name=main" target="_blank">Moore_Ivan</a></div>
                                    </div>
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fab fa-github"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">GitHub</div>
                                        <div class="info-value"><a href="https://github.com/Moore-Ivan" target="_blank">Moore-Ivan</a></div>
                                    </div>
                                </div>

                                <div class="info-row">
                                    <div class="info-icon"><i class="fas fa-envelope"></i></div>
                                    <div class="info-body">
                                        <div class="info-label">Email</div>
                                        <div class="info-value"><a href="mailto:Moore_Ivan@foxmail.com">Moore_Ivan@foxmail.com</a></div>
                                    </div>
                                </div>
                            </div>
                        </div>

                    </div>
                </main>

                <footer>
                    <div class="footer-glass"></div>
                    <p>&copy; <span class="highlight">Moore_Ivan</span> 的个人资源 — 保留所有权利</p>
                </footer>

                <script>
                    function copyAndToast(text, platform) {
                        var toast = document.getElementById('toast');
                        var copied = false;

                        if (navigator.clipboard) {
                            navigator.clipboard.writeText(text).then(function() { copied = true; showToast(platform + '号 ' + text + ' 已复制到剪贴板'); });
                        } else {
                            var ta = document.createElement('textarea');
                            ta.value = text;
                            ta.style.cssText = 'position:fixed;left:-9999px;top:-9999px';
                            document.body.appendChild(ta);
                            ta.select();
                            copied = document.execCommand('copy');
                            document.body.removeChild(ta);
                            showToast(platform + '号 ' + text + ' 已复制到剪贴板');
                        }
                    }

                    function showToast(msg) {
                        var toast = document.getElementById('toast');
                        toast.textContent = msg;
                        toast.classList.add('show');
                        clearTimeout(toast._timer);
                        toast._timer = setTimeout(function() {
                            toast.classList.remove('show');
                        }, 2500);
                    }
                </script>

            </body>
            </html>""";
}
