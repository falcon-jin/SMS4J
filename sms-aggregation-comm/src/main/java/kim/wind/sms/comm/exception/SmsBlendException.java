package kim.wind.sms.comm.exception;

public class SmsBlendException extends RuntimeException{
    public String code;
    public String message;
    public String requestId;

    public SmsBlendException(String message) {
        super(message);
        this.message = message;
    }

    public SmsBlendException(String code, String message) {
        super("[" + code + "] " + message);
        this.message = message;
        this.code = code;
    }

    public SmsBlendException(String code, String message, String requestId) {
        super("[" + code + "] " + message);
        this.message = message;
        this.code = code;
        this.code = requestId;
    }
}
