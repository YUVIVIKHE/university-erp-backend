package com.university.erp.repository.admin;
import com.university.erp.entity.admin.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailSettingsRepository extends JpaRepository<EmailSettings, Long> {
    java.util.Optional<EmailSettings> findFirstByEnabledTrueOrderByIdDesc();
    java.util.Optional<EmailSettings> findFirstByOrderByIdDesc();
    java.util.List<EmailSettings> findAllByOrderByIdDesc();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from EmailSettings s order by s.id")
    java.util.List<EmailSettings> lockProfiles();
}
