package com.airtribe.prism.service;

public interface RouteResolver {
    String resolve(String requestedModel, String prompt);
}
