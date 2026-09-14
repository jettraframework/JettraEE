package io.jettra.server.openapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SwaggerUIHandler implements HttpHandler {

    private final String openApiUrl;

    public SwaggerUIHandler(String openApiUrl) {
        this.openApiUrl = io.jettra.server.JettraServer.resolvePath(openApiUrl);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n" +
                "  <meta name=\"description\" content=\"SwaggerUI\" />\n" +
                "  <title>SwaggerUI</title>\n" +
                "  <link id=\"swagger-theme\" rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui.css\" />\n" +
                "  <style>\n" +
                "    @import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700&family=Inter:wght@300;400;600&display=swap');\n" +
                "    .theme-switch-wrapper {\n" +
                "      position: fixed; top: 15px; right: 25px; z-index: 9999;\n" +
                "      display: flex; align-items: center; background: rgba(255,255,255,0.9);\n" +
                "      padding: 8px 16px; border-radius: 30px; box-shadow: 0 4px 15px rgba(0,0,0,0.2);\n" +
                "      font-family: 'Inter', sans-serif; font-size: 14px; font-weight: 600; color: #111;\n" +
                "      backdrop-filter: blur(5px); border: 1px solid rgba(0,0,0,0.1); transition: all 0.3s ease;\n" +
                "    }\n" +
                "    body.dark-mode .theme-switch-wrapper {\n" +
                "      background: rgba(10,15,30,0.8); color: #00f3ff;\n" +
                "      border: 1px solid rgba(0, 243, 255, 0.4); box-shadow: 0 0 15px rgba(0, 243, 255, 0.2);\n" +
                "    }\n" +
                "    .theme-switch-wrapper span { margin-right: 12px; letter-spacing: 1px; }\n" +
                "    .theme-switch { display: inline-block; position: relative; width: 50px; height: 26px; }\n" +
                "    .theme-switch input { opacity: 0; width: 0; height: 0; }\n" +
                "    .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #ccc; transition: .4s; border-radius: 26px; box-shadow: inset 0 0 5px rgba(0,0,0,0.2); }\n" +
                "    .slider:before { position: absolute; content: \"\"; height: 20px; width: 20px; left: 3px; bottom: 3px; background-color: white; transition: .4s; border-radius: 50%; box-shadow: 0 2px 5px rgba(0,0,0,0.2); }\n" +
                "    input:checked + .slider { background-color: #00f3ff; box-shadow: inset 0 0 10px rgba(0, 243, 255, 0.5); }\n" +
                "    input:checked + .slider:before { transform: translateX(24px); background-color: #fff; box-shadow: 0 0 10px #fff; }\n" +
                "    \n" +
                "    /* FUTURISTIC DARK MODE OVERRIDES */\n" +
                "    body.dark-mode {\n" +
                "      background: radial-gradient(circle at top, #0b0f19, #050508) !important;\n" +
                "      color: #e0e0ff !important;\n" +
                "      font-family: 'Inter', sans-serif !important;\n" +
                "      min-height: 100vh;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui {\n" +
                "      color: #e0e0ff !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .info .title {\n" +
                "      color: #00f3ff !important;\n" +
                "      font-family: 'Orbitron', sans-serif !important;\n" +
                "      text-shadow: 0 0 15px rgba(0, 243, 255, 0.6);\n" +
                "      letter-spacing: 2px;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .info p,\n" +
                "    body.dark-mode .swagger-ui .info a {\n" +
                "      color: #a0a0d0 !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .scheme-container {\n" +
                "      background: rgba(10, 15, 30, 0.6) !important;\n" +
                "      backdrop-filter: blur(10px);\n" +
                "      border-bottom: 1px solid rgba(0, 243, 255, 0.2);\n" +
                "      box-shadow: 0 10px 30px rgba(0,0,0,0.5);\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .opblock {\n" +
                "      background: rgba(15, 20, 35, 0.6) !important;\n" +
                "      border: 1px solid rgba(0, 243, 255, 0.15) !important;\n" +
                "      border-radius: 12px;\n" +
                "      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);\n" +
                "      backdrop-filter: blur(5px);\n" +
                "      transition: all 0.3s ease;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .opblock:hover {\n" +
                "      border-color: rgba(0, 243, 255, 0.6) !important;\n" +
                "      box-shadow: 0 0 20px rgba(0, 243, 255, 0.2) !important;\n" +
                "      transform: translateY(-2px);\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .opblock .opblock-summary {\n" +
                "      border-bottom: 1px solid rgba(0, 243, 255, 0.1) !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .opblock .opblock-summary-method {\n" +
                "      border-radius: 6px;\n" +
                "      font-family: 'Orbitron', sans-serif !important;\n" +
                "      box-shadow: 0 0 10px rgba(0,0,0,0.5);\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .opblock-tag {\n" +
                "      color: #bd00ff !important;\n" +
                "      border-bottom: 1px solid rgba(189, 0, 255, 0.3) !important;\n" +
                "      font-family: 'Orbitron', sans-serif !important;\n" +
                "      text-shadow: 0 0 10px rgba(189, 0, 255, 0.5);\n" +
                "      letter-spacing: 1px;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .btn {\n" +
                "      background: rgba(0, 243, 255, 0.05) !important;\n" +
                "      border: 1px solid #00f3ff !important;\n" +
                "      color: #00f3ff !important;\n" +
                "      box-shadow: 0 0 10px rgba(0, 243, 255, 0.2);\n" +
                "      border-radius: 6px;\n" +
                "      font-family: 'Inter', sans-serif !important;\n" +
                "      font-weight: 600;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 1px;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .btn:hover {\n" +
                "      background: #00f3ff !important;\n" +
                "      color: #000 !important;\n" +
                "      box-shadow: 0 0 20px rgba(0, 243, 255, 0.6);\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui select,\n" +
                "    body.dark-mode .swagger-ui input,\n" +
                "    body.dark-mode .swagger-ui textarea {\n" +
                "      background: rgba(5, 10, 20, 0.8) !important;\n" +
                "      border: 1px solid rgba(0, 243, 255, 0.3) !important;\n" +
                "      color: #00f3ff !important;\n" +
                "      border-radius: 6px;\n" +
                "      font-family: 'Inter', monospace !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui select:focus,\n" +
                "    body.dark-mode .swagger-ui input:focus,\n" +
                "    body.dark-mode .swagger-ui textarea:focus {\n" +
                "      outline: none !important;\n" +
                "      border-color: #00f3ff !important;\n" +
                "      box-shadow: 0 0 15px rgba(0, 243, 255, 0.4) !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .opblock-body pre.microlight {\n" +
                "      background: #050508 !important;\n" +
                "      border: 1px solid rgba(189, 0, 255, 0.3) !important;\n" +
                "      border-radius: 8px;\n" +
                "      color: #00f3ff !important;\n" +
                "      text-shadow: 0 0 2px rgba(0, 243, 255, 0.2);\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui table thead tr th {\n" +
                "      color: #a0a0d0 !important;\n" +
                "      border-bottom: 1px solid rgba(0, 243, 255, 0.2) !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .parameter__name {\n" +
                "      color: #00f3ff !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .parameter__type {\n" +
                "      color: #bd00ff !important;\n" +
                "    }\n" +
                "    body.dark-mode .swagger-ui .responses-inner h4,\n" +
                "    body.dark-mode .swagger-ui .responses-inner h5 {\n" +
                "      color: #a0a0d0 !important;\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"theme-switch-wrapper\">\n" +
                "    <span>CYBER THEME</span>\n" +
                "    <label class=\"theme-switch\">\n" +
                "      <input type=\"checkbox\" id=\"checkbox\" />\n" +
                "      <div class=\"slider\"></div>\n" +
                "    </label>\n" +
                "  </div>\n" +
                "<div id=\"swagger-ui\"></div>\n" +
                "<script src=\"https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui-bundle.js\" crossorigin></script>\n" +
                "<script>\n" +
                "  window.onload = () => {\n" +
                "    window.ui = SwaggerUIBundle({\n" +
                "      url: '" + openApiUrl + "',\n" +
                "      dom_id: '#swagger-ui',\n" +
                "      operationsSorter: 'alpha',\n" +
                "      tagsSorter: 'alpha',\n" +
                "    });\n" +
                "  };\n" +
                "  const toggleSwitch = document.querySelector('.theme-switch input[type=\"checkbox\"]');\n" +
                "  const themeLink = document.getElementById('swagger-theme');\n" +
                "  const lightThemeUrl = 'https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui.css';\n" +
                "  const darkThemeUrl = 'https://unpkg.com/swagger-ui-themes@3.0.1/themes/3.x/theme-muted.css';\n" +
                "  const currentTheme = localStorage.getItem('theme');\n" +
                "  if (currentTheme) {\n" +
                "      if (currentTheme === 'dark-theme') {\n" +
                "          toggleSwitch.checked = true;\n" +
                "          themeLink.href = darkThemeUrl;\n" +
                "          document.body.classList.add('dark-mode');\n" +
                "      }\n" +
                "  }\n" +
                "  toggleSwitch.addEventListener('change', function(e) {\n" +
                "      if (e.target.checked) {\n" +
                "          themeLink.href = darkThemeUrl;\n" +
                "          document.body.classList.add('dark-mode');\n" +
                "          localStorage.setItem('theme', 'dark-theme');\n" +
                "      } else {\n" +
                "          themeLink.href = lightThemeUrl;\n" +
                "          document.body.classList.remove('dark-mode');\n" +
                "          localStorage.setItem('theme', 'light');\n" +
                "      }\n" +
                "  });\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
