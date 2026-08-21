package com.ssafy.snaphere.domain.notification.repository;

import com.ssafy.snaphere.domain.notification.entity.UserDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    List<UserDevice> findByUserIdAndPushEnabledTrue(Long userId);

    void deleteByUserId(Long userId);
}
