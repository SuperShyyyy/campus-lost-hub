package com.hub.security;

import java.security.Principal;

public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(long userId) {
        this.name = String.valueOf(userId);
    }

    @Override
    public String getName() {
        return name;
    }
}
