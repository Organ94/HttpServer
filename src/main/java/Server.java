import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final ExecutorService threadPool = Executors.newFixedThreadPool(64);
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();

    private final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
    private final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};

    public void listen(int port) {
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket socket = server.accept();
                threadPool.submit(new ServerHandler(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        Map<String, Handler> map = new ConcurrentHashMap<>();
        if (handlers.containsKey(method)) {
            map = handlers.get(method);
        }
        map.put(path, handler);
        handlers.put(method, map);
    }

    private class ServerHandler implements Runnable {

        private final Socket socket;

        public ServerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                final int limit = 4096;
                in.mark(limit);
                final byte[] buffer = new byte[limit];
                final int read = in.read(buffer);

                RequestLine requestLine = getRequestLine(buffer, read);
                if (requestLine == null) {
                    badRequest(out);
                    return;
                }

                List<String> headers = getHeaders(buffer, read, in);
                if (headers == null) {
                    badRequest(out);
                    return;
                }

                Request request = new Request(requestLine, headers);
                request.setBody(getBody(request, in));
                request.setQueryParams(getQueryParams(requestLine.getPath()));

                runHAndler(request, out);
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    private void runHAndler(Request request, BufferedOutputStream out) throws IOException {
        Handler handler = handlers.get(request.getRequestLine().getMethod())
                .get(request.getRequestLine().getPath());
        if (handler != null) {
            handler.handle(request, out);
        } else {
            notFound(out);
        }
    }

    private void notFound(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                ).getBytes());
        out.flush();
    }

    private List<NameValuePair> getQueryParams(String path) throws URISyntaxException {
        final URI uri = new URI(path);
        return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
    }

    private String getBody(Request request, BufferedInputStream in) throws IOException {
        if (!request.getRequestLine().getMethod().equals("GET")) {
            in.skip(headersDelimiter.length);
            final Optional<String> contentLength = extractHeader(request.getHeaders(), "Content-Length");
            if (contentLength.isPresent()) {
                final int length = Integer.parseInt(contentLength.get());
                final byte[] bodyBytes = in.readNBytes(length);
                return new String(bodyBytes);
            }
        }
        return null;
    }

    private List<String> getHeaders(byte[] buffer, int read, BufferedInputStream in) throws IOException {
        final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);

        final int headersStart = requestLineEnd - requestLineDelimiter.length;
        final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);

        if (headersEnd == -1) {
            return null;
        }

        in.reset();
        in.skip(headersStart);

        final byte[] headersBytes = in.readNBytes(headersEnd - headersStart);
        return Arrays.asList(new String(headersBytes).split("\r\n"));
    }

    private RequestLine getRequestLine(byte[] buffer, int read) {
        final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            return null;
        }

        final String[] parts = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (parts.length != 3) {
            return null;
        }
        if(!parts[1].startsWith("/")) {
            return null;
        }
        return new RequestLine(parts[0], parts[1], parts[2]);
    }

    private Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                ).getBytes());
        out.flush();
    }

    private int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = 0; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
