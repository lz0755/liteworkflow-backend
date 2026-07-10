package com.liteworkflow.identity.repository;

import com.liteworkflow.identity.domain.LoginLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLogRepository extends JpaRepository<LoginLog, UUID> {
}
