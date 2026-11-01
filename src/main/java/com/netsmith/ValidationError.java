package com.netsmith;

public class ValidationError extends RuntimeException {

    public enum Field {
        CIDR,
        SUBNET_COUNT,
        HOST_COUNT,
        HOST_LIST,
        PREFIX,
        DEPT_COUNT,
        IP_START,
        IP_END,
        IP,
        IPV6_CIDR,
        IPV6_ADDRESS,
        MAC_ADDRESS,
        IPV4_ADDRESS,
        UNKNOWN
    }

    private final Field field;
    private final String message;

    public ValidationError(Field field, String message) {
        super(message);
        this.field = field;
        this.message = message;
    }

    public ValidationError(Field field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
        this.message = message;
    }

    public Field getField() {
        return field;
    }

    @Override
    public String getMessage() {
        return message;
    }
}