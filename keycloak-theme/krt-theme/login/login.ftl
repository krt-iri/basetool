<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div class="login-container">
            <img src="${url.resourcesPath}/img/krt.webp" alt="KRT" class="login-logo">
            <h1>PROFIT BASETOOL</h1>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                <div class="form-group">
                    <label for="username" class="krt-label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                    <input tabindex="1" id="username" class="krt-input" name="username" value="${(login.username!'')}"  type="text" autofocus autocomplete="off" />
                </div>

                <div class="form-group">
                    <label for="password" class="krt-label">${msg("password")}</label>
                    <input tabindex="2" id="password" class="krt-input" name="password" type="password" autocomplete="off" />
                </div>

                <#if realm.rememberMe>
                    <div class="form-group">
                        <label class="krt-checkbox-label">
                            <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" class="krt-checkbox" checked />
                            ${msg("rememberMe")}
                        </label>
                    </div>
                </#if>

                <#if realm.resetPasswordAllowed>
                    <div class="krt-form-footer">
                        <a tabindex="5" href="${url.loginResetCredentialsUrl!''}" class="krt-link">${msg("doForgotPassword")}</a>
                    </div>
                </#if>

                <div class="form-group login-action">
                    <input tabindex="4" class="krt-button" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                </div>
            </form>

            <#-- Social / IdP login buttons (e.g. Discord). Rendered on the Keycloak login page itself
                 so the entry point is reachable from EVERY login surface — the credential form, the
                 device-grant verification page used by the extractor, and direct logins — not only the
                 app sidebar shortcut. Keycloak populates `social.providers` only with IdPs that are
                 NOT hidden on the login page, so a `discord` IdP with "Hide on login page" = OFF shows
                 up here automatically. -->
            <#if realm.password && social.providers??>
                <div id="kc-social-providers" class="krt-social">
                    <div class="krt-social-divider"><span>${msg("identity-provider-login-label")}</span></div>
                    <#list social.providers as p>
                        <a id="social-${p.alias}" class="krt-button-social" href="${p.loginUrl}">${p.displayName!}</a>
                    </#list>
                </div>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
