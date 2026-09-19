package io.bidvelocity.auth.oauth;

import io.bidvelocity.auth.dto.Dtos.AuthResponse;
import io.bidvelocity.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Completes the Google server-side OAuth flow (Spring Security handles the
 * /oauth2/authorization/google initiation and /login/oauth2/code/google
 * callback + token exchange + ID-token verification). On success: find/create
 * the user, assign USER role, mint the application JWT and hand it to React
 * via a URL fragment (fragments never hit server logs or Referer headers).
 * The Google client secret never leaves this service's environment.
 */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService auth;
    private final String frontendUrl;

    public GoogleLoginSuccessHandler(AuthService auth, @Value("${bidvelocity.frontend-url}") String frontendUrl) {
        this.auth = auth;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication authentication) throws IOException {
        if (authentication.getPrincipal() instanceof OidcUser oidc) {
            String email = oidc.getEmail();
            Boolean verified = oidc.getClaimAsString("email_verified") == null ? null
                    : Boolean.valueOf(oidc.getClaimAsString("email_verified"));
            if (email == null || Boolean.FALSE.equals(verified)) {
                res.sendRedirect(frontendUrl + "/#/auth/callback#error=google_email_unverified");
                return;
            }
            AuthResponse r = auth.issueFor(auth.upsertGoogleUser(
                    email, oidc.getGivenName(), oidc.getFamilyName(), oidc.getSubject()));
            res.sendRedirect(frontendUrl + "/#/auth/callback#token=" + enc(r.accessToken()) + "&refresh=" + enc(r.refreshToken()));
        } else {
            res.sendRedirect(frontendUrl + "/#/auth/callback#error=unexpected_principal");
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
