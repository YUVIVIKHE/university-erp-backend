package com.university.erp.service.finance;

import com.university.erp.entity.User;
import com.university.erp.exception.ConflictException;
import com.university.erp.service.admin.AdminAuditService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountantSettingsService {
 private final NamedParameterJdbcTemplate jdbc;
 private final AdminAuditService audit;
 private final org.springframework.security.crypto.password.PasswordEncoder passwords;
 public record PasswordChange(@NotBlank @Size(max=128) String currentPassword,@NotBlank @Size(min=12,max=72) String newPassword){}
 @Transactional public void changePassword(User actor,PasswordChange input){
  if(input.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("New password must be at most 72 UTF-8 bytes");
  var p=new MapSqlParameterSource("id",actor.getId());
  var row=jdbc.queryForMap("SELECT password_hash,token_version FROM users WHERE id=:id FOR UPDATE",p);
  if(!passwords.matches(input.currentPassword(),(String)row.get("password_hash")))throw new IllegalArgumentException("Current password is incorrect");
  if(passwords.matches(input.newPassword(),(String)row.get("password_hash")))throw new IllegalArgumentException("Choose a different new password");
  if(((Number)row.get("token_version")).intValue()!=actor.getTokenVersion())throw new ConflictException("Session changed. Sign in again before changing your password.");
  p.addValue("hash",passwords.encode(input.newPassword()));
  jdbc.update("UPDATE users SET password_hash=:hash,token_version=token_version+1,updated_at=now() WHERE id=:id",p);
  audit.record(actor,"FINANCE_PASSWORD_CHANGED","User",actor.getId());
 }
 @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
 public record Input(@NotNull @Pattern(regexp="MONTH|YEAR|FINANCIAL_YEAR") String reportPeriod,
  @NotNull @Pattern(regexp="collections|balances|ageing|defaulters|awards|refunds|budgets|payroll|vendors") String reportSection,@Min(0) long version){}
 public record Preferences(String reportPeriod,String reportSection,long version){}
 public record View(String fullName,String email,String role,String currency,String timeZone,LocalDate today,LocalDate reportFrom,Preferences preferences,List<String> paymentMethods){}
 @Transactional(readOnly=true) public View read(User actor){
  var rows=jdbc.queryForList("SELECT report_period,report_section,version FROM finance_user_preferences WHERE user_id=:id",Map.of("id",actor.getId()));
  Preferences pref=rows.isEmpty()?new Preferences("YEAR","collections",0):new Preferences((String)rows.get(0).get("report_period"),(String)rows.get(0).get("report_section"),((Number)rows.get(0).get("version")).longValue());
  LocalDate today=LocalDate.now(ZoneId.of(timeZone));
  return new View(actor.getFullName(),actor.getEmail(),actor.getRole().name(),"INR",timeZone,today,start(today,pref.reportPeriod()),pref,List.of("CASH","BANK_TRANSFER","UPI","CARD","CHEQUE"));
 }
 static LocalDate start(LocalDate today,String period){return switch(period){case "MONTH"->today.withDayOfMonth(1);case "YEAR"->today.withDayOfYear(1);case "FINANCIAL_YEAR"->LocalDate.of(today.getMonthValue()<4?today.getYear()-1:today.getYear(),4,1);default->throw new IllegalArgumentException("Unsupported report period");};}
 @Transactional public View save(User actor,Input input){
  var p=new MapSqlParameterSource("id",actor.getId()).addValue("period",input.reportPeriod()).addValue("section",input.reportSection()).addValue("version",input.version());
  int changed=input.version()==0?jdbc.update("INSERT INTO finance_user_preferences(user_id,report_period,report_section) VALUES(:id,:period,:section) ON CONFLICT DO NOTHING",p):jdbc.update("UPDATE finance_user_preferences SET report_period=:period,report_section=:section,version=version+1,updated_at=now() WHERE user_id=:id AND version=:version",p);
  if(changed==0)throw new ConflictException("Preferences changed in another session. Reload before saving.");
  audit.record(actor,"FINANCE_PREFERENCES_UPDATED","FinancePreferences",actor.getId());
  return read(actor);
 }
}
