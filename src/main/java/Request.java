import org.apache.http.NameValuePair;

import java.util.List;
import java.util.stream.Collectors;

public class Request {

    private final RequestLine requestLine;
    private final List<String> headers;
    private String body;
    private List<NameValuePair> queryParams;

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

    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }

    public List<String> getQueryParam(String name) {
        return queryParams.stream()
                .filter(o -> o.getName().startsWith(name))
                .map(NameValuePair::getValue)
                .collect(Collectors.toList());
    }

    public void setQueryParams(List<NameValuePair> queryParams) {
        this.queryParams = queryParams;
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
