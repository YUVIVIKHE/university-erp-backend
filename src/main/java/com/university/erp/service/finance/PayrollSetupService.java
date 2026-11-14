package com.university.erp.service.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.exception.ConflictException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PayrollSetupService {
 private final NamedParameterJdbcTemplate jdbc;
 private final ObjectMapper mapper;
 private final DisbursementService disbursements;
 @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
 public record Profile(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._/-]{0,29}") String employeeCode,Long departmentId,
  @NotBlank @Size(max=100) String designation,@NotNull LocalDate joinedOn,@NotNull Boolean active,@Min(0) long version){}
 public record Salary(@NotNull UUID requestId,@NotNull @Positive Long userId,@NotNull LocalDate effectiveFrom,
  @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal basic,
  @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal allowances,
  @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal deductions,@NotBlank @Size(max=1000) String notes){}
 public record Generate(@NotNull UUID requestId,@NotNull UUID salaryId,@NotNull LocalDate month,
  @NotBlank @Size(max=100) String reference,@NotNull LocalDate date,@NotBlank @Size(max=2000) String notes){}
 static BigDecimal net(Salary s){
  if(s.effectiveFrom().getDayOfMonth()!=1)throw new IllegalArgumentException("Salary changes must start at the beginning of a month");
  BigDecimal gross=s.basic().add(s.allowances()),net=gross.subtract(s.deductions());
  if(gross.compareTo(new BigDecimal("9999999999.99"))>0||net.signum()<=0)throw new IllegalArgumentException("Gross must fit the supported amount and net pay must be positive");return net;
 }
 private LocalDate today(){return LocalDate.now(ZoneId.of(timeZone));}
 private void staffLock(long user){jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key","payroll-salary-"+user));}
 private void eligible(long user){
  if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=:id AND is_active=true AND invitation_pending=false AND role IN('ADMIN','ACCOUNTANT','FACULTY'))",Map.of("id",user),Boolean.class)))throw new IllegalArgumentException("Choose an active staff user");
 }
 private static final String PROFILE="SELECT p.*,u.full_name,u.email,d.name AS department_name FROM finance_payroll_profiles p JOIN users u ON u.id=p.user_id LEFT JOIN programs d ON d.id=p.department_id ";
 @Transactional(readOnly=true) public Map<String,Object> profile(long user){var rows=jdbc.queryForList(PROFILE+" WHERE p.user_id=:id",Map.of("id",user));if(rows.isEmpty())throw new NoSuchElementException("Payroll profile not set up");return rows.get(0);}
 @Transactional(readOnly=true) public Page<Map<String,Object>> departments(String search,int page){
  var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("offset",page*20L);String where=" FROM programs WHERE status='ACTIVE' AND name ILIKE :search";
  long count=jdbc.queryForObject("SELECT count(*)"+where,p,Long.class);return new PageImpl<>(jdbc.queryForList("SELECT id,code,name"+where+" ORDER BY name,id LIMIT 20 OFFSET :offset",p),PageRequest.of(page,20),count);
 }
 @Transactional public Map<String,Object> save(long user,Profile input,String actor){
  staffLock(user);eligible(user);if(input.joinedOn().isAfter(today()))throw new IllegalArgumentException("Joining date cannot be in the future");
  if(input.departmentId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM programs WHERE id=:id AND status='ACTIVE')",Map.of("id",input.departmentId()),Boolean.class)))throw new IllegalArgumentException("Choose an active shared department");
  var p=new MapSqlParameterSource("user",user).addValue("code",input.employeeCode().trim().toUpperCase(Locale.ROOT)).addValue("department",input.departmentId(),java.sql.Types.BIGINT)
   .addValue("designation",input.designation().trim()).addValue("joined",java.sql.Date.valueOf(input.joinedOn())).addValue("active",input.active()).addValue("version",input.version()).addValue("actor",actor);
  int changed=input.version()==0?jdbc.update("INSERT INTO finance_payroll_profiles(user_id,employee_code,department_id,designation,joined_on,active,version,updated_by) VALUES(:user,:code,:department,:designation,:joined,:active,1,:actor) ON CONFLICT(user_id) DO NOTHING",p):jdbc.update("UPDATE finance_payroll_profiles SET employee_code=:code,department_id=:department,designation=:designation,joined_on=:joined,active=:active,version=version+1,updated_by=:actor,updated_at=now() WHERE user_id=:user AND version=:version",p);
  if(changed==0)throw new ConflictException("Profile changed. Reload before saving.");return profile(user);
 }
 private Map<String,Object> salaryRow(Map<String,Object> row){var r=new LinkedHashMap<>(row);r.remove("request_hash");return r;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page<Map<String,Object>> salaries(Long user,String status,int page){
  if(!Set.of("","PENDING","APPROVED","REJECTED","CANCELLED").contains(status))throw new IllegalArgumentException("Invalid salary status");
  var p=new MapSqlParameterSource("status",status).addValue("offset",page*20L);String where=" WHERE (:status='' OR s.status=:status)";
  if(user!=null){p.addValue("user",user);where+=" AND s.user_id=:user";}
  String from=" FROM finance_salary_structures s JOIN users u ON u.id=s.user_id";long count=jdbc.queryForObject("SELECT count(*)"+from+where,p,Long.class);
  return new PageImpl<>(jdbc.queryForList("SELECT s.*,u.full_name,u.email"+from+where+" ORDER BY s.effective_from DESC,s.id LIMIT 20 OFFSET :offset",p).stream().map(this::salaryRow).toList(),PageRequest.of(page,20),count);
 }
 @Transactional public Map<String,Object> salary(Salary input,String actor){
  BigDecimal net=net(input);String fingerprint;try{fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(input.userId(),input.effectiveFrom(),input.basic().stripTrailingZeros().toPlainString(),input.allowances().stripTrailingZeros().toPlainString(),input.deductions().stripTrailingZeros().toPlainString(),input.notes().trim()))));}catch(Exception e){throw new IllegalArgumentException("Invalid salary structure");}
  var p=new MapSqlParameterSource("request",input.requestId()).addValue("id",UUID.randomUUID()).addValue("user",input.userId()).addValue("effective",java.sql.Date.valueOf(input.effectiveFrom())).addValue("basic",input.basic()).addValue("allowances",input.allowances()).addValue("deductions",input.deductions()).addValue("net",net).addValue("notes",input.notes().trim()).addValue("actor",actor).addValue("hash",fingerprint);
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",p);
  var old=jdbc.queryForList("SELECT * FROM finance_salary_structures WHERE request_id=:request",p);
  if(!old.isEmpty()){if(!fingerprint.equals(old.get(0).get("request_hash"))||!actor.equals(old.get(0).get("created_by")))throw new ConflictException("Salary request ID already used");return salaryRow(old.get(0));}
  staffLock(input.userId());eligible(input.userId());var profile=profile(input.userId());if(!Boolean.TRUE.equals(profile.get("active")))throw new ConflictException("Payroll profile is paused");
  if(input.effectiveFrom().isBefore(((java.sql.Date)profile.get("joined_on")).toLocalDate().withDayOfMonth(1)))throw new IllegalArgumentException("Salary period cannot precede joining month");
  var created=jdbc.queryForList("INSERT INTO finance_salary_structures(id,request_id,request_hash,user_id,effective_from,basic,allowances,deductions,net,notes,created_by) VALUES(:id,:request,:hash,:user,:effective,:basic,:allowances,:deductions,:net,:notes,:actor) ON CONFLICT DO NOTHING RETURNING *",p);
  if(created.isEmpty())throw new ConflictException("An active salary structure already exists for this effective month");return salaryRow(created.get(0));
 }
 @Transactional public Map<String,Object> review(UUID id,OperatingFinanceService.Review input,String actor){
  var rows=jdbc.queryForList("SELECT user_id FROM finance_salary_structures WHERE id=:id",Map.of("id",id));if(rows.isEmpty())throw new NoSuchElementException("Salary structure not found");long user=((Number)rows.get(0).get("user_id")).longValue();staffLock(user);
  var p=new MapSqlParameterSource("id",id).addValue("user",user).addValue("actor",actor).addValue("status",input.status()).addValue("note",input.note().trim());var row=jdbc.queryForMap("SELECT * FROM finance_salary_structures WHERE id=:id FOR UPDATE",p);
  if(input.status().equals(row.get("status"))&&actor.equals(row.get("reviewed_by"))&&input.note().trim().equals(row.get("review_note")))return salaryRow(row);
  OperatingFinanceService.validateReview((String)row.get("status"),(String)row.get("created_by"),actor,input);
  if(!input.status().equals("CANCELLED")&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=:user AND email=:actor)",p,Boolean.class)))throw new ConflictException("Staff cannot review their own salary");
  if(input.status().equals("APPROVED")){
   p.addValue("effective",row.get("effective_from"));long later=jdbc.queryForObject("SELECT count(*) FROM finance_salary_structures WHERE user_id=:user AND status='APPROVED' AND effective_from>=:effective",p,Long.class);
   if(later>0)throw new ConflictException("Approve salary versions in chronological order; existing approved periods cannot be overwritten");
  }
  return salaryRow(jdbc.queryForMap("UPDATE finance_salary_structures SET status=:status,reviewed_by=:actor,reviewed_at=now(),review_note=:note WHERE id=:id RETURNING *",p));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Map<String,Object> preview(long user,LocalDate month){return buildPreview(user,month);}
 private Map<String,Object> buildPreview(long user,LocalDate month){
  DisbursementService.month(month,today());eligible(user);var p=profile(user);
  if(!Boolean.TRUE.equals(p.get("active")))throw new ConflictException("Payroll profile is paused");
  if(month.isBefore(((java.sql.Date)p.get("joined_on")).toLocalDate()))throw new ConflictException("Joining/partial employment month needs an HR-approved manual payout");
  var rows=jdbc.queryForList("SELECT * FROM finance_salary_structures WHERE user_id=:user AND status='APPROVED' AND effective_from<=:month ORDER BY effective_from DESC LIMIT 1",Map.of("user",user,"month",java.sql.Date.valueOf(month)));
  if(rows.isEmpty())throw new NoSuchElementException("No approved salary structure covers this month");var s=salaryRow(rows.get(0));s.put("gross",((BigDecimal)s.get("basic")).add((BigDecimal)s.get("allowances")));s.put("profile",p);s.put("month",month);return s;
 }
 @Transactional public Map<String,Object> generate(long user,Generate input,String actor){
  // Same request lock/order as the existing payroll mutation. Snapshots are immutable.
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",Map.of("request",input.requestId()));staffLock(user);
  var previous=jdbc.queryForList("SELECT e.id,e.staff_user_id,e.payroll_month,e.document_date,e.description,e.document_reference,s.salary_id FROM finance_operating_entries e LEFT JOIN finance_payroll_snapshots s ON s.entry_id=e.id WHERE e.request_id=:request",Map.of("request",input.requestId()));
  if(!previous.isEmpty()){
   var old=previous.get(0);if(old.get("salary_id")==null)throw new ConflictException("Request ID belongs to a manual payout");String ref=input.month().toString().substring(0,7)+" / U"+user+" / "+input.reference().trim();
   if(((Number)old.get("staff_user_id")).longValue()!=user||!input.salaryId().equals(old.get("salary_id"))||!java.sql.Date.valueOf(input.month()).equals(old.get("payroll_month"))||!java.sql.Date.valueOf(input.date()).equals(old.get("document_date"))||!input.notes().trim().equals(old.get("description"))||!ref.equals(old.get("document_reference")))throw new ConflictException("Generated payroll request ID already used");
   return disbursements.payroll(new DisbursementService.Payroll(input.requestId(),user,input.month(),input.reference(),input.date(),(BigDecimal)snapshot((UUID)old.get("id")).get("net"),input.notes()),actor);
  }
  var s=buildPreview(user,input.month());if(!input.salaryId().equals(s.get("id")))throw new ConflictException("Salary version changed. Reload preview before generating.");
  var entry=disbursements.payroll(new DisbursementService.Payroll(input.requestId(),user,input.month(),input.reference(),input.date(),(BigDecimal)s.get("net"),input.notes()),actor);
  @SuppressWarnings("unchecked") var p=(Map<String,Object>)s.get("profile");
  var args=new MapSqlParameterSource("entry",entry.get("id")).addValue("salary",s.get("id")).addValue("code",p.get("employee_code")).addValue("designation",p.get("designation")).addValue("department",p.get("department_name"),java.sql.Types.VARCHAR)
   .addValue("basic",s.get("basic")).addValue("allowances",s.get("allowances")).addValue("deductions",s.get("deductions")).addValue("gross",s.get("gross")).addValue("net",s.get("net"));
  jdbc.update("INSERT INTO finance_payroll_snapshots(entry_id,salary_id,employee_code,designation,department_name,basic,allowances,deductions,gross,net) VALUES(:entry,:salary,:code,:designation,:department,:basic,:allowances,:deductions,:gross,:net)",args);return entry;
 }
 @Transactional(readOnly=true) public Map<String,Object> snapshot(UUID entry){var rows=jdbc.queryForList("SELECT * FROM finance_payroll_snapshots WHERE entry_id=:id",Map.of("id",entry));if(rows.isEmpty())throw new NoSuchElementException("Manual net payout has no salary breakdown snapshot");return rows.get(0);}
}
