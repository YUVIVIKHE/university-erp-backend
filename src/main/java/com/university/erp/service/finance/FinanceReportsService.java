package com.university.erp.service.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class FinanceReportsService {
    private final NamedParameterJdbcTemplate jdbc;
    public record Report(LocalDate from,LocalDate until,Map<String,Object> totals,List<Map<String,Object>> monthly,List<Map<String,Object>> sources,List<Map<String,Object>> categories,Map<String,Object> previousPeriod,Map<String,Object> previousYear){}
    static void validate(LocalDate from,LocalDate until){
        GeneralLedgerService.dates(from,until);
        if(from==null||until==null||until.isAfter(from.plusYears(5).minusDays(1)))
            throw new IllegalArgumentException("Choose a report period of no more than five years");
    }
    public Report report(LocalDate from,LocalDate until){
        validate(from,until);
        var p=Map.of("from",java.sql.Date.valueOf(from),"until",java.sql.Date.valueOf(until));
        String period=" FROM finance_ledger_entries WHERE entry_date BETWEEN :from AND :until";
        String inflow="CASE WHEN debit_account IN ('1000','1010') THEN amount ELSE 0 END";
        String outflow="CASE WHEN credit_account IN ('1000','1010') THEN amount ELSE 0 END";
        String sums="count(*) AS transactions,coalesce(sum("+inflow+"),0) AS inflow,coalesce(sum("+outflow+"),0) AS outflow,coalesce(sum(("+inflow+")-("+outflow+")),0) AS net";
        var totals=jdbc.queryForMap("SELECT "+sums+period,p);
        var monthly=jdbc.queryForList("SELECT to_char(entry_date,'YYYY-MM') AS month,"+sums+period+" GROUP BY to_char(entry_date,'YYYY-MM') ORDER BY month",p);
        var sources=jdbc.queryForList("SELECT source_type AS source,"+sums+period+" GROUP BY source_type ORDER BY source_type",p);
        var categories=jdbc.queryForList("SELECT a.code,a.name,a.kind,coalesce(sum(CASE WHEN a.kind='INCOME' THEN l.credit-l.debit ELSE l.debit-l.credit END),0) AS amount FROM finance_ledger_accounts a LEFT JOIN finance_ledger_lines l ON l.account_code=a.code AND l.entry_date BETWEEN :from AND :until WHERE a.kind IN ('INCOME','EXPENSE') GROUP BY a.code,a.name,a.kind ORDER BY a.kind,a.code",p);
        long days=java.time.temporal.ChronoUnit.DAYS.between(from,until)+1;
        var previous=Map.of("from",java.sql.Date.valueOf(from.minusDays(days)),"until",java.sql.Date.valueOf(from.minusDays(1)));
        var year=Map.of("from",java.sql.Date.valueOf(from.minusYears(1)),"until",java.sql.Date.valueOf(until.minusYears(1)));
        var previousTotals=new LinkedHashMap<String,Object>(jdbc.queryForMap("SELECT "+sums+period,previous));previousTotals.putAll(previous);
        var yearTotals=new LinkedHashMap<String,Object>(jdbc.queryForMap("SELECT "+sums+period,year));yearTotals.putAll(year);
        return new Report(from,until,totals,monthly,sources,categories,previousTotals,yearTotals);
    }
}
