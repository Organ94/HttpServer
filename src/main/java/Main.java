import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        final List<String> validPaths = List.of(
                "/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css",
                "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js"
        );

        int port = 9999;

        Server server = new Server();

        Handler handler = (request, out) -> {
            try {
                final Path filePath = Path.of(".", "public", request.getRequestLine().getPath());
                final String mimeType = Files.probeContentType(filePath);
                final long length = Files.size(filePath);

                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                Files.copy(filePath, out);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        for (String validPath : validPaths) {
            if (!validPath.equals("/classic.html")) {
                server.addHandler("GET", validPath, handler);
            }

            server.addHandler("GET", "/classic.html", (request, out) -> {
                final Path filePath = Path.of(".", "public", "/classic.html");
                final String mimeType = Files.probeContentType(filePath);
                final String template = Files.readString(filePath);
                final byte[] content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();

                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                        ).getBytes());
                out.write(content);
                out.flush();
            });
        }

        server.listen(port);
    }
}
