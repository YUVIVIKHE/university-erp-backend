package com.university.erp.controller.finance;
import com.university.erp.entity.User;
import com.university.erp.service.finance.AccountantSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController @RequestMapping("/api/finance/settings") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class AccountantSettingsController {
 private final AccountantSettingsService service;
 @PostMapping("/password") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void password(@AuthenticationPrincipal User actor,@Valid @RequestBody AccountantSettingsService.PasswordChange input){service.changePassword(actor,input);}
 @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping public AccountantSettingsService.View read(@AuthenticationPrincipal User actor){return service.read(actor);}
 @PutMapping public AccountantSettingsService.View save(@AuthenticationPrincipal User actor,@Valid @RequestBody AccountantSettingsService.Input input){return service.save(actor,input);}
}
