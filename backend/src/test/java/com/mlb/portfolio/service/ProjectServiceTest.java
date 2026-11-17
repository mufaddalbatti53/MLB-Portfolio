package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlb.portfolio.mapper.ProjectMapper;
import com.mlb.portfolio.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class ProjectServiceTest {
    private static ProjectService service(ProjectRepository repository) {
        return new ProjectService(repository, new ProjectMapper(new ContentSanitizer()));
    }

    @Test
    void listReturnsOnlyMappedResponseFields() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectService service = service(repository);
        when(repository.findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(any(Pageable.class)))
            .thenReturn(List.of());

        assertEquals(List.of(), service.list(6));
        verify(repository).findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(argThat(page ->
            page.getPageNumber() == 0 && page.getPageSize() == 6));
    }

    @Test
    void missingOrUnpublishedProjectDoesNotLeak() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectService service = service(repository);
        when(repository.findBySlugAndPublishedTrue("private-draft"))
            .thenReturn(java.util.Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.get("private-draft"));
    }
}

