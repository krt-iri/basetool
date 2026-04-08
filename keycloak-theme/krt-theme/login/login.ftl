<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div class="login-container">
            <h1>IRIDIUM BASETOOL</h1>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                <div class="form-group">
                    <label for="username" class="krt-label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                    <input tabindex="1" id="username" class="krt-input" name="username" value="${(login.username!'')}"  type="text" autofocus autocomplete="off" />
                </div>

                <div class="form-group">
                    <label for="password" class="krt-label">${msg("password")}</label>
                    <input tabindex="2" id="password" class="krt-input" name="password" type="password" autocomplete="off" />
                </div>

                <div class="form-group login-action">
                    <input tabindex="4" class="krt-button" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                </div>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
