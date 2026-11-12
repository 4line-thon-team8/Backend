package com.fivegrade.backend.auth.model;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;

import java.util.Objects;

/** 요청 인증에서 확인된 사용자. 본문의 userId로 대체하지 않는다. */
public record AuthenticatedUser(Long id) {

    public void requireOwner(Long ownerId) {
        if (!Objects.equals(id, ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
