package com.artagram.canvas.party;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.party")
public class PartyProperties {

    private int tickIntervalMs = 500;
    private int eraserAppearsEverySeconds = 30;
    private int eraserHoldSeconds = 10;
    private int freeMaxSalasActivas = 1;

    public int getTickIntervalMs() {
        return tickIntervalMs;
    }

    public void setTickIntervalMs(int tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public int getEraserAppearsEverySeconds() {
        return eraserAppearsEverySeconds;
    }

    public void setEraserAppearsEverySeconds(int eraserAppearsEverySeconds) {
        this.eraserAppearsEverySeconds = eraserAppearsEverySeconds;
    }

    public int getEraserHoldSeconds() {
        return eraserHoldSeconds;
    }

    public void setEraserHoldSeconds(int eraserHoldSeconds) {
        this.eraserHoldSeconds = eraserHoldSeconds;
    }

    public int getFreeMaxSalasActivas() {
        return freeMaxSalasActivas;
    }

    public void setFreeMaxSalasActivas(int freeMaxSalasActivas) {
        this.freeMaxSalasActivas = freeMaxSalasActivas;
    }
}
