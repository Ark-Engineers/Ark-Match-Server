package io.arknights.dateorfriends.tools.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin.bootstrap")
public class AdminBootstrapProperties {

    private String account;
    private String password;
    private String email;
    private String nickname = "管理员";

    private String superAccount;
    private String superPassword;
    private String superEmail;
    private String superNickname = "超级管理员";

    private String adminAccount;
    private String adminPassword;
    private String adminEmail;
    private String adminNickname = "管理员";

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getSuperAccount() {
        return superAccount == null || superAccount.isBlank() ? account : superAccount;
    }

    public void setSuperAccount(String superAccount) {
        this.superAccount = superAccount;
    }

    public String getSuperPassword() {
        return superPassword == null || superPassword.isBlank() ? password : superPassword;
    }

    public void setSuperPassword(String superPassword) {
        this.superPassword = superPassword;
    }

    public String getSuperEmail() {
        return superEmail == null || superEmail.isBlank() ? email : superEmail;
    }

    public void setSuperEmail(String superEmail) {
        this.superEmail = superEmail;
    }

    public String getSuperNickname() {
        return superNickname == null || superNickname.isBlank() ? nickname : superNickname;
    }

    public void setSuperNickname(String superNickname) {
        this.superNickname = superNickname;
    }

    public String getAdminAccount() {
        return adminAccount;
    }

    public void setAdminAccount(String adminAccount) {
        this.adminAccount = adminAccount;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminNickname() {
        return adminNickname;
    }

    public void setAdminNickname(String adminNickname) {
        this.adminNickname = adminNickname;
    }
}

