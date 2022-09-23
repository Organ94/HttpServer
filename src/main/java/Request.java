import java.util.List;

public class Request {

    private final RequestLine requestLine;
    private final List<String> headers;
    private String body;

    public Request(RequestLine requestLine, List<String> headers) {
        this.requestLine = requestLine;
        this.headers = headers;
    }

    public RequestLine getRequestLine() {
        return requestLine;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "Request{" +
                "requestLine=" + requestLine +
                ", headers=" + headers +
                ", body='" + body + '\'' +
                '}';
    }
}
