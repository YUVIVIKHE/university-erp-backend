package com.university.erp.service.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class GeneralLedgerService {
    private final NamedParameterJdbcTemplate jdbc;
    public record Trial(List<Map<String,Object>> accounts,BigDecimal debits,BigDecimal credits,BigDecimal difference){}
    public record Statement(Map<String,Object> account,BigDecimal opening,BigDecimal debits,BigDecimal credits,BigDecimal closing,Page<Map<String,Object>> movements){}
    static void dates(LocalDate from,LocalDate until){if(from!=null&&until!=null&&until.isBefore(from))throw new IllegalArgumentException("End date must be on or after start date");}
    public List<Map<String,Object>> accounts(){return jdbc.queryForList("SELECT code,name,kind FROM finance_ledger_accounts ORDER BY code",Map.of());}
    private MapSqlParameterSource parameters(LocalDate from,LocalDate until){
        dates(from,until);var p=new MapSqlParameterSource();
        if(from!=null)p.addValue("from",java.sql.Date.valueOf(from));if(until!=null)p.addValue("until",java.sql.Date.valueOf(until));return p;
    }
    private String period(LocalDate from,LocalDate until){return (from==null?"":" AND entry_date>=:from")+(until==null?"":" AND entry_date<=:until");}
    public Page<Map<String,Object>> journals(String search,String source,LocalDate from,LocalDate until,int page){
        if(!Set.of("","RECEIPT","REFUND","OPERATING","VENDOR","PAYROLL").contains(source))throw new IllegalArgumentException("Invalid ledger source");
        var p=parameters(from,until).addValue("search","%"+search.trim()+"%").addValue("source",source).addValue("offset",page*20L);
        String where=" WHERE (:source='' OR source_type=:source) AND (reference ILIKE :search OR counterparty ILIKE :search OR entry_id ILIKE :search) "+period(from,until);
        long count=jdbc.queryForObject("SELECT count(*) FROM finance_ledger_entries"+where,p,Long.class);
        return new PageImpl<>(jdbc.queryForList("SELECT * FROM finance_ledger_entries"+where+" ORDER BY entry_date DESC,entry_id DESC LIMIT 20 OFFSET :offset",p),PageRequest.of(page,20),count);
    }
    public Trial trial(LocalDate from,LocalDate until){
        var p=parameters(from,until);
        // All accounts, including zero balances. Period movements and closing balances remain distinct.
        String opening=from==null?"0::numeric":"coalesce(sum(debit-credit) FILTER(WHERE entry_date<:from),0)";
        String movement=from==null?"true":"entry_date>=:from";
        String query="SELECT a.code,a.name,a.kind,coalesce(t.opening,0) AS opening,coalesce(t.debit,0) AS debit,coalesce(t.credit,0) AS credit,coalesce(t.closing,0) AS closing FROM finance_ledger_accounts a LEFT JOIN (SELECT account_code,"+opening+" AS opening,coalesce(sum(debit) FILTER(WHERE "+movement+"),0) AS debit,coalesce(sum(credit) FILTER(WHERE "+movement+"),0) AS credit,coalesce(sum(debit-credit),0) AS closing FROM finance_ledger_lines WHERE true"+(until==null?"":" AND entry_date<=:until")+" GROUP BY account_code) t ON t.account_code=a.code ORDER BY a.code";
        var rows=jdbc.queryForList(query,p);BigDecimal debit=BigDecimal.ZERO,credit=BigDecimal.ZERO;
        for(var row:rows){var balance=(BigDecimal)row.get("closing");if(balance.signum()>0)debit=debit.add(balance);else credit=credit.subtract(balance);}
        return new Trial(rows,debit,credit,debit.subtract(credit));
    }
    public Statement statement(String code,LocalDate from,LocalDate until,int page){
        var p=parameters(from,until).addValue("code",code).addValue("offset",page*20L);
        var accounts=jdbc.queryForList("SELECT code,name,kind FROM finance_ledger_accounts WHERE code=:code",p);
        if(accounts.isEmpty())throw new NoSuchElementException("Ledger account not found");
        String history=" FROM finance_ledger_lines WHERE account_code=:code"+(until==null?"":" AND entry_date<=:until");
        String opening=from==null?"0::numeric":"coalesce(sum(debit-credit) FILTER(WHERE entry_date<:from),0)";
        String movement=from==null?"true":"entry_date>=:from";
        var totals=jdbc.queryForMap("SELECT "+opening+" AS opening,coalesce(sum(debit) FILTER(WHERE "+movement+"),0) AS debit,coalesce(sum(credit) FILTER(WHERE "+movement+"),0) AS credit,coalesce(sum(debit-credit),0) AS closing"+history,p);
        long count=jdbc.queryForObject("SELECT count(*) FROM finance_ledger_lines WHERE account_code=:code"+period(from,until),p,Long.class);
        var rows=jdbc.queryForList("WITH history AS(SELECT *,sum(debit-credit) OVER(ORDER BY entry_date,entry_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance"+history+") SELECT * FROM history WHERE true"+(from==null?"":" AND entry_date>=:from")+" ORDER BY entry_date DESC,entry_id DESC LIMIT 20 OFFSET :offset",p);
        return new Statement(accounts.get(0),(BigDecimal)totals.get("opening"),(BigDecimal)totals.get("debit"),(BigDecimal)totals.get("credit"),(BigDecimal)totals.get("closing"),new PageImpl<>(rows,PageRequest.of(page,20),count));
    }
}
