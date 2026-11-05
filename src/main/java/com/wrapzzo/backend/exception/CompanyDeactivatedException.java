package com.wrapzzo.backend.exception;

/** The person's company has been deactivated by Wrappzo, so nobody in it can sign in until it is switched back on. */
public class CompanyDeactivatedException extends RuntimeException {

    public CompanyDeactivatedException(String companyName) {
        super(companyName + "'s account is deactivated, so nobody in it can sign in. Contact Wrappzo support to switch it back on.");
    }
}
