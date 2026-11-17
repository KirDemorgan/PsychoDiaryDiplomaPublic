package alex.drankov.diary.exports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.exports.dto.DeleteAccountRequest;
import alex.drankov.diary.users.repository.UserRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private final UserAccess access;
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Transactional
    public void delete(
            UUID user,
            DeleteAccountRequest request
    ) {
        var owner = access.lock(user);
        if (!Boolean.TRUE.equals(request.confirm()) || request.currentPassword() == null
                || request.currentPassword().isBlank() || request.currentPassword().length() > 1024) {
            throw ApiException.invalid("Подтвердите удаление и передайте текущий пароль");
        }
        if (!passwords.matches(request.currentPassword(), owner.getPasswordHash())) {
            throw new ApiException(403, "PASSWORD_INVALID", "Не удалось подтвердить пароль");
        }
        users.delete(owner);
        users.flush();
    }
}
