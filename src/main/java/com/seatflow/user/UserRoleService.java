package com.seatflow.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.seatflow.common.BusinessException;
import com.seatflow.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

/**
 * Role assignment, restricted to administrators.
 *
 * <p>中文批注：角色更新与缓存清理的先后顺序很关键。必须等事务提交之后再删缓存，
 * 否则并发请求可能在提交前读到旧角色并把它重新写回缓存，结果新角色反而被缓存覆盖。
 * 这里用 afterCommit 回调保证"新值已经可见"之后才删键。
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRepository userRepository;
    private final UserRoleResolver roleResolver;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public UserResponse assignRole(Long targetUserId, UserRole role) {
        CurrentUser actor = currentUserProvider.require();
        if (!actor.isAdmin()) {
            throw new BusinessException(403, "only admin can assign roles");
        }
        AppUser target = userRepository.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("user not found: " + targetUserId));
        if (target.getRole() == role) {
            return UserResponse.from(target);
        }

        target.setRole(role);
        evictRoleAfterCommit(targetUserId);
        return UserResponse.from(target);
    }

    private void evictRoleAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    roleResolver.evict(userId);
                }
            });
        } else {
            roleResolver.evict(userId);
        }
    }
}
