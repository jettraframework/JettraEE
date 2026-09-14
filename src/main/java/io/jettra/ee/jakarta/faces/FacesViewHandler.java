package io.jettra.ee.jakarta.faces;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.core.IO;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.ee.server.WebResourceManager;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manejador y motor de renderizado ligero de Jakarta Faces (Facelets XHTML) para JettraEE.
 * Permite procesar archivos .xhtml con componentes estándar de Faces (h:form, h:inputText,
 * h:commandButton, h:outputText, h:dataTable) y expresiones EL #{bean.propiedad} vinculadas
 * a beans CDI administrados por JettraCDIContainer.
 */
public class FacesViewHandler implements HttpHandler {

    private final String webappRoot;
    private final WebResourceManager resourceManager;

    public FacesViewHandler() {
        this(new WebResourceManager());
    }

    public FacesViewHandler(String webappRoot) {
        this(new WebResourceManager(webappRoot));
    }

    public FacesViewHandler(WebResourceManager resourceManager) {
        this.resourceManager = resourceManager != null ? resourceManager : new WebResourceManager();
        this.webappRoot = this.resourceManager.getWebappRoot();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/faces/")) {
            path = path.substring(6);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // Si la ruta termina en / o vacía, buscar index.xhtml
        if (path.equals("/") || path.endsWith("/")) {
            path = path + "index.xhtml";
        }
        if (!path.endsWith(".xhtml")) {
            path = path + ".xhtml";
        }

        String xhtmlContent = loadXhtml(path);
        if (xhtmlContent == null) {
            send404(exchange, path);
            return;
        }

        Map<String, String> formData = new HashMap<>();
        String actionToExecute = null;

        // Si es POST, procesar los campos enviados y ejecutar la acción
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            formData = parseFormData(exchange);
            actionToExecute = formData.get("jakarta.faces.action");

            // 1. Establecer propiedades en los beans CDI
            applyFormValues(formData);

            // 2. Ejecutar acción de botón si fue solicitada
            if (actionToExecute != null && !actionToExecute.isBlank()) {
                executeAction(actionToExecute);
            }
        }

        // Renderizar el template Facelets
        String renderedHtml = renderFacelets(xhtmlContent, formData);

