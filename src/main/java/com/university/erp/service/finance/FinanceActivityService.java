package com.university.erp.service.finance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class FinanceActivityService {
 private final NamedParameterJdbcTemplate jdbc;
 @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
 public Page<Map<String,Object>> list(LocalDate from,LocalDate until,String search,String kind,String outcome,int page){
  FinanceReportsService.validate(from,until);
  if(page<0||page>100000||search.length()>100||!Set.of("","FINANCE","ACCESS","SETTINGS").contains(kind)||!Set.of("","SUCCESS","ERROR").contains(outcome))throw new IllegalArgumentException("Invalid activity filters");
  var zone=ZoneId.of(timeZone);
  var p=new MapSqlParameterSource("from",from.atStartOfDay(zone).toOffsetDateTime()).addValue("until",until.plusDays(1).atStartOfDay(zone).toOffsetDateTime()).addValue("search","%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%").addValue("kind",kind).addValue("outcome",outcome).addValue("offset",page*20L);
  String events="""
   SELECT 'F-'||id AS id,'FINANCE'::text AS kind,actor,method||' '||path AS action,http_status,
     CASE WHEN http_status<400 THEN 'SUCCESS' ELSE 'ERROR' END AS outcome,created_at
   FROM finance_activity_events
   UNION ALL
   SELECT 'A-'||a.id,CASE WHEN a.action='USER_LOGIN' THEN 'ACCESS' ELSE 'SETTINGS' END,
     coalesce(u.email,'Deleted account'),a.action,NULL::integer,'SUCCESS',a.created_at
   FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id
   WHERE (a.action='USER_LOGIN' AND u.role='ACCOUNTANT') OR a.action IN('FINANCE_PREFERENCES_UPDATED','FINANCE_PASSWORD_CHANGED')
   """;
  String where=" WHERE created_at>=:from AND created_at<:until AND (:kind='' OR kind=:kind) AND (:outcome='' OR outcome=:outcome) AND (actor ILIKE :search ESCAPE '!' OR action ILIKE :search ESCAPE '!')";
  long count=jdbc.queryForObject("SELECT count(*) FROM ("+events+") e"+where,p,Long.class);
  var rows=jdbc.queryForList("SELECT * FROM ("+events+") e"+where+" ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET :offset",p);
  return new PageImpl<>(rows,PageRequest.of(page,20),count);
 }
}
