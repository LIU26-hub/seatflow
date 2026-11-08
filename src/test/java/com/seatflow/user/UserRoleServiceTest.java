package com.seatflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.common.BusinessException;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleResolver roleResolver;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private UserRoleService userRoleService;

    @Test
    void rejectsCallersThatAreNotAdmins() {
        given(currentUserProvider.require())
                .willReturn(new CurrentUser(9L, "participant", UserRole.PARTICIPANT));

        assertThatThrownBy(() -> userRoleService.assignRole(2L, UserRole.ORGANIZER))
                .isInstanceOf(BusinessException.class)
                .hasMessage("only admin can assign roles");

        verifyNoInteractions(userRepository, roleResolver);
    }

    @Test
    void adminChangesRoleAndEvictsTheCache() {
        given(currentUserProvider.require())
                .willReturn(new CurrentUser(1L, "admin", UserRole.ADMIN));
        AppUser target = new AppUser();
        target.setId(2L);
        target.setUsername("candidate");
        target.setRole(UserRole.PARTICIPANT);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        var response = userRoleService.assignRole(2L, UserRole.ORGANIZER);

        assertThat(response.role()).isEqualTo(UserRole.ORGANIZER);
        assertThat(target.getRole()).isEqualTo(UserRole.ORGANIZER);
        verify(roleResolver).evict(2L);
    }
}
