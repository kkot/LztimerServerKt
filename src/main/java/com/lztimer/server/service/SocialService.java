package com.lztimer.server.service;

import com.lztimer.server.entity.Authority;
import com.lztimer.server.entity.User;
import com.lztimer.server.repository.AuthorityRepository;
import com.lztimer.server.repository.UserRepository;
import com.lztimer.server.security.Authorities;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionRepository;
import org.springframework.social.connect.UserProfile;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class SocialService {

    private final Logger log = LoggerFactory.getLogger(SocialService.class);

    private final UsersConnectionRepository usersConnectionRepository;

    private final AuthorityRepository authorityRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    public SocialService(UsersConnectionRepository usersConnectionRepository, AuthorityRepository authorityRepository,
                         PasswordEncoder passwordEncoder, UserRepository userRepository) {

        this.usersConnectionRepository = usersConnectionRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    public void deleteUserSocialConnection(String login) {
        ConnectionRepository connectionRepository = usersConnectionRepository.createConnectionRepository(login);
        connectionRepository.findAllConnections().keySet().stream()
                .forEach(providerId -> {
                    connectionRepository.removeConnections(providerId);
                    log.debug("Delete user social connection providerId: {}", providerId);
                });
    }

    public User createSocialUser(Connection<?> connection, String langKey) {
        if (connection == null) {
            log.error("Cannot create social user because connection is null");
            throw new IllegalArgumentException("Connection cannot be null");
        }
        UserProfile userProfile = connection.fetchUserProfile();
        String providerId = connection.getKey().getProviderId();
        String imageUrl = connection.getImageUrl();
        User user = createUserIfNotExist(userProfile, langKey, providerId, imageUrl);
        createSocialConnection(user.getLogin(), connection);
        return user;
    }

    private User createUserIfNotExist(UserProfile userProfile, String langKey, String providerId, String imageUrl) {
        String email = userProfile.getEmail();
        String userName = userProfile.getUsername();
        if (!StringUtils.isBlank(userName)) {
            userName = userName.toLowerCase(Locale.ENGLISH);
        }
        if (StringUtils.isBlank(email) && StringUtils.isBlank(userName)) {
            log.error("Cannot create social user because email and login are null");
            throw new IllegalArgumentException("Email and login cannot be null");
        }
        if (StringUtils.isBlank(email) && userRepository.findOneByLogin(userName).isPresent()) {
            log.error("Cannot create social user because email is null and login already exist, login -> {}", userName);
            throw new IllegalArgumentException("Email cannot be null with an existing login");
        }
        if (!StringUtils.isBlank(email)) {
            Optional<User> user = userRepository.findOneByEmail(email);
            if (user.isPresent()) {
                log.info("User already exist associate the connection to this account");
                return user.get();
            }
        }

        String login = getLoginDependingOnProviderId(userProfile, providerId);
        String encryptedPassword = passwordEncoder.encode(RandomStringUtils.random(10));
        Set<Authority> authorities = getAuthorities();

        User newUser = new User();
        newUser.setLogin(login);
        newUser.setFirstName(userProfile.getFirstName());
        newUser.setLastName(userProfile.getLastName());
        newUser.setEmail(email);
        newUser.setAuthorities(authorities);
        newUser.setLangKey(langKey);
        newUser.setImageUrl(imageUrl);

        return userRepository.save(newUser);
    }

    private Set<Authority> getAuthorities() {
        Set<Authority> authorities = new HashSet<>(1);
        Authority roleUser = authorityRepository.findOne(Authorities.USER.getName());
        if (roleUser == null) {
            roleUser = authorityRepository.save(new Authority(Authorities.USER.getName()));
        }
        authorities.add(roleUser);
        return authorities;
    }

    /**
     * @return login if provider manage a login like Twitter or GitHub otherwise email address.
     * Because provider like Google or Facebook didn't provide login or login like "12099388847393"
     */
    private String getLoginDependingOnProviderId(UserProfile userProfile, String providerId) {
        switch (providerId) {
            case "twitter":
                return userProfile.getUsername().toLowerCase();
            default:
                return userProfile.getEmail();
        }
    }

    private void createSocialConnection(String login, Connection<?> connection) {
        ConnectionRepository connectionRepository = usersConnectionRepository.createConnectionRepository(login);
        connectionRepository.addConnection(connection);
    }
}
