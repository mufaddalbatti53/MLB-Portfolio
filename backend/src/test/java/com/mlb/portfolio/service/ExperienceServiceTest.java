package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlb.portfolio.dto.ExperienceRequest;
import com.mlb.portfolio.mapper.ExperienceMapper;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.repository.ExperienceRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ExperienceServiceTest {
    private static ExperienceService service(ExperienceRepository repository) {
        return new ExperienceService(repository, new ExperienceMapper(), new ContentSanitizer());
    }

    private static ExperienceRequest request(LocalDate endDate, boolean current) {
        return new ExperienceRequest(
            "Software Developer Intern",
            "Web Graphics Studio",
            "Internship · Part-Time",
            "Remote (Work From Home)",
            "Remote · India",
            LocalDate.of(2026, 1, 1),
            endDate,
            current,
            "Built responsive interfaces and order workflows.",
            List.of("Delivered client-side UI components."),
            List.of("HTML5", "CSS3", "JavaScript"),
            10,
            true
        );
    }

    @Test
    void publicListMapsPublishedExperiences() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        when(repository.findByPublishedTrueOrderBySortOrderAscStartDateDesc()).thenReturn(List.of());

        assertEquals(List.of(), service(repository).publicList());
        verify(repository).findByPublishedTrueOrderBySortOrderAscStartDateDesc();
    }

    @Test
    void pastExperienceRequiresEndDate() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        assertThrows(ResponseStatusException.class, () -> service(repository).create(request(null, false)));
    }

    @Test
    void currentExperienceRejectsEndDate() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        assertThrows(ResponseStatusException.class, () -> service(repository).create(
            request(LocalDate.of(2026, 6, 30), true)));
    }

    @Test
    void validExperiencePersists() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        when(repository.save(any(Experience.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository).create(request(LocalDate.of(2026, 6, 30), false));
        verify(repository).save(any(Experience.class));
    }
}
