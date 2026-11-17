package alex.drankov.diary.users.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.repository.UserRepository;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccess {
    private final UserRepository users;

    /**
     * Locks the owner row to serialize mutations belonging to one user.
     *
     * <p>The caller must have an active transaction and acquire this lock before any child-row locks.
     *
     * @param userId the identifier of the resource owner
     * @return the locked user
     * @throws ApiException if the user does not exist
     */
    public User lock(UUID userId) {
        return users.lockById(userId).orElseThrow(ApiException::notFound);
    }

    public User read(UUID userId) {
        return users.findById(userId).orElseThrow(ApiException::notFound);
    }
}
