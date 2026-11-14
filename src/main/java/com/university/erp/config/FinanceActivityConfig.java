package com.university.erp.config;
import com.university.erp.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.*;

@Configuration @RequiredArgsConstructor
public class FinanceActivityConfig implements WebMvcConfigurer {
 private final org.springframework.beans.factory.ObjectProvider<JdbcTemplate> jdbc;
 @Override public void addInterceptors(InterceptorRegistry registry){
  registry.addInterceptor(new HandlerInterceptor(){
   @Override public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception ex){
    if(!java.util.Set.of("POST","PUT","PATCH","DELETE").contains(req.getMethod()))return;
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth==null||!(auth.getPrincipal() instanceof User actor))return;
    String path=req.getRequestURI();if(path.length()>300)return;
    try{jdbc.getObject().update("INSERT INTO finance_activity_events(actor,method,path,http_status) VALUES(?,?,?,?)",actor.getEmail(),req.getMethod(),path,res.getStatus());}
    catch(org.springframework.dao.DataAccessException error){org.slf4j.LoggerFactory.getLogger(FinanceActivityConfig.class).error("Finance activity recording failed; check activity storage availability");}
   }
  }).addPathPatterns("/api/finance/**");
 }
}
