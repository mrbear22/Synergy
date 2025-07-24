package me.synergy.web;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class LogsHandler implements HttpHandler {
    
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    private static final Map<String, CachedLogFile> archiveCache = new ConcurrentHashMap<>();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!isAuthorized(exchange)) {
            sendAuthRequest(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        if (!"GET".equals(method)) {
            String response = "405 Method Not Allowed";
            exchange.sendResponseHeaders(405, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        LogSearchParams params = parseSearchParams(query);
        
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            String response = "404 Logs directory not found";
            exchange.sendResponseHeaders(404, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            return;
        }

        try {
            List<LogEntry> allLogs = new ArrayList<>();
            
            File[] logFiles = logsDir.listFiles();
            if (logFiles != null) {
                Arrays.sort(logFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                
                for (File logFile : logFiles) {
                    if (logFile.isFile()) {
                        if (logFile.getName().equals("latest.log")) {
                            readLogFile(logFile, allLogs, "latest.log");
                        } else if (logFile.getName().endsWith(".log.gz")) {
                            readCachedGzipLogFile(logFile, allLogs, logFile.getName());
                        } else if (logFile.getName().endsWith(".log") && !logFile.getName().equals("latest.log")) {
                            readCachedLogFile(logFile, allLogs, logFile.getName());
                        }
                    }
                }
            }
            
            allLogs.sort(Comparator.comparingLong(entry -> entry.parsedTimestamp));
            
            if (params.startDate != null || params.endDate != null) {
                allLogs.removeIf(entry -> !isWithinDateRange(entry, params.startDate, params.endDate));
            }

            if (!params.searchKeywords.isEmpty()) {
                List<Pattern> searchPatterns = new ArrayList<>();
                for (String keyword : params.searchKeywords) {
                    searchPatterns.add(Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE));
                }
                
                allLogs.removeIf(entry -> {
                    for (Pattern pattern : searchPatterns) {
                        if (!pattern.matcher(entry.content).find()) {
                            return true;
                        }
                    }
                    return false;
                });
            }
            
            if (!params.showAll && allLogs.size() > params.maxLines) {
                allLogs = allLogs.subList(Math.max(0, allLogs.size() - params.maxLines), allLogs.size());
            }
            
            String response = generateHtmlResponse(allLogs, params);
            
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();
            
        } catch (IOException e) {
            String response = "500 Internal Server Error: " + e.getMessage();
            exchange.sendResponseHeaders(500, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    private void readCachedLogFile(File file, List<LogEntry> logs, String fileName) throws IOException {
        String cacheKey = file.getAbsolutePath();
        long fileModified = file.lastModified();
        
        CachedLogFile cached = archiveCache.get(cacheKey);
        
        if (cached != null && cached.lastModified == fileModified) {
            logs.addAll(cached.entries);
        } else {
            List<LogEntry> fileEntries = new ArrayList<>();
            readLogFile(file, fileEntries, fileName);
            
            archiveCache.put(cacheKey, new CachedLogFile(fileEntries, fileModified));
            logs.addAll(fileEntries);
        }
    }
    
    private void readCachedGzipLogFile(File file, List<LogEntry> logs, String fileName) throws IOException {
        String cacheKey = file.getAbsolutePath();
        long fileModified = file.lastModified();
        
        CachedLogFile cached = archiveCache.get(cacheKey);
        
        if (cached != null && cached.lastModified == fileModified) {
            logs.addAll(cached.entries);
        } else {
            List<LogEntry> fileEntries = new ArrayList<>();
            readGzipLogFile(file, fileEntries, fileName);

            archiveCache.put(cacheKey, new CachedLogFile(fileEntries, fileModified));
            logs.addAll(fileEntries);
        }
    }
    
    private LogSearchParams parseSearchParams(String query) {
        LogSearchParams params = new LogSearchParams();
        
        if (query != null) {
            String[] queryParams = query.split("&");
            for (String param : queryParams) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value;
                    try {
                        value = URLDecoder.decode(keyValue[1], "UTF-8");
                    } catch (Exception e) {
                        continue;
                    }
                    
                    switch (key) {
                        case "search":
                            if (!value.trim().isEmpty()) {
                                String[] keywords = value.split(",");
                                for (String keyword : keywords) {
                                    String trimmed = keyword.trim();
                                    if (!trimmed.isEmpty()) {
                                        params.searchKeywords.add(trimmed);
                                    }
                                }
                            }
                            break;
                        case "lines":
                            try {
                                params.maxLines = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                params.maxLines = 10000;
                            }
                            break;
                        case "all":
                            params.showAll = "true".equals(value);
                            break;
                        case "start_date":
                            try {
                                params.startDate = LocalDate.parse(value);
                            } catch (DateTimeParseException e) {
                            }
                            break;
                        case "end_date":
                            try {
                                params.endDate = LocalDate.parse(value);
                            } catch (DateTimeParseException e) {
                            }
                            break;
                        case "clear_cache":
                            if ("true".equals(value)) {
                                clearCache();
                            }
                            break;
                    }
                }
            }
        }
        
        return params;
    }
    
    private void clearCache() {
        archiveCache.clear();
        System.out.println("Archive cache cleared");
    }
    
    private boolean isWithinDateRange(LogEntry entry, LocalDate startDate, LocalDate endDate) {
        if (entry.parsedDate == null) return true;
        
        if (startDate != null && entry.parsedDate.isBefore(startDate)) {
            return false;
        }
        
        if (endDate != null && entry.parsedDate.isAfter(endDate)) {
            return false;
        }
        
        return true;
    }
    
    private void readLogFile(File file, List<LogEntry> logs, String fileName) throws IOException {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                LogEntry entry = new LogEntry(line, fileName, file.lastModified());
                logs.add(entry);
            }
        } catch (IOException e) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LogEntry entry = new LogEntry(line, fileName, file.lastModified());
                    logs.add(entry);
                }
            }
        }
    }
    
    private void readGzipLogFile(File file, List<LogEntry> logs, String fileName) throws IOException {
        try (GZIPInputStream gzipStream = new GZIPInputStream(new FileInputStream(file));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = new LogEntry(line, fileName, file.lastModified());
                logs.add(entry);
            }
        }
    }
    
    private String generateHtmlResponse(List<LogEntry> logs, LogSearchParams params) {
        StringBuilder htmlResponse = new StringBuilder();
        htmlResponse.append("<!DOCTYPE html>");
        htmlResponse.append("<html><head>");
        htmlResponse.append("<title>Server Logs</title>");
        htmlResponse.append("<meta charset='UTF-8'>");
        htmlResponse.append("<style>");
        htmlResponse.append("body { font-family: monospace; background-color: #1e1e1e; color: #ffffff; margin: 20px; }");
        htmlResponse.append("h1 { color: #00ff00; }");
        htmlResponse.append(".controls { background-color: #2d2d2d; padding: 15px; border-radius: 5px; margin-bottom: 20px; }");
        htmlResponse.append(".controls input, .controls select, .controls button { margin: 5px; padding: 8px; background-color: #1e1e1e; color: #ffffff; border: 1px solid #555; border-radius: 3px; }");
        htmlResponse.append(".controls button { background-color: #007acc; cursor: pointer; }");
        htmlResponse.append(".controls button:hover { background-color: #005a9e; }");
        htmlResponse.append(".controls label { margin-right: 15px; display: inline-block; }");
        htmlResponse.append(".date-range { margin: 10px 0; }");
        htmlResponse.append(".cache-info { color: #888; font-size: 0.8em; margin: 10px 0; }");
        htmlResponse.append(".log-container { background-color: #2d2d2d; padding: 20px; border-radius: 5px; max-height: 80vh; overflow-y: auto; }");
        htmlResponse.append(".log-line { margin: 2px 0; white-space: pre-wrap; word-wrap: break-word; }");
        htmlResponse.append(".log-file { color: #888; font-size: 0.9em; border-bottom: 1px solid #444; padding: 5px 0; margin: 10px 0 5px 0; }");
        htmlResponse.append(".info { color: #00ffff; }");
        htmlResponse.append(".warn { color: #ffff00; }");
        htmlResponse.append(".error { color: #ff0000; }");
        htmlResponse.append(".debug { color: #808080; }");
        htmlResponse.append(".highlight { background-color: #ffff00; color: #000000; font-weight: bold; }");
        htmlResponse.append(".stats { color: #888; margin-bottom: 10px; }");
        htmlResponse.append(".auth-info { color: #00ff00; margin-bottom: 10px; font-size: 0.9em; }");
        htmlResponse.append(".keyword-hint { color: #888; font-size: 0.8em; margin-left: 10px; }");
        htmlResponse.append("</style>");
        htmlResponse.append("</head><body>");
        htmlResponse.append("<h1>Server Logs - All Files</h1>");
        
        htmlResponse.append("<div class='auth-info'>✓ Авторизовано як: admin</div>");
        
        htmlResponse.append("<div class='controls'>");
        htmlResponse.append("<form method='GET' style='display: inline;'>");
        
        String searchValue = String.join(", ", params.searchKeywords);
        htmlResponse.append("<label>Ключові слова:</label>");
        htmlResponse.append("<input type='text' name='search' value='").append(escapeHtml(searchValue)).append("' placeholder='Пошук по логах (через кому)...' style='width: 300px;'>");
        htmlResponse.append("<span class='keyword-hint'>Розділяйте ключові слова комами</span><br>");
        
        htmlResponse.append("<div class='date-range'>");
        htmlResponse.append("<label>Період:</label>");
        htmlResponse.append("<input type='date' name='start_date' value='").append(params.startDate != null ? params.startDate.toString() : "").append("' placeholder='Від дати'>");
        htmlResponse.append("<input type='date' name='end_date' value='").append(params.endDate != null ? params.endDate.toString() : "").append("' placeholder='До дати'>");
        htmlResponse.append("</div>");
        
        htmlResponse.append("<label>Кількість рядків:</label>");
        htmlResponse.append("<select name='lines'>");
        htmlResponse.append("<option value='1000'").append(params.maxLines == 1000 ? " selected" : "").append(">1000 рядків</option>");
        htmlResponse.append("<option value='5000'").append(params.maxLines == 5000 ? " selected" : "").append(">5000 рядків</option>");
        htmlResponse.append("<option value='10000'").append(params.maxLines == 10000 ? " selected" : "").append(">10000 рядків</option>");
        htmlResponse.append("<option value='50000'").append(params.maxLines == 50000 ? " selected" : "").append(">50000 рядків</option>");
        htmlResponse.append("</select>");
        
        htmlResponse.append("<label><input type='checkbox' name='all' value='true'").append(params.showAll ? " checked" : "").append("> Показати всі</label>");
        htmlResponse.append("<button type='submit'>Пошук</button>");
        htmlResponse.append("</form>");
        htmlResponse.append("<button onclick='location.href=\"/logs\"'>Очистити</button>");
        htmlResponse.append("<button onclick='location.href=\"/logs?clear_cache=true\"' style='background-color: #cc7a00;'>Очистити кеш</button>");
        htmlResponse.append("</div>");
        
        htmlResponse.append("<div class='cache-info'>");
        htmlResponse.append("📂 Кешовано архівних файлів: ").append(archiveCache.size());
        htmlResponse.append("</div>");
        
        htmlResponse.append("<div class='stats'>");
        htmlResponse.append("Знайдено рядків: ").append(logs.size());
        if (!params.searchKeywords.isEmpty()) {
            htmlResponse.append(" (пошук: \"").append(escapeHtml(String.join(", ", params.searchKeywords))).append("\")");
        }
        if (params.startDate != null || params.endDate != null) {
            htmlResponse.append(" (період: ");
            if (params.startDate != null) {
                htmlResponse.append("з ").append(params.startDate);
            }
            if (params.endDate != null) {
                if (params.startDate != null) htmlResponse.append(" ");
                htmlResponse.append("до ").append(params.endDate);
            }
            htmlResponse.append(")");
        }
        htmlResponse.append("</div>");
        
        htmlResponse.append("<div class='log-container'>");
        
        String currentFile = "";
        for (LogEntry entry : logs) {
            if (!currentFile.equals(entry.fileName)) {
                currentFile = entry.fileName;
                String cacheIndicator = entry.fileName.equals("latest.log") ? "" : " (кешовано)";
                htmlResponse.append("<div class='log-file'>📁 ").append(escapeHtml(currentFile)).append(cacheIndicator).append("</div>");
            }
            
            String line = entry.content;
            String cssClass = getLogLevelClass(line);
            
            if (!params.searchKeywords.isEmpty()) {
                line = highlightKeywords(line, params.searchKeywords);
            }
            
            htmlResponse.append("<div class='log-line ").append(cssClass).append("'>");
            htmlResponse.append(line);
            htmlResponse.append("</div>");
        }
        
        if (logs.isEmpty()) {
            htmlResponse.append("<div style='text-align: center; color: #888; padding: 50px;'>");
            htmlResponse.append("Не знайдено записів за вказаними критеріями");
            htmlResponse.append("</div>");
        }
        
        htmlResponse.append("</div>");
        htmlResponse.append("<script>");
        htmlResponse.append("window.scrollTo(0, document.body.scrollHeight);"); // Автоскрол вниз
        htmlResponse.append("</script>");
        htmlResponse.append("</body></html>");
        
        return htmlResponse.toString();
    }
    
    private String highlightKeywords(String text, List<String> keywords) {
        if (keywords.isEmpty()) return escapeHtml(text);
        
        String escapedText = escapeHtml(text);
        
        for (String keyword : keywords) {
            String escapedKeyword = escapeHtml(keyword.trim());
            if (!escapedKeyword.isEmpty()) {
                Pattern pattern = Pattern.compile(Pattern.quote(escapedKeyword), Pattern.CASE_INSENSITIVE);
                escapedText = pattern.matcher(escapedText).replaceAll("<span class='highlight'>$0</span>");
            }
        }
        
        return escapedText;
    }
    
    private String getLogLevelClass(String line) {
        if (line.contains("[INFO]")) return "info";
        if (line.contains("[WARN]")) return "warn";
        if (line.contains("[ERROR]")) return "error";
        if (line.contains("[DEBUG]")) return "debug";
        return "";
    }
    
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    private boolean isAuthorized(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        try {
            String base64Credentials = authHeader.substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            String[] parts = credentials.split(":", 2);
            
            if (parts.length == 2) {
                String username = parts[0];
                String password = parts[1];
                return ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password);
            }
        } catch (Exception e) {
            return false;
        }
        
        return false;
    }
    
    private void sendAuthRequest(HttpExchange exchange) throws IOException {
        String response = "401 Unauthorized - Authentication required";
        exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"Logs Access\"");
        exchange.sendResponseHeaders(401, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
    
    private static class LogSearchParams {
        List<String> searchKeywords = new ArrayList<>();
        int maxLines = 10000;
        boolean showAll = false;
        LocalDate startDate = null;
        LocalDate endDate = null;
    }
    
    private static class LogEntry {
        String content;
        String fileName;
        long timestamp;
        long parsedTimestamp;
        LocalDate parsedDate;
        
        LogEntry(String content, String fileName, long timestamp) {
            this.content = content;
            this.fileName = fileName;
            this.timestamp = timestamp;
            this.parsedTimestamp = parseTimestampFromLog(content, timestamp);
            this.parsedDate = parseDateFromLog(content, timestamp);
        }
        
        private long parseTimestampFromLog(String logLine, long fallbackTimestamp) {
            try {
                if (logLine.startsWith("[") && logLine.contains("]")) {
                    String dateStr = logLine.substring(1, logLine.indexOf("]"));
                    
                    // Спробуємо різні формати
                    String[] formats = {
                        "yyyy-MM-dd HH:mm:ss",
                        "HH:mm:ss",
                        "dd/MM/yyyy HH:mm:ss",
                        "MM-dd HH:mm:ss"
                    };
                    
                    for (String format : formats) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                            if (format.equals("HH:mm:ss")) {
                                LocalDateTime fileDate = LocalDateTime.ofEpochSecond(fallbackTimestamp / 1000, 0, 
                                    java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.ofEpochMilli(fallbackTimestamp)));
                                LocalDateTime parsed = LocalDateTime.of(fileDate.toLocalDate(), 
                                    java.time.LocalTime.parse(dateStr, formatter));
                                return parsed.toEpochSecond(java.time.ZoneOffset.systemDefault().getRules().getOffset(parsed)) * 1000;
                            } else {
                                LocalDateTime parsed = LocalDateTime.parse(dateStr, formatter);
                                return parsed.toEpochSecond(java.time.ZoneOffset.systemDefault().getRules().getOffset(parsed)) * 1000;
                            }
                        } catch (DateTimeParseException e) {
                        }
                    }
                }
            } catch (Exception e) {
            }
            
            return fallbackTimestamp;
        }
        
        private LocalDate parseDateFromLog(String logLine, long fallbackTimestamp) {
            try {
                long timestamp = parseTimestampFromLog(logLine, fallbackTimestamp);
                return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, 
                    java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.ofEpochMilli(timestamp)))
                    .toLocalDate();
            } catch (Exception e) {
                return LocalDateTime.ofEpochSecond(fallbackTimestamp / 1000, 0, 
                    java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.ofEpochMilli(fallbackTimestamp)))
                    .toLocalDate();
            }
        }
    }
    
    private static class CachedLogFile {
        final List<LogEntry> entries;
        final long lastModified;
        
        CachedLogFile(List<LogEntry> entries, long lastModified) {
            this.entries = new ArrayList<>(entries);
            this.lastModified = lastModified;
        }
    }
}