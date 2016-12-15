package model;

import java.net.Socket;

public class User {
    private Integer id;
    private String name;
    private String password;
    private String address;
    private Socket socket;
    private Socket publicSocket;
    private Boolean loggedIn = false;

    public User(Integer id, String name, String password) {
        this.id = id;
        this.name = name;
        this.password = password;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(Boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public Socket getPublicSocket() {
        return publicSocket;
    }

    public void setPublicSocket(Socket publicSocket) {
        this.publicSocket = publicSocket;
    }

    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof User)) return false;
        if (!super.equals(object)) return false;

        User user = (User) object;

        if (!name.equals(user.name)) return false;
        if (!password.equals(user.password)) return false;
        if (!id.equals(user.id)) return false;

        return true;
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + password.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return name + " ("+((address != null)?address:"no address")+ ")";
    }
}