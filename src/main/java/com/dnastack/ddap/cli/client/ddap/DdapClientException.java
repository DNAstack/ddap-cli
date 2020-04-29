package com.dnastack.ddap.cli.client.ddap;

public class DdapClientException extends RuntimeException {

    public DdapClientException() {
    }
    public DdapClientException(String message) {
        super(message);
    }

    public DdapClientException(Throwable cause) {
        super(cause);
    }
}
