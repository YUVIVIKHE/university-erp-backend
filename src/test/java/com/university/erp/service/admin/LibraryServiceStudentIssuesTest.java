package com.university.erp.service.admin;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LibraryServiceStudentIssuesTest {

    @Test
    void currentIssuesAreQueriedOnlyForTheRequestedStudentAndOpenLoans() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        InstitutionSettingsService settings = mock(InstitutionSettingsService.class);
        LibraryService service = new LibraryService(jdbc, mock(AdminAuditService.class), settings);
        doReturn(12).when(settings).getInt("libraryFinePerDay");
        doReturn(java.util.List.of()).when(jdbc).query(
            anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        service.currentIssuesForStudent(73L);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<MapSqlParameterSource> parameters =
            org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("li.student_id = :studentId"));
        assertTrue(sql.getValue().contains("li.status = 'ISSUED'"));
        assertEquals(73L, parameters.getValue().getValue("studentId"));
    }
}
