package com.boycottpro.users.models;

public class UserForm {
    private String user_id;
    private String oldUsername;
    private String newUsername;

    public UserForm() {
    }

    public UserForm(String user_id, String oldUsername, String newUsername) {
        this.user_id = user_id;
        this.oldUsername = oldUsername;
        this.newUsername = newUsername;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getOldUsername() {
        return oldUsername;
    }

    public void setOldUsername(String oldUsername) {
        this.oldUsername = oldUsername;
    }

    public String getNewUsername() {
        return newUsername;
    }

    public void setNewUsername(String newUsername) {
        this.newUsername = newUsername;
    }
}
