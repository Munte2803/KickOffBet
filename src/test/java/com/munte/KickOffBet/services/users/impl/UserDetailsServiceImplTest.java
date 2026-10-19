package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadsUserByEmailWithRoleAuthority() {
        User user = new User();
        user.setEmail("admin@kickoffbet.test");
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail("admin@kickoffbet.test")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("admin@kickoffbet.test");

        assertThat(details).isSameAs(user);
        assertThat(details.getUsername()).isEqualTo("admin@kickoffbet.test");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        when(userRepository.findByEmail("ghost@kickoffbet.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@kickoffbet.test"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost@kickoffbet.test");
    }

    @Test
    void suspendedOrTemporarilyLockedUsersAreReportedAsLocked() {
        User suspended = new User();
        suspended.setStatus(UserStatus.SUSPENDED);
        User locked = new User();
        locked.setStatus(UserStatus.ACTIVE);
        locked.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        User deactivated = new User();
        deactivated.setStatus(UserStatus.DEACTIVATED);

        assertThat(suspended.isAccountNonLocked()).isFalse();
        assertThat(locked.isAccountNonLocked()).isFalse();
        assertThat(deactivated.isAccountNonLocked()).isFalse();
        assertThat(deactivated.isEnabled()).isFalse();
    }
}
