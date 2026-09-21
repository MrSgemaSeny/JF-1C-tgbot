package kz.zhanfinance.bot.client;

public class BackendClientException extends RuntimeException {
    private final int statusCode;

    public BackendClientException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public BackendClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public BackendClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
