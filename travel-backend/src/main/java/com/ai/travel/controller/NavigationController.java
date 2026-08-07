package com.ai.travel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class NavigationController {

    /**
     * 高德地图导航 H5 中转页面
     * 在小程序 webview 中加载此页面，再由页面重定向到高德地图 URI，从而唤起高德地图 App
     */
    @GetMapping(value = "/navigation/amap", produces = "text/html;charset=UTF-8")
    public String amapNavigation(
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to,
            @RequestParam(defaultValue = "") String via,
            @RequestParam(defaultValue = "car") String mode,
            @RequestParam(defaultValue = "") String fromName,
            @RequestParam(defaultValue = "") String toName,
            @RequestParam(defaultValue = "") String viaNames) {

        // 构建高德地图 URI
        StringBuilder uri = new StringBuilder("https://uri.amap.com/navigation?");
        if (!from.isEmpty()) {
            uri.append("from=").append(from);
            if (!fromName.isEmpty()) {
                uri.append(",").append(URLEncoder.encode(fromName, StandardCharsets.UTF_8));
            }
        }
        if (!to.isEmpty()) {
            uri.append("&to=").append(to);
            if (!toName.isEmpty()) {
                uri.append(",").append(URLEncoder.encode(toName, StandardCharsets.UTF_8));
            }
        }
        if (!via.isEmpty()) {
            uri.append("&via=").append(via);
        }
        uri.append("&mode=").append(mode);
        uri.append("&callnative=1");
        String finalUri = uri.toString();

        // 构建途经点显示 HTML
        StringBuilder viaHtml = new StringBuilder();
        if (!viaNames.isEmpty()) {
            String[] names = viaNames.split("\\|");
            for (String name : names) {
                if (!name.isEmpty()) {
                    viaHtml.append("<div class=\"via-item\">").append(escapeHtml(name)).append("</div>");
                }
            }
        }

        // 构建完整的 HTML 页面
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n");
        html.append("<title>导航</title>\n");
        html.append("<style>\n");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("body {\n");
        html.append("  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n");
        html.append("  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
        html.append("  min-height: 100vh;\n");
        html.append("  display: flex;\n");
        html.append("  align-items: center;\n");
        html.append("  justify-content: center;\n");
        html.append("  padding: 20px;\n");
        html.append("}\n");
        html.append(".card {\n");
        html.append("  background: #fff;\n");
        html.append("  border-radius: 20px;\n");
        html.append("  padding: 32px 24px;\n");
        html.append("  width: 100%;\n");
        html.append("  max-width: 380px;\n");
        html.append("  box-shadow: 0 20px 60px rgba(0,0,0,0.15);\n");
        html.append("  text-align: center;\n");
        html.append("}\n");
        html.append(".nav-icon { font-size: 56px; margin-bottom: 16px; }\n");
        html.append(".title { font-size: 20px; font-weight: 600; color: #1a1a2e; margin-bottom: 8px; }\n");
        html.append(".subtitle { font-size: 14px; color: #888; margin-bottom: 24px; }\n");
        html.append(".route-info {\n");
        html.append("  background: #f5f7ff;\n");
        html.append("  border-radius: 12px;\n");
        html.append("  padding: 16px;\n");
        html.append("  margin-bottom: 24px;\n");
        html.append("  text-align: left;\n");
        html.append("}\n");
        html.append(".route-point { display: flex; align-items: flex-start; gap: 10px; padding: 6px 0; }\n");
        html.append(".route-point .dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 4px; flex-shrink: 0; }\n");
        html.append(".route-point .dot.start { background: #22c55e; }\n");
        html.append(".route-point .dot.end { background: #ef4444; }\n");
        html.append(".route-point .dot.via { background: #f59e0b; }\n");
        html.append(".route-point .name { font-size: 14px; color: #333; line-height: 1.4; }\n");
        html.append(".route-line { width: 2px; height: 20px; background: #ddd; margin-left: 4px; }\n");
        html.append(".via-section { margin: 4px 0 4px 20px; }\n");
        html.append(".via-item { font-size: 13px; color: #666; padding: 3px 0; }\n");
        html.append(".btn {\n");
        html.append("  display: block; width: 100%; padding: 14px; border: none;\n");
        html.append("  border-radius: 12px; font-size: 17px; font-weight: 600;\n");
        html.append("  cursor: pointer; text-decoration: none;\n");
        html.append("  transition: transform 0.15s, box-shadow 0.15s;\n");
        html.append("}\n");
        html.append(".btn:active { transform: scale(0.97); }\n");
        html.append(".btn-primary {\n");
        html.append("  background: linear-gradient(135deg, #667eea, #764ba2);\n");
        html.append("  color: #fff;\n");
        html.append("  box-shadow: 0 6px 20px rgba(102,126,234,0.4);\n");
        html.append("}\n");
        html.append(".btn-primary:hover { box-shadow: 0 8px 28px rgba(102,126,234,0.5); }\n");
        html.append(".btn-secondary {\n");
        html.append("  background: #f0f0f5; color: #666; margin-top: 12px; font-size: 14px;\n");
        html.append("}\n");
        html.append(".tip { font-size: 12px; color: #aaa; margin-top: 16px; line-height: 1.6; }\n");
        html.append(".loading { display: none; margin-top: 16px; color: #999; font-size: 13px; }\n");
        html.append(".loading.active { display: block; }\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<div class=\"card\">\n");
        html.append("  <div class=\"nav-icon\">&#x1F5FA;&#xFE0F;</div>\n");
        html.append("  <div class=\"title\">导航到目的地</div>\n");
        html.append("  <div class=\"subtitle\">将跳转至高德地图 App</div>\n");
        html.append("  <div class=\"route-info\">\n");

        // 起点
        if (!fromName.isEmpty()) {
            html.append("    <div class=\"route-point\">\n");
            html.append("      <div class=\"dot start\"></div>\n");
            html.append("      <div class=\"name\"><strong>起点：</strong>").append(escapeHtml(fromName)).append("</div>\n");
            html.append("    </div>\n");
        }

        // 途经点
        if (viaHtml.length() > 0) {
            html.append("    <div class=\"route-point\">\n");
            html.append("      <div class=\"dot via\"></div>\n");
            html.append("      <div class=\"name\"><strong>途经点：</strong></div>\n");
            html.append("    </div>\n");
            html.append("    <div class=\"via-section\">").append(viaHtml).append("</div>\n");
        }

        // 终点
        html.append("    <div class=\"route-point\">\n");
        html.append("      <div class=\"dot end\"></div>\n");
        html.append("      <div class=\"name\"><strong>终点：</strong>").append(escapeHtml(toName)).append("</div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        html.append("  <button class=\"btn btn-primary\" onclick=\"openNavigation()\">打开高德地图导航</button>\n");
        html.append("  <button class=\"btn btn-secondary\" onclick=\"tryOpenApp()\">尝试直接打开 App</button>\n");
        html.append("  <div class=\"tip\">\n");
        html.append("    如果未自动跳转，请点击上方按钮重试<br>\n");
        html.append("    未安装高德地图 App 将跳转至网页版\n");
        html.append("  </div>\n");
        html.append("  <div class=\"loading\" id=\"loading\">正在打开高德地图…</div>\n");
        html.append("</div>\n");

        html.append("<script>\n");
        html.append("var navUrl = \"").append(escapeJs(finalUri)).append("\";\n");
        html.append("var retryCount = 0;\n");
        html.append("function openNavigation() {\n");
        html.append("  document.getElementById('loading').classList.add('active');\n");
        html.append("  window.location.href = navUrl;\n");
        html.append("  setTimeout(function() {\n");
        html.append("    if (retryCount < 2) { retryCount++; tryOpenApp(); }\n");
        html.append("    else { document.getElementById('loading').classList.remove('active'); }\n");
        html.append("  }, 3000);\n");
        html.append("}\n");
        html.append("function tryOpenApp() {\n");
        html.append("  document.getElementById('loading').classList.add('active');\n");
        html.append("  window.location.href = navUrl;\n");
        html.append("}\n");
        html.append("setTimeout(function() { openNavigation(); }, 500);\n");
        html.append("</script>\n");
        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeJs(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}