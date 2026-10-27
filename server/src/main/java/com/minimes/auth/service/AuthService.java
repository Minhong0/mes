package com.minimes.auth.service;

import com.minimes.auth.service.TokenService.IssuedToken;
import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.master.domain.Worker;
import com.minimes.master.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사번과 비밀번호로 로그인한다.
 * 없는 사번, 퇴사자, 비밀번호가 없거나 틀림을 모두 같은 오류로 돌려준다 (어느 사번이 있는지 알려 주지 않는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final WorkerRepository workerRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public LoginResult login(String workerId, String password) {
        Worker worker = workerRepository.findById(workerId)
                .filter(Worker::isActive)
                .filter(w -> w.getPasswordHash() != null && passwordEncoder.matches(password, w.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return new LoginResult(worker, tokenService.issue(worker));
    }

    public record LoginResult(Worker worker, IssuedToken token) {
    }
}
