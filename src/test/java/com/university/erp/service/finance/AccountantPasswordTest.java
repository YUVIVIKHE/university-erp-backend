package com.university.erp.service.finance;
import com.university.erp.entity.User;
import com.university.erp.service.admin.AdminAuditService;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class AccountantPasswordTest {
 private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
 private final AdminAuditService audit=mock(AdminAuditService.class);
 private final PasswordEncoder passwords=mock(PasswordEncoder.class);
 private final AccountantSettingsService service=new AccountantSettingsService(jdbc,audit,passwords);
 private final User actor=User.builder().id(7L).email("accountant@example.invalid").tokenVersion(2).build();
 private void setup(){when(jdbc.queryForMap(anyString(),any(SqlParameterSource.class))).thenReturn(Map.of("password_hash","hash","token_version",2));}
 @Test void wrongCurrentPasswordDoesNotWrite(){setup();assertThrows(IllegalArgumentException.class,()->service.changePassword(actor,new AccountantSettingsService.PasswordChange("wrong","different-password")));verify(jdbc,never()).update(anyString(),any(SqlParameterSource.class));verifyNoInteractions(audit);}
 @Test void utf8ByteLimitIsEnforced(){assertThrows(IllegalArgumentException.class,()->service.changePassword(actor,new AccountantSettingsService.PasswordChange("current","界".repeat(25))));verifyNoInteractions(jdbc,audit,passwords);}
 @Test void successfulChangeRevokesTokensAndAuditsWithoutCredentials(){setup();when(passwords.matches("current","hash")).thenReturn(true);when(passwords.encode("different-password")).thenReturn("new-hash");service.changePassword(actor,new AccountantSettingsService.PasswordChange("current","different-password"));verify(jdbc).update(contains("token_version=token_version+1"),any(SqlParameterSource.class));verify(audit).record(actor,"FINANCE_PASSWORD_CHANGED","User",7L);}
}
