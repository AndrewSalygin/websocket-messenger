package com.andrewsalygin.model;

import java.util.Objects;

public class UserChat {
    private String master;
    private String slave;

    public UserChat(String master, String slave) {
        if (master.compareTo(slave) < 0) {
            this.master = master;
            this.slave = slave;
        } else {
            this.master = slave;
            this.slave = master;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserChat userChat)) return false;
        return Objects.equals(master, userChat.master) && Objects.equals(slave, userChat.slave);
    }

    @Override
    public int hashCode() {
        return Objects.hash(master, slave);
    }
}