        byte[] bytes = renderedHtml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String loadXhtml(String relativePath) {
        if (resourceManager != null) {
            try (InputStream is = resourceManager.getResourceAsStream(relativePath)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {}
        }

        // Buscar en el sistema de archivos (priorizando Document Root src/main/webapp)
        String[] possiblePaths = new String[]{
                webappRoot + relativePath,
                "src/main/webapp" + relativePath,
                "src/main/resources/META-INF/resources" + relativePath,
                "webapp" + relativePath,
                "src/main/resources/webapp" + relativePath
        };

        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists() && !f.isDirectory()) {
                try {
                    return Files.readString(f.toPath(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
        }

        // Buscar en el ClassLoader (JAR en producción o web-fragments)
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = getClass().getClassLoader();
        String[] clPaths = new String[]{
                "META-INF/resources" + relativePath,
                relativePath.startsWith("/") ? relativePath.substring(1) : relativePath,
                "webapp" + relativePath,
                "src/main/resources/webapp" + relativePath
        };
        for (String cp : clPaths) {
            try (InputStream is = cl.getResourceAsStream(cp)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {}
        }

        return null;
    }

    private void applyFormValues(Map<String, String> formData) {
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Si la clave tiene formato bean_propiedad o #{bean.propiedad}
            String expr = key;
            if (!expr.startsWith("#{") && expr.contains(".")) {
                expr = "#{" + expr + "}";
            }

            if (expr.startsWith("#{") && expr.endsWith("}")) {
                setElValue(expr, value);
            }
        }
    }

    private void executeAction(String actionExpr) {
        if (actionExpr.startsWith("#{") && actionExpr.endsWith("}")) {
            String content = actionExpr.substring(2, actionExpr.length() - 1).trim();
            int dotIdx = content.indexOf('.');
            if (dotIdx > 0) {
                String beanName = content.substring(0, dotIdx).trim();
                String methodName = content.substring(dotIdx + 1).trim();
                if (methodName.endsWith("()")) methodName = methodName.substring(0, methodName.length() - 2);

                Object bean = JettraCDIContainer.getInstance().getBeanByName(beanName);
                if (bean != null) {
                    try {
                        Method m = bean.getClass().getMethod(methodName);
                        m.setAccessible(true);
                        m.invoke(bean);
                        IO.info("Jakarta Faces: Acción ejecutada con éxito: " + actionExpr);
                    } catch (Exception e) {
                        IO.error("Error al ejecutar acción Faces: " + actionExpr, e);
                    }
                }
            }
        }
    }

    private String renderFacelets(String xhtml, Map<String, String> formData) {
        String html = xhtml;

        // 1. Limpiar etiquetas XML, namespaces y directivas de Facelets ui:
        html = html.replaceAll("(?s)<\\?xml.*?\\?>", "");
        html = html.replaceAll("xmlns:[a-zA-Z0-9_-]+=\"[^\"]*\"", "");
        html = html.replaceAll("(?s)<ui:composition.*?>", "");
        html = html.replaceAll("</ui:composition>", "");
        html = html.replaceAll("(?s)<ui:define.*?>", "");
        html = html.replaceAll("</ui:define>", "");
        html = html.replaceAll("(?s)<ui:param.*?>", "");
        html = html.replaceAll("(?s)<f:view.*?>", "");
        html = html.replaceAll("</f:view>", "");
        html = html.replaceAll("(?s)<f:convertNumber.*?>", "");

        // 2. Reemplazar <h:head> y <h:body>
        html = html.replace("<h:head>", "<head>").replace("</h:head>", "</head>");
        html = html.replace("<h:body>", "<body>").replace("</h:body>", "</body>");

        // 3. Si no tiene estructura HTML completa, envolver
        if (!html.contains("<html") && !html.contains("<HTML")) {
            html = "<!DOCTYPE html>\n<html>\n<head>\n<title>JettraEE - Jakarta Faces & PrimeFaces</title>\n</head>\n<body>\n" + html + "\n</body>\n</html>";
        }

        // Inyectar estilos CSS de PrimeFaces Showcase en <head>
        String pfStyles = """
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <link rel="stylesheet" href="https://unpkg.com/primeicons/primeicons.css" />
            <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"/>
            <style>
                body {
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background-color: #f8fafc;
                    color: #334155;
                    margin: 0;
                    padding: 0;
                }
                .card {
                    background: #ffffff;
                    padding: 1.75rem;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05);
                    border: 1px solid #e2e8f0;
                    margin-bottom: 2rem;
                }
                .ui-datatable, .p-datatable-table {
                    width: 100%;
                    border-collapse: separate;
                    border-spacing: 0;
                    font-size: 0.925rem;
                }
                .ui-datatable .ui-datatable-tablewrapper {
                    overflow-x: auto;
                    border-radius: 8px;
                    border: 1px solid #e2e8f0;
                }
                .ui-datatable .ui-datatable-thead > tr > th,
                .p-datatable-table thead th {
                    background: #f1f5f9;
                    color: #1e293b;
                    padding: 0.85rem 1.25rem;
                    border-bottom: 1px solid #cbd5e1;
                    text-align: left;
                    font-weight: 600;
                    letter-spacing: 0.02em;
                }
                .ui-datatable .ui-datatable-data > tr > td,
                .p-datatable-table tbody td {
                    padding: 0.85rem 1.25rem;
                    border-bottom: 1px solid #f1f5f9;
                    vertical-align: middle;
                }
                .ui-datatable.ui-datatable-striped .ui-datatable-data > tr:nth-child(even),
                .p-datatable-table tr:nth-child(even) {
                    background-color: #f8fafc;
                }
                .ui-datatable .ui-datatable-data > tr:hover,
                .p-datatable-table tr:hover {
                    background-color: #f1f5f9 !important;
                    transition: background-color 0.15s ease-in-out;
                }
                .p-tag {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    padding: 0.25rem 0.65rem;
                    border-radius: 6px;
                    font-size: 0.75rem;
                    font-weight: 700;
                    text-transform: uppercase;
                    letter-spacing: 0.04em;
                }
                .p-tag.p-tag-rounded {
                    border-radius: 9999px;
                }
                .p-tag-success { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }
                .p-tag-warning { background: #fef9c3; color: #a16207; border: 1px solid #fef08a; }
                .p-tag-danger { background: #fee2e2; color: #b91c1c; border: 1px solid #fecaca; }
                .p-tag-info { background: #e0f2fe; color: #0369a1; border: 1px solid #bae6fd; }
                .p-rating {
                    display: inline-flex;
                    gap: 3px;
                    font-size: 1.15rem;
                    line-height: 1;
                }
                .p-badge {
                    display: inline-block;
                    padding: 0.25rem 0.5rem;
                    font-size: 0.75rem;
                    font-weight: 700;
                    border-radius: 4px;
                    background: #6366f1;
                    color: #fff;
                }
                .p-button {
                    background: #6366f1;
                    color: #fff;
                    border: none;
                    padding: 0.5rem 1rem;
                    border-radius: 6px;
                    font-weight: 600;
                    cursor: pointer;
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    text-decoration: none;
                    font-size: 0.875rem;
                }
                .p-button:hover { background: #4f46e5; }
                .ui-button-success { background: #10b981 !important; }
                .ui-button-success:hover { background: #059669 !important; }
                .ui-button-danger { background: #ef4444 !important; }
                .ui-button-danger:hover { background: #dc2626 !important; }
                .ui-button-warning { background: #f59e0b !important; }
                .ui-button-warning:hover { background: #d97706 !important; }
                .ui-button-secondary { background: #64748b !important; }
                .ui-button-secondary:hover { background: #475569 !important; }
                .ui-button-outlined { background: transparent !important; border: 1px solid currentColor !important; color: #6366f1 !important; }
                .ui-button-outlined:hover { background: #eef2ff !important; }
                .ui-button-rounded { border-radius: 9999px !important; }

                /* PrimeFaces Datatable Header, Footer & Paginator */
                .ui-datatable-header, .ui-datatable-footer {
                    background: #f8fafc;
                    border: 1px solid #e2e8f0;
                    padding: 0.75rem 1.25rem;
                    font-weight: 600;
                    color: #1e293b;
                }
                .ui-datatable-header {
                    border-top-left-radius: 8px;
                    border-top-right-radius: 8px;
                    border-bottom: none;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .ui-datatable-footer {
                    border-bottom-left-radius: 8px;
                    border-bottom-right-radius: 8px;
                    border-top: none;
                }
                .ui-paginator {
                    background: #f8fafc;
                    border: 1px solid #e2e8f0;
                    border-top: none;
                    padding: 0.5rem 1rem;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 6px;
                    font-size: 0.875rem;
                    border-bottom-left-radius: 8px;
                    border-bottom-right-radius: 8px;
                }
                .ui-paginator a {
                    min-width: 2rem;
                    height: 2rem;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    border-radius: 6px;
                    cursor: pointer;
                    color: #475569;
                    user-select: none;
                }
                .ui-paginator a:hover { background: #e2e8f0; }
                .ui-paginator a.ui-state-active { background: #6366f1; color: #fff; font-weight: bold; }
                .ui-paginator a.ui-state-disabled { opacity: 0.4; cursor: not-allowed; }
                .ui-paginator-current { margin-right: auto; color: #64748b; font-size: 0.8rem; }
                .ui-paginator-rpp-options { margin-left: auto; }
                .ui-paginator-rpp-options select {
                    border: 1px solid #cbd5e1;
                    border-radius: 4px;
                    padding: 4px 8px;
                    background: #fff;
                    font-size: 0.85rem;
                }

                /* Sort & Filters */
                .ui-sortable-column { cursor: pointer; user-select: none; }
                .ui-sortable-column:hover { background: #e2e8f0 !important; }
                .ui-sortable-column-icon { color: #94a3b8; font-size: 0.8rem; margin-left: 6px; }
                .ui-column-filter {
                    width: 100%;
                    box-sizing: border-box;
                    padding: 4px 8px;
                    font-size: 0.8rem;
                    border: 1px solid #cbd5e1;
                    border-radius: 4px;
                    margin-top: 4px;
                }

                /* Size variations */
                .ui-datatable-sm td, .ui-datatable-sm th { padding: 0.45rem 0.75rem !important; font-size: 0.85rem !important; }
                .ui-datatable-lg td, .ui-datatable-lg th { padding: 1.25rem 1.5rem !important; font-size: 1.05rem !important; }

                /* Toolbar */
                .ui-toolbar {
                    background: #ffffff;
                    border: 1px solid #e2e8f0;
                    border-radius: 8px;
                    padding: 0.75rem 1rem;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-bottom: 1.25rem;
                }
                .ui-toolbar-group-left, .ui-toolbar-group-right {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }

                /* Dialog Modal */
                .ui-dialog-mask {
                    position: fixed;
                    top: 0; left: 0; right: 0; bottom: 0;
                    background: rgba(15, 23, 42, 0.6);
                    backdrop-filter: blur(2px);
                    display: none;
                    align-items: center;
                    justify-content: center;
                    z-index: 1000;
                }
                .ui-dialog {
                    background: #fff;
                    border-radius: 12px;
                    box-shadow: 0 20px 25px -5px rgba(0,0,0,0.2);
                    max-width: 500px;
                    width: 90%;
                    overflow: hidden;
                    border: 1px solid #cbd5e1;
                }
                .ui-dialog-titlebar {
                    background: #f8fafc;
                    padding: 1rem 1.25rem;
                    border-bottom: 1px solid #e2e8f0;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    font-weight: 700;
                }
                .ui-dialog-content { padding: 1.25rem; }
                .ui-dialog-footer {
                    background: #f8fafc;
                    padding: 0.75rem 1.25rem;
                    border-top: 1px solid #e2e8f0;
                    display: flex;
                    justify-content: flex-end;
                    gap: 8px;
                }
            </style>
            <script>
                // PrimeFaces Showcase JavaScript Engine
                const PF = (widgetVar) => ({
                    show: () => {
                        const d = document.getElementById(widgetVar) || document.querySelector('[data-widget="' + widgetVar + '"]');
                        if (d) d.style.display = 'flex';
                    },
                    hide: () => {
                        const d = document.getElementById(widgetVar) || document.querySelector('[data-widget="' + widgetVar + '"]');
                        if (d) d.style.display = 'none';
                    },
                    filter: () => {
                        const filterVal = (document.getElementById('globalFilter') || document.querySelector('.global-filter-input'))?.value.toLowerCase() || '';
                        document.querySelectorAll('.p-datatable-table tbody tr').forEach(row => {
                            const txt = row.innerText.toLowerCase();
                            row.style.display = txt.includes(filterVal) ? '' : 'none';
                        });
                    }
                });

                function sortTable(th, colIdx) {
                    const table = th.closest('table');
                    const tbody = table.querySelector('tbody');
                    const rows = Array.from(tbody.querySelectorAll('tr'));
                    const isAsc = th.getAttribute('data-order') !== 'asc';
                    th.setAttribute('data-order', isAsc ? 'asc' : 'desc');

                    rows.sort((r1, r2) => {
                        const t1 = r1.children[colIdx]?.innerText.trim() || '';
                        const t2 = r2.children[colIdx]?.innerText.trim() || '';
                        const n1 = parseFloat(t1.replace(/[^0-9.-]+/g, ''));
                        const n2 = parseFloat(t2.replace(/[^0-9.-]+/g, ''));
                        if (!isNaN(n1) && !isNaN(n2)) {
                            return isAsc ? n1 - n2 : n2 - n1;
                        }
                        return isAsc ? t1.localeCompare(t2) : t2.localeCompare(t1);
                    });
                    rows.forEach(r => tbody.appendChild(r));
                }

                function filterColumn(input, colIdx) {
                    const val = input.value.toLowerCase();
                    const table = input.closest('table');
                    table.querySelectorAll('tbody tr').forEach(row => {
                        const cell = row.children[colIdx];
                        const text = cell ? cell.innerText.toLowerCase() : '';
                        row.style.display = text.includes(val) ? '' : 'none';
                    });
                }

                function toggleSelectAll(chk) {
                    const table = chk.closest('table');
                    table.querySelectorAll('tbody tr').forEach(row => {
                        const rowChk = row.querySelector('.ui-chkbox-row');
                        if (rowChk) {
                            rowChk.checked = chk.checked;
                            if (chk.checked) row.classList.add('ui-state-highlight');
                            else row.classList.remove('ui-state-highlight');
                        }
                    });
                }

                function toggleRowSelect(chk) {
                    const row = chk.closest('tr');
                    if (chk.checked) row.classList.add('ui-state-highlight');
                    else row.classList.remove('ui-state-highlight');
                }

                function paginateTable(tableId, page, pageSize) {
                    const table = document.getElementById(tableId) || document.querySelector('.p-datatable-table');
                    if (!table) return;
                    const rows = Array.from(table.querySelectorAll('tbody tr'));
                    const start = (page - 1) * pageSize;
                    const end = start + pageSize;
                    rows.forEach((r, idx) => {
                        r.style.display = (idx >= start && idx < end) ? '' : 'none';
                    });
                }
            </script>
            """;
        if (html.contains("<head>")) {
            html = html.replace("<head>", "<head>\n" + pfStyles);
        } else if (html.contains("<body>")) {
            html = html.replace("<body>", "<head>\n" + pfStyles + "</head>\n<body>");
        }

        // 4. Procesar <h:form>
        Pattern formPattern = Pattern.compile("<h:form(.*?)>(.*?)</h:form>", Pattern.DOTALL);
        Matcher formMatcher = formPattern.matcher(html);
        StringBuilder formSb = new StringBuilder();
        while (formMatcher.find()) {
            String attrs = formMatcher.group(1);
            String body = formMatcher.group(2);
            String formReplacement = "<form method=\"post\" action=\"\" " + attrs + ">" + body + "</form>";
            formMatcher.appendReplacement(formSb, Matcher.quoteReplacement(formReplacement));
        }
        formMatcher.appendTail(formSb);
        html = formSb.toString();

        // 5. Procesar <p:toolbar>
        html = renderToolbars(html);

        // 6. Procesar <p:dialog>
        html = renderDialogs(html);

        // 7. Procesar <h:dataTable> y <p:dataTable>
        html = renderDataTables(html);

        // 8. Procesar <p:tag>
        html = renderPrimeTags(html);

        // 9. Procesar <p:rating>
        html = renderPrimeRatings(html);

        // 10. Procesar <p:badge>
        html = renderPrimeBadges(html);

        // 11. Procesar <h:inputText> y <p:inputText>
        Pattern inputPattern = Pattern.compile("<(?:h:inputText|p:inputText)\\s+([^>]*?)/?>", Pattern.DOTALL);
        Matcher inputMatcher = inputPattern.matcher(html);
        StringBuilder inputSb = new StringBuilder();
        while (inputMatcher.find()) {
            String attrs = inputMatcher.group(1);
            String valueExpr = extractAttr(attrs, "value");
            String styleClass = extractAttr(attrs, "styleClass");
            String placeholder = extractAttr(attrs, "placeholder");
            String id = extractAttr(attrs, "id");
            String onkeyup = extractAttr(attrs, "onkeyup");

            String val = resolveEl(valueExpr);
            String name = (valueExpr != null && valueExpr.startsWith("#{") && valueExpr.endsWith("}"))
                    ? valueExpr.substring(2, valueExpr.length() - 1)
                    : (id != null ? id : "input_" + Math.abs(attrs.hashCode()));

            String renderedInput = "<input type=\"text\" name=\"" + name + "\" value=\"" + (val != null ? escapeHtml(val) : "") + "\" "
                    + (styleClass != null ? "class=\"" + styleClass + "\" " : "class=\"ui-inputtext\" ")
                    + (placeholder != null ? "placeholder=\"" + placeholder + "\" " : "")
                    + (onkeyup != null ? "onkeyup=\"" + onkeyup + "\" " : "")
                    + (id != null ? "id=\"" + id + "\" " : "") + "/>";

            inputMatcher.appendReplacement(inputSb, Matcher.quoteReplacement(renderedInput));
        }
        inputMatcher.appendTail(inputSb);
        html = inputSb.toString();

        // 12. Procesar <h:commandButton>, <p:commandButton>, <p:button>
        Pattern btnPattern = Pattern.compile("<(?:h:commandButton|p:commandButton|p:button)\\s+([^>]*?)/?>", Pattern.DOTALL);
        Matcher btnMatcher = btnPattern.matcher(html);
        StringBuilder btnSb = new StringBuilder();
        while (btnMatcher.find()) {
            String attrs = btnMatcher.group(1);
            String value = extractAttr(attrs, "value");
            String action = extractAttr(attrs, "action");
            String styleClass = extractAttr(attrs, "styleClass");
            String icon = extractAttr(attrs, "icon");
            String onclick = extractAttr(attrs, "onclick");

            String iconHtml = (icon != null && !icon.isBlank()) ? "<i class=\"" + icon + "\"></i> " : "";
            String cls = "p-button " + (styleClass != null ? styleClass : "");

            String renderedBtn = "<button type=\"button\" name=\"jakarta.faces.action\" value=\"" + (action != null ? action : "") + "\" "
                    + "class=\"" + cls + "\" "
                    + (onclick != null ? "onclick=\"" + onclick + "\" " : "") + ">"
                    + iconHtml + (value != null ? escapeHtml(value) : "") + "</button>";

            btnMatcher.appendReplacement(btnSb, Matcher.quoteReplacement(renderedBtn));
        }
        btnMatcher.appendTail(btnSb);
        html = btnSb.toString();

        // 13. Procesar <h:outputText value="#{...}" .../>
        Pattern outPattern = Pattern.compile("<h:outputText\\s+([^>]*?)/?>", Pattern.DOTALL);
        Matcher outMatcher = outPattern.matcher(html);
        StringBuilder outSb = new StringBuilder();
        while (outMatcher.find()) {
            String attrs = outMatcher.group(1);
            String valueExpr = extractAttr(attrs, "value");
            String styleClass = extractAttr(attrs, "styleClass");
            String val = resolveEl(valueExpr);

            String renderedOut = "<span " + (styleClass != null ? "class=\"" + styleClass + "\" " : "") + ">"
                    + (val != null ? escapeHtml(val) : "") + "</span>";

            outMatcher.appendReplacement(outSb, Matcher.quoteReplacement(renderedOut));
        }
        outMatcher.appendTail(outSb);
        html = outSb.toString();

        // 14. Reemplazar cualquier EL restante #{...} en texto general
        Pattern elPattern = Pattern.compile("#\\{([^}]+)\\}");
        Matcher elMatcher = elPattern.matcher(html);
        StringBuilder elSb = new StringBuilder();
        while (elMatcher.find()) {
            String expr = "#{" + elMatcher.group(1) + "}";
            String val = resolveEl(expr);
            elMatcher.appendReplacement(elSb, Matcher.quoteReplacement(val != null ? val : ""));
        }
        elMatcher.appendTail(elSb);
        html = elSb.toString();

        return html;
    }

    private String renderToolbars(String html) {
        Pattern tbPattern = Pattern.compile("<p:toolbar(?:\\s+[^>]*?)?>(.*?)</p:toolbar>", Pattern.DOTALL);
        Matcher tbMatcher = tbPattern.matcher(html);
        StringBuilder tbSb = new StringBuilder();
        while (tbMatcher.find()) {
            String content = tbMatcher.group(1);
            content = content.replace("<p:toolbarGroup align=\"left\">", "<div class=\"ui-toolbar-group-left\">")
                             .replace("<p:toolbarGroup align=\"right\">", "<div class=\"ui-toolbar-group-right\">")
                             .replace("<p:toolbarGroup>", "<div class=\"ui-toolbar-group-left\">")
                             .replace("</p:toolbarGroup>", "</div>");
            String rendered = "<div class=\"ui-toolbar ui-widget ui-widget-header\">" + content + "</div>";
            tbMatcher.appendReplacement(tbSb, Matcher.quoteReplacement(rendered));
        }
        tbMatcher.appendTail(tbSb);
        return tbSb.toString();
    }

    private String renderDialogs(String html) {
        Pattern dlgPattern = Pattern.compile("<p:dialog\\s+([^>]*?)>(.*?)</p:dialog>", Pattern.DOTALL);
        Matcher dlgMatcher = dlgPattern.matcher(html);
        StringBuilder dlgSb = new StringBuilder();
        while (dlgMatcher.find()) {
            String attrs = dlgMatcher.group(1);
            String body = dlgMatcher.group(2);
            String header = extractAttr(attrs, "header");
            String widgetVar = extractAttr(attrs, "widgetVar");
            String id = extractAttr(attrs, "id");
            String dlgId = widgetVar != null ? widgetVar : (id != null ? id : "dlg_" + Math.abs(attrs.hashCode()));

            // Extract footer facet if present
            String footerHtml = "";
            Pattern fPattern = Pattern.compile("<f:facet\\s+name=\"footer\">(.*?)</f:facet>", Pattern.DOTALL);
            Matcher fMatcher = fPattern.matcher(body);
            if (fMatcher.find()) {
                footerHtml = "<div class=\"ui-dialog-footer\">" + fMatcher.group(1) + "</div>";
                body = fMatcher.replaceAll("");
            }

            String dlgHtml = "<div class=\"ui-dialog-mask\" id=\"" + dlgId + "\" data-widget=\"" + dlgId + "\">\n"
                    + "  <div class=\"ui-dialog ui-widget\">\n"
                    + "    <div class=\"ui-dialog-titlebar\">\n"
                    + "      <span class=\"ui-dialog-title\">" + (header != null ? escapeHtml(header) : "") + "</span>\n"
                    + "      <button type=\"button\" class=\"p-button ui-button-secondary\" style=\"padding:2px 8px;\" onclick=\"PF('" + dlgId + "').hide()\">&#10005;</button>\n"
                    + "    </div>\n"
                    + "    <div class=\"ui-dialog-content\">" + body + "</div>\n"
                    + footerHtml + "\n"
                    + "  </div>\n"
                    + "</div>";

            dlgMatcher.appendReplacement(dlgSb, Matcher.quoteReplacement(dlgHtml));
        }
        dlgMatcher.appendTail(dlgSb);
        return dlgSb.toString();
    }

    private String renderDataTables(String html) {
        Pattern dtPattern = Pattern.compile("<(?:h|p):dataTable\\s+([^>]*?)>(.*?)</(?:h|p):dataTable>", Pattern.DOTALL);
        Matcher dtMatcher = dtPattern.matcher(html);
        StringBuilder dtSb = new StringBuilder();

        while (dtMatcher.find()) {
            String dtAttrs = dtMatcher.group(1);
            String dtBody = dtMatcher.group(2);

            boolean isPrime = dtMatcher.group(0).startsWith("<p:dataTable");
            String valueExpr = extractAttr(dtAttrs, "value");
            String varName = extractAttr(dtAttrs, "var");
            String styleClass = extractAttr(dtAttrs, "styleClass");
            String idAttr = extractAttr(dtAttrs, "id");
            String dtId = idAttr != null ? idAttr : "tbl_" + Math.abs(dtAttrs.hashCode());
            String sizeAttr = extractAttr(dtAttrs, "size");
            String paginatorAttr = extractAttr(dtAttrs, "paginator");
            boolean hasPaginator = "true".equalsIgnoreCase(paginatorAttr);
            String rowsAttr = extractAttr(dtAttrs, "rows");
            int rowsPerPage = rowsAttr != null ? Integer.parseInt(rowsAttr) : 10;

            String showGridlinesAttr = extractAttr(dtAttrs, "showGridlines");
            boolean showGridlines = "true".equalsIgnoreCase(showGridlinesAttr) || (dtAttrs.contains("gridlines"));
            String stripedRowsAttr = extractAttr(dtAttrs, "stripedRows");
            boolean stripedRows = "true".equalsIgnoreCase(stripedRowsAttr) || (dtAttrs.contains("striped"));

            Object listObj = getElObject(valueExpr);
            List<?> items = (listObj instanceof List<?> l) ? l : Collections.emptyList();

            // Check Table-level header and footer facets (outside columns)
            String tableHeaderHtml = null;
            String tableFooterHtml = null;

            // Pattern for header facet before columns
            Pattern thfPattern = Pattern.compile("<f:facet\\s+name=\"header\">(.*?)</f:facet>", Pattern.DOTALL);
            Matcher thfMatcher = thfPattern.matcher(dtBody);
            if (thfMatcher.find() && (!dtBody.substring(0, thfMatcher.start()).contains("<p:column") && !dtBody.substring(0, thfMatcher.start()).contains("<h:column"))) {
                tableHeaderHtml = thfMatcher.group(1);
                dtBody = dtBody.substring(0, thfMatcher.start()) + dtBody.substring(thfMatcher.end());
            }

            // Pattern for footer facet after columns
            Pattern tffPattern = Pattern.compile("<f:facet\\s+name=\"footer\">(.*?)</f:facet>", Pattern.DOTALL);
            Matcher tffMatcher = tffPattern.matcher(dtBody);
            if (tffMatcher.find()) {
                tableFooterHtml = tffMatcher.group(1);
                dtBody = dtBody.substring(0, tffMatcher.start()) + dtBody.substring(tffMatcher.end());
            }

            // Extraer columnas (<h:column> o <p:column>)
            List<String> headers = new ArrayList<>();
            List<String> colTemplates = new ArrayList<>();
            List<Boolean> isSortableList = new ArrayList<>();
            List<Boolean> isFilterableList = new ArrayList<>();
            List<Boolean> isSelectAllList = new ArrayList<>();

            Pattern colPattern = Pattern.compile("<(?:h|p):column(?:\\s+([^>]*?))?>(.*?)</(?:h|p):column>", Pattern.DOTALL);
            Matcher colMatcher = colPattern.matcher(dtBody);
            while (colMatcher.find()) {
                String colAttrs = colMatcher.group(1) != null ? colMatcher.group(1) : "";
                String colContent = colMatcher.group(2);

                String headerText = extractAttr(colAttrs, "headerText");
                String sortBy = extractAttr(colAttrs, "sortBy");
                String filterBy = extractAttr(colAttrs, "filterBy");
                String selectionMode = extractAttr(colAttrs, "selectionMode");

                isSortableList.add(sortBy != null && !sortBy.isBlank());
                isFilterableList.add(filterBy != null && !filterBy.isBlank());
                isSelectAllList.add("multiple".equalsIgnoreCase(selectionMode));

                if ("multiple".equalsIgnoreCase(selectionMode)) {
                    headers.add("__SELECTION_MODE_MULTIPLE__");
                } else if (headerText != null && !headerText.isBlank()) {
                    headers.add(headerText.trim());
                } else {
                    Pattern headerFacet = Pattern.compile("<f:facet\\s+name=\"header\">(.*?)</f:facet>", Pattern.DOTALL);
                    Matcher hfMatcher = headerFacet.matcher(colContent);
                    if (hfMatcher.find()) {
                        headers.add(hfMatcher.group(1).trim());
                        colContent = headerFacet.matcher(colContent).replaceAll("");
                    } else {
                        headers.add("");
                    }
                }
                colTemplates.add(colContent.trim());
            }

            StringBuilder tableHtml = new StringBuilder();
            if (isPrime) {
                String sizeClass = "small".equalsIgnoreCase(sizeAttr) ? "ui-datatable-sm" : ("large".equalsIgnoreCase(sizeAttr) ? "ui-datatable-lg" : "");
                String glClass = showGridlines ? "ui-datatable-gridlines" : "";
                String strClass = stripedRows ? "ui-datatable-striped" : "";

                tableHtml.append("<div class=\"ui-datatable ui-widget ").append(glClass).append(" ").append(strClass)
                         .append(" ").append(sizeClass).append(" ").append(styleClass != null ? styleClass : "")
                         .append("\" id=\"").append(dtId).append("\">\n");

                if (tableHeaderHtml != null) {
                    tableHtml.append("  <div class=\"ui-datatable-header ui-widget-header\">")
                             .append(renderCellComponents(tableHeaderHtml)).append("</div>\n");
                }

                tableHtml.append("  <div class=\"ui-datatable-tablewrapper\">\n");
                tableHtml.append("    <table role=\"grid\" class=\"p-datatable-table\" id=\"tbl_grid_").append(dtId).append("\">\n");
            } else {
                tableHtml.append("<table ").append(styleClass != null ? "class=\"" + styleClass + "\" " : "").append(">\n");
            }

            // Thead
            if (!headers.isEmpty()) {
                tableHtml.append("  <thead").append(isPrime ? " class=\"ui-datatable-thead\"" : "").append(">\n    <tr role=\"row\">\n");
                for (int c = 0; c < headers.size(); c++) {
                    String h = headers.get(c);
                    boolean sortable = isSortableList.get(c);
                    boolean filterable = isFilterableList.get(c);
                    boolean isMultipleSelect = isSelectAllList.get(c);

                    if (isMultipleSelect) {
                        tableHtml.append("      <th class=\"ui-state-default\" style=\"width: 3rem; text-align: center;\">")
                                 .append("<input type=\"checkbox\" class=\"ui-chkbox-all\" onclick=\"toggleSelectAll(this)\"/></th>\n");
                    } else if (isPrime) {
                        String sortAttr = sortable ? " class=\"ui-state-default ui-sortable-column p-column-title\" onclick=\"sortTable(this, " + c + ")\"" : " class=\"ui-state-default p-column-title\"";
                        tableHtml.append("      <th").append(sortAttr).append(" role=\"columnheader\">\n")
                                 .append("        <span class=\"ui-column-title\">").append(h).append("</span>\n");
                        if (sortable) {
                            tableHtml.append("        <span class=\"ui-sortable-column-icon pi pi-sort-alt\">&#8645;</span>\n");
                        }
                        if (filterable) {
                            tableHtml.append("        <div><input type=\"text\" class=\"ui-column-filter\" placeholder=\"Filtrar...\" onkeyup=\"filterColumn(this, ").append(c).append(")\"/></div>\n");
                        }
                        tableHtml.append("      </th>\n");
                    } else {
                        tableHtml.append("      <th>").append(h).append("</th>\n");
                    }
                }
                tableHtml.append("    </tr>\n  </thead>\n");
            }

            // Tbody
            tableHtml.append("  <tbody").append(isPrime ? " class=\"ui-datatable-data ui-widget-content\"" : "").append(">\n");
            int rowIndex = 0;
            for (Object item : items) {
                String rowClass = isPrime ? ((rowIndex % 2 == 0) ? "ui-widget-content ui-datatable-even" : "ui-widget-content ui-datatable-odd") : "";
                tableHtml.append("    <tr role=\"row\"").append(!rowClass.isEmpty() ? " class=\"" + rowClass + "\"" : "").append(">\n");
                for (int c = 0; c < colTemplates.size(); c++) {
                    String colTmpl = colTemplates.get(c);
                    boolean isMultipleSelect = isSelectAllList.get(c);

                    if (isMultipleSelect) {
                        tableHtml.append("      <td role=\"gridcell\" style=\"text-align: center;\"><input type=\"checkbox\" class=\"ui-chkbox-row\" onchange=\"toggleRowSelect(this)\"/></td>\n");
                        continue;
                    }

                    String cellContent = colTmpl;

                    // Reemplazar #{var.prop} y expresiones anidadas
                    if (varName != null) {
                        Pattern varPropPattern = Pattern.compile("#\\{" + Pattern.quote(varName) + "\\.([a-zA-Z0-9_.]+)\\}");
                        Matcher vpMatcher = varPropPattern.matcher(cellContent);
                        StringBuilder cellSb = new StringBuilder();
                        while (vpMatcher.find()) {
                            String propChain = vpMatcher.group(1);
                            Object current = item;
                            for (String part : propChain.split("\\.")) {
                                if (current == null) break;
                                if (part.endsWith("()")) {
                                    String mName = part.substring(0, part.length() - 2);
                                    try {
                                        Method m = current.getClass().getMethod(mName);
                                        m.setAccessible(true);
                                        current = m.invoke(current);
                                    } catch (Exception e) {
                                        current = null;
                                    }
                                } else {
                                    current = readProperty(current, part);
                                }
                            }
                            vpMatcher.appendReplacement(cellSb, Matcher.quoteReplacement(current != null ? String.valueOf(current) : ""));
                        }
                        vpMatcher.appendTail(cellSb);
                        cellContent = cellSb.toString();
                    }

                    // Procesar componentes internos de la celda
                    cellContent = renderCellComponents(cellContent);

                    tableHtml.append("      <td role=\"gridcell\">").append(cellContent).append("</td>\n");
                }
                tableHtml.append("    </tr>\n");
                rowIndex++;
            }
            tableHtml.append("  </tbody>\n</table>\n");
            if (isPrime) {
                tableHtml.append("  </div>\n");

                if (tableFooterHtml != null) {
                    tableHtml.append("  <div class=\"ui-datatable-footer ui-widget-header\">")
                             .append(renderCellComponents(tableFooterHtml)).append("</div>\n");
                }

                if (hasPaginator) {
                    int totalItems = items.size();
                    int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / rowsPerPage));
                    tableHtml.append("  <div class=\"ui-paginator ui-paginator-bottom ui-widget-header\" role=\"region\">\n")
                             .append("    <span class=\"ui-paginator-current\">Mostrando 1 a ").append(Math.min(rowsPerPage, totalItems)).append(" de ").append(totalItems).append(" productos</span>\n")
                             .append("    <a class=\"ui-paginator-first ui-state-default\" onclick=\"paginateTable('tbl_grid_").append(dtId).append("', 1, ").append(rowsPerPage).append(")\">&#171;</a>\n")
                             .append("    <a class=\"ui-paginator-prev ui-state-default\" onclick=\"paginateTable('tbl_grid_").append(dtId).append("', 1, ").append(rowsPerPage).append(")\">&#8249;</a>\n")
                             .append("    <span class=\"ui-paginator-pages\">\n");

                    for (int p = 1; p <= totalPages; p++) {
                        String activeCls = (p == 1) ? " ui-state-active" : "";
                        tableHtml.append("      <a class=\"ui-paginator-page ui-state-default").append(activeCls)
                                 .append("\" onclick=\"paginateTable('tbl_grid_").append(dtId).append("', ").append(p).append(", ").append(rowsPerPage).append(")\">")
                                 .append(p).append("</a>\n");
                    }

                    tableHtml.append("    </span>\n")
                             .append("    <a class=\"ui-paginator-next ui-state-default\" onclick=\"paginateTable('tbl_grid_").append(dtId).append("', 2, ").append(rowsPerPage).append(")\">&#8250;</a>\n")
                             .append("    <a class=\"ui-paginator-last ui-state-default\" onclick=\"paginateTable('tbl_grid_").append(dtId).append("', ").append(totalPages).append(", ").append(rowsPerPage).append(")\">&#187;</a>\n")
                             .append("  </div>\n");
                }

                tableHtml.append("</div>");
            }

            dtMatcher.appendReplacement(dtSb, Matcher.quoteReplacement(tableHtml.toString()));
        }
        dtMatcher.appendTail(dtSb);
        return dtSb.toString();
    }


    private String renderCellComponents(String cellContent) {
        // Renderizar <h:outputText>
        Pattern outPattern = Pattern.compile("<h:outputText\\s+([^>]*?)(?:/?>|>(.*?)</h:outputText>)", Pattern.DOTALL);
        Matcher outMatcher = outPattern.matcher(cellContent);
        StringBuilder outSb = new StringBuilder();
        while (outMatcher.find()) {
            String attrs = outMatcher.group(1);
            String val = extractAttr(attrs, "value");
            if (val == null || val.isBlank()) {
                val = outMatcher.group(2) != null ? outMatcher.group(2).trim() : "";
            }
            String styleClass = extractAttr(attrs, "styleClass");
            String rendered = (styleClass != null ? "<span class=\"" + styleClass + "\">" : "")
                    + escapeHtml(val)
                    + (styleClass != null ? "</span>" : "");
            outMatcher.appendReplacement(outSb, Matcher.quoteReplacement(rendered));
        }
        outMatcher.appendTail(outSb);
        cellContent = outSb.toString();

        // Renderizar <p:tag>
        cellContent = renderPrimeTags(cellContent);

        // Renderizar <p:rating>
        cellContent = renderPrimeRatings(cellContent);

        // Renderizar <p:badge>
        cellContent = renderPrimeBadges(cellContent);

        return cellContent;
    }

    private String renderPrimeTags(String html) {
        Pattern tagPattern = Pattern.compile("<p:tag\\s+([^>]*?)(?:/?>|>(.*?)</p:tag>)", Pattern.DOTALL);
        Matcher tagMatcher = tagPattern.matcher(html);
        StringBuilder tagSb = new StringBuilder();
        while (tagMatcher.find()) {
            String attrs = tagMatcher.group(1);
            String val = extractAttr(attrs, "value");
            if (val == null || val.isBlank()) {
                val = tagMatcher.group(2) != null ? tagMatcher.group(2).trim() : "";
            }
            String sev = extractAttr(attrs, "severity");
            if (sev == null) sev = "info";
            sev = sev.toLowerCase().trim();
            if (sev.equals("instock")) sev = "success";
            else if (sev.equals("lowstock")) sev = "warning";
            else if (sev.equals("outofstock")) sev = "danger";

            String rounded = extractAttr(attrs, "rounded");
            boolean isRounded = "true".equalsIgnoreCase(rounded);
            String icon = extractAttr(attrs, "icon");
            String styleClass = extractAttr(attrs, "styleClass");

            String tagHtml = "<span class=\"p-tag p-tag-" + sev + (isRounded ? " p-tag-rounded" : "")
                    + (styleClass != null ? " " + styleClass : "") + "\">"
                    + (icon != null ? "<i class=\"" + icon + " p-tag-icon\"></i> " : "")
                    + "<span class=\"p-tag-value\">" + escapeHtml(val) + "</span></span>";

            tagMatcher.appendReplacement(tagSb, Matcher.quoteReplacement(tagHtml));
        }
        tagMatcher.appendTail(tagSb);
        return tagSb.toString();
    }

    private String renderPrimeRatings(String html) {
        Pattern rPattern = Pattern.compile("<p:rating\\s+([^>]*?)(?:/?>|>(.*?)</p:rating>)", Pattern.DOTALL);
        Matcher rMatcher = rPattern.matcher(html);
        StringBuilder rSb = new StringBuilder();
        while (rMatcher.find()) {
            String attrs = rMatcher.group(1);
            String valStr = extractAttr(attrs, "value");
            int stars = 0;
            try {
                if (valStr != null && !valStr.isBlank()) {
                    stars = (int) Math.round(Double.parseDouble(valStr.trim()));
                }
            } catch (Exception ignored) {}

            StringBuilder starsHtml = new StringBuilder("<div class=\"p-rating\" title=\"" + stars + "/5\">");
            for (int s = 1; s <= 5; s++) {
                if (s <= stars) {
                    starsHtml.append("<span style=\"color:#f59e0b;\">&#9733;</span>");
                } else {
                    starsHtml.append("<span style=\"color:#cbd5e1;\">&#9734;</span>");
                }
            }
            starsHtml.append("</div>");

            rMatcher.appendReplacement(rSb, Matcher.quoteReplacement(starsHtml.toString()));
        }
        rMatcher.appendTail(rSb);
        return rSb.toString();
    }

    private String renderPrimeBadges(String html) {
        Pattern bPattern = Pattern.compile("<p:badge\\s+([^>]*?)(?:/?>|>(.*?)</p:badge>)", Pattern.DOTALL);
        Matcher bMatcher = bPattern.matcher(html);
        StringBuilder bSb = new StringBuilder();
        while (bMatcher.find()) {
            String attrs = bMatcher.group(1);
            String val = extractAttr(attrs, "value");
            if (val == null || val.isBlank()) {
                val = bMatcher.group(2) != null ? bMatcher.group(2).trim() : "";
            }
            String sev = extractAttr(attrs, "severity");
            if (sev == null) sev = "info";

            String bHtml = "<span class=\"p-badge p-badge-" + sev + "\">" + escapeHtml(val) + "</span>";
            bMatcher.appendReplacement(bSb, Matcher.quoteReplacement(bHtml));
        }
        bMatcher.appendTail(bSb);
        return bSb.toString();
    }

    private String resolveEl(String expr) {
        if (expr == null) return null;
        Object obj = getElObject(expr);
        return obj != null ? obj.toString() : "";
    }

    private Object getElObject(String expr) {
        if (expr == null || !expr.startsWith("#{") || !expr.endsWith("}")) {
            return expr;
        }
        String content = expr.substring(2, expr.length() - 1).trim();
        int dotIdx = content.indexOf('.');
        if (dotIdx < 0) {
            return JettraCDIContainer.getInstance().getBeanByName(content);
        }

        String beanName = content.substring(0, dotIdx).trim();
        String property = content.substring(dotIdx + 1).trim();

        Object bean = JettraCDIContainer.getInstance().getBeanByName(beanName);
        if (bean == null) return null;

        return readProperty(bean, property);
    }

    private void setElValue(String expr, String value) {
        if (expr == null || !expr.startsWith("#{") || !expr.endsWith("}")) {
            return;
        }
        String content = expr.substring(2, expr.length() - 1).trim();
        int dotIdx = content.indexOf('.');
        if (dotIdx <= 0) return;

        String beanName = content.substring(0, dotIdx).trim();
        String property = content.substring(dotIdx + 1).trim();

        Object bean = JettraCDIContainer.getInstance().getBeanByName(beanName);
        if (bean == null) return;

        writeProperty(bean, property, value);
    }

    private Object readProperty(Object target, String prop) {
        if (target == null) return null;
        String getterName = "get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
        String isName = "is" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);

        try {
            Method m;
            try {
                m = target.getClass().getMethod(getterName);
            } catch (NoSuchMethodException e) {
                m = target.getClass().getMethod(isName);
            }
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception e) {
            try {
                var field = target.getClass().getDeclaredField(prop);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void writeProperty(Object target, String prop, String value) {
        if (target == null) return;
        String setterName = "set" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);

        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                try {
                    Class<?> pType = m.getParameterTypes()[0];
                    Object convertedVal = convert(value, pType);
                    m.setAccessible(true);
                    m.invoke(target, convertedVal);
                    return;
                } catch (Exception e) {
                    IO.error("Error asignando propiedad Faces " + prop, e);
                }
            }
        }
    }

    private Object convert(String val, Class<?> type) {
        if (val == null) return null;
        if (type == String.class) return val;
        if (type == int.class || type == Integer.class) return Integer.parseInt(val.trim());
        if (type == long.class || type == Long.class) return Long.parseLong(val.trim());
        if (type == double.class || type == Double.class) return Double.parseDouble(val.trim());
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(val.trim());
        return val;
    }

    private String extractAttr(String attrs, String attrName) {
        Pattern p = Pattern.compile(Pattern.quote(attrName) + "=\"([^\"]*)\"");
        Matcher m = p.matcher(attrs);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (InputStream is = exchange.getRequestBody()) {
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) return map;

            for (String pair : raw.split("&")) {
                int idx = pair.indexOf("=");
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
                String val = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                map.put(key, val);
            }
        }
        return map;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private void send404(HttpExchange exchange, String path) throws IOException {
        String html = "<html><body style='font-family:sans-serif;padding:40px;'>"
                + "<h2>404 - Vista Faces No Encontrada</h2>"
                + "<p>No se encontró el archivo Facelets: <code>" + path + "</code></p>"
                + "</body></html>";
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
        exchange.sendResponseHeaders(404, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }
}
