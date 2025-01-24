package org.example;

import java.time.LocalDateTime;

public class Player {
    private final LocalDateTime loginDate;
    private final String nickname;
    private final String ip;

    public Player(String nickname, String ip) {
        this.loginDate = LocalDateTime.now();
        this.nickname = nickname;
        this.ip = ip;
    }

    // Getters
    public LocalDateTime getLoginDate() {
        return loginDate;
    }

    public String getNickname() {
        return nickname;
    }

    public String getIp() {
        return ip;
    }

    @Override
    public String toString() {
        return "Player{" +
                "loginDate=" + loginDate +
                ", nickname='" + nickname + '\'' +
                ", ip='" + ip + '\'' +
                '}';
    }
}