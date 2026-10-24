package com.artagram.canvas.community;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.quotas")
public class ComunidadProperties {

    private int freeMaxCommunities = 3;

    public int getFreeMaxCommunities() {
        return freeMaxCommunities;
    }

    public void setFreeMaxCommunities(int freeMaxCommunities) {
        this.freeMaxCommunities = freeMaxCommunities;
    }
}
