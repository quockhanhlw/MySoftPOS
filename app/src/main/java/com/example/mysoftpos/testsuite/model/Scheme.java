package com.example.mysoftpos.testsuite.model;

import java.io.Serializable;
import java.util.UUID;

public class Scheme implements Serializable {
    private String id;
    private String name;
    private String prefix;
    private String iconLetter;
    private String color;
    private boolean builtIn;

    // Connection config
    private String serverIp;
    private int serverPort;
    private int timeout; // milliseconds

    public Scheme() {
        this.id = UUID.randomUUID().toString();
        this.serverIp = "";
        this.serverPort = 0;
        this.timeout = 30000;
    }

    public Scheme(String name, String prefix, String iconLetter, String color, boolean builtIn) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.prefix = prefix;
        this.iconLetter = iconLetter;
        this.color = color;
        this.builtIn = builtIn;
        this.serverIp = "";
        this.serverPort = 0;
        this.timeout = 30000;
    }

    // ── Identity ──
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getIconLetter() {
        return iconLetter;
    }

    public void setIconLetter(String iconLetter) {
        this.iconLetter = iconLetter;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    // ── Connection ──
    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /** Returns true if this scheme has a valid IP:port configured */
    public boolean hasConnectionConfig() {
        return serverIp != null && !serverIp.isEmpty() && serverPort > 0;
    }
}
