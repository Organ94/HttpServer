import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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

                final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
                if (requestLineEnd == -1) {
                    badRequest(out);
                    return;
                }

                final String[] parts = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
                if (parts.length != 3) {
                    badRequest(out);
                    return;
                }

                if (!parts[1].startsWith("/")) {
                    badRequest(out);
                    return;
                }

                RequestLine requestLine = new RequestLine(parts[0], parts[1], parts[2]);

                final int headersStart = requestLineEnd - requestLineDelimiter.length;
                final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
                if (headersEnd == -1) {
                    badRequest(out);
                    return;
                }

                in.reset();
                in.skip(headersStart);

                final byte[] headersBytes = in.readNBytes(headersEnd - headersStart);
                final List<String> headers = Arrays.asList(new String(headersBytes).split("\r\n"));

                Request request = new Request(requestLine, headers);
                if (!requestLine.getMethod().equals("GET")) {
                    in.skip(headersDelimiter.length);
                    final Optional<String> contentLength = extractHeader(headers, "Content-Length");
                    if (contentLength.isPresent()) {
                        final int length = Integer.parseInt(contentLength.get());
                        final byte[] bodyBytes = in.readNBytes(length);

                        final String body = new String(bodyBytes);
                        request.setBody(body);
                    }
                }

                Handler handler = handlers.get(request.getRequestLine().getMethod())
                        .get(request.getRequestLine().getPath());
                handler.handle(request, out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
