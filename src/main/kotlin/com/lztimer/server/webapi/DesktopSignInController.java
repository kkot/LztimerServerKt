package com.lztimer.server.webapi;

import com.lztimer.server.entity.User;
import com.lztimer.server.service.SocialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.connect.web.ProviderSignInController;
import org.springframework.social.connect.web.ProviderSignInUtils;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.social.support.URIBuilder;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * @author Krzysztof Kot (krzykot@gmail.com)
 */
@RequestMapping("/signin/desktop")
public class DesktopSignInController extends ProviderSignInController {
    private final Logger log = LoggerFactory.getLogger(DesktopSignInController.class);

    private final SocialService socialService;

    private final ProviderSignInUtils providerSignInUtils;

    private final SignInAdapter signInAdapter;

    public DesktopSignInController(ConnectionFactoryLocator connectionFactoryLocator,
                                   UsersConnectionRepository usersConnectionRepository,
                                   SignInAdapter signInAdapter, SocialService socialService,
                                   ProviderSignInUtils providerSignInUtils) {
        super(connectionFactoryLocator, usersConnectionRepository, signInAdapter);
        this.socialService = socialService;
        this.providerSignInUtils = providerSignInUtils;
        this.signInAdapter = signInAdapter;
    }

    @GetMapping("/new_user")
    public RedirectView signUp(NativeWebRequest webRequest,
                               @CookieValue(name = "NG_TRANSLATE_LANG_KEY", required = false, defaultValue = "\"en\"") String langKey,
                               RedirectAttributes attributes) {
        try {
            Connection<?> connection = providerSignInUtils.getConnectionFromSession(webRequest);
            User user = socialService.createSocialUser(connection, langKey.replace("\"", ""));
            signInAdapter.signIn(user.getLogin(), connection, webRequest);
            attributes.addAttribute("just_created", true);
            return new RedirectView("completed");
        } catch (Exception e) {
            log.error("Exception creating social user: ", e);
            return new RedirectView(URIBuilder.fromUri("/#/social-register/no-provider")
                    .queryParam("success", "false")
                    .build().toString(), true);
        }
    }

    @GetMapping("/completed")
    public ModelAndView completed(NativeWebRequest webRequest) {
        return new ModelAndView("/completed.html");
    }
}
