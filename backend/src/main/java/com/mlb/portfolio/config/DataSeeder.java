package com.mlb.portfolio.config;

import com.mlb.portfolio.model.BootstrapState;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.model.PortfolioProfile;
import com.mlb.portfolio.model.Project;
import com.mlb.portfolio.repository.BootstrapStateRepository;
import com.mlb.portfolio.repository.ExperienceRepository;
import com.mlb.portfolio.repository.PortfolioProfileRepository;
import com.mlb.portfolio.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public final class DataSeeder implements ApplicationRunner {
    private static final String BASE_SEED_VERSION = "mlb_initial_profile_projects_v1";
    private static final String CASE_STUDY_SEED_VERSION = "mlb_project_case_studies_v2";
    private static final String PROFILE_IDENTITY_SEED_VERSION = "mlb_profile_identity_v3";
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private static final int MIN_SIGNING_KEY_BYTES = 32;

    private final MongoTemplate mongo;
    private final PortfolioProfileRepository profiles;
    private final ProjectRepository projects;
    private final ExperienceRepository experiences;
    private final BootstrapStateRepository states;
    private final boolean enabled;
    private final String adminUsername;
    private final String adminPasswordHash;
    private final String signingKey;
    private final int jwtTtlMinutes;
    private final Clock clock;
    
    @Autowired
    public DataSeeder(
            MongoTemplate mongo,
            PortfolioProfileRepository profiles,
            ProjectRepository projects,
            ExperienceRepository experiences,
            BootstrapStateRepository states,
            @Value("${mlb.seed.enabled:true}") boolean enabled,
            @Value("${mlb.security.admin-username}") String adminUsername,
            @Value("${mlb.security.admin-password-hash}") String adminPasswordHash,
            @Value("${mlb.security.jwt-secret}") String signingKey,
            @Value("${mlb.security.jwt-ttl-minutes:15}") int jwtTtlMinutes) {
        this(mongo, profiles, projects, experiences, states, enabled, adminUsername, adminPasswordHash,
            signingKey, jwtTtlMinutes, Clock.systemUTC());
    }

    DataSeeder(
            MongoTemplate mongo,
            PortfolioProfileRepository profiles,
            ProjectRepository projects,
            ExperienceRepository experiences,
            BootstrapStateRepository states,
            boolean enabled,
            String adminUsername,
            String adminPasswordHash,
            String signingKey,
            int jwtTtlMinutes,
            Clock clock) {
        this.mongo = mongo;
        this.profiles = profiles;
        this.projects = projects;
        this.experiences = experiences;
        this.states = states;
        this.enabled = enabled;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
        this.signingKey = signingKey;
        this.jwtTtlMinutes = jwtTtlMinutes;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateSecurityEnvironment();
        backfillLegacyVersions();
        if (!enabled) return;

        Instant now = clock.instant();
        if (!states.existsById(BASE_SEED_VERSION)) {
            if (!profiles.existsById("owner")) {
                profiles.save(initialProfile(now));
            }
            states.save(new BootstrapState(BASE_SEED_VERSION, now));
        }

        if (!states.existsById(CASE_STUDY_SEED_VERSION)) {
            seedCaseStudies(now);
            states.save(new BootstrapState(CASE_STUDY_SEED_VERSION, now));
        }

        if (!states.existsById(PROFILE_IDENTITY_SEED_VERSION)) {
            migrateKnownLegacyProfileIdentity(now);
            states.save(new BootstrapState(PROFILE_IDENTITY_SEED_VERSION, now));
        }

        if (experiences.count() == 0) {
            experiences.save(initialExperience(now));
        }
    }


    private void migrateKnownLegacyProfileIdentity(Instant now) {
        PortfolioProfile profile = profiles.findById("owner").orElseGet(() -> initialProfile(now));
        boolean changed = profile.getId() == null;

        if (profile.getGithubUrl() == null || profile.getGithubUrl().isBlank()
                || "https://github.com".equals(profile.getGithubUrl())) {
            profile.setGithubUrl("https://github.com/mufaddalbatti53");
            changed = true;
        }
        if (profile.getLinkedinUrl() == null || profile.getLinkedinUrl().isBlank()
                || "https://linkedin.com".equals(profile.getLinkedinUrl())
                || "https://www.linkedin.com".equals(profile.getLinkedinUrl())) {
            profile.setLinkedinUrl("https://www.linkedin.com/in/mufaddal-tech/");
            changed = true;
        }
        if (profile.getWebsiteUrl() == null || profile.getWebsiteUrl().isBlank()
                || "https://github.io".equals(profile.getWebsiteUrl())) {
            profile.setWebsiteUrl("https://mufaddalbatti53.github.io/Mufaddal-Portfolio/");
            changed = true;
        }
        if (changed) {
            profile.setUpdatedAt(now);
            profiles.save(profile);
        }
    }

    private void backfillLegacyVersions() {
        Query missingVersion = Query.query(Criteria.where("version").exists(false));
        Update initialVersion = new Update().set("version", 0L);
        mongo.updateMulti(missingVersion, initialVersion, Project.class);
        mongo.updateMulti(missingVersion, initialVersion, com.mlb.portfolio.model.BlogPost.class);
        mongo.updateMulti(missingVersion, initialVersion, Experience.class);
    }

    private void seedCaseStudies(Instant now) {
        upsertCaseStudy(
            "mlb-full-stack-portfolio",
            "MLB Full-Stack Portfolio & Custom CMS",
            "A secure developer portfolio with a custom content-management workflow, recruiter-focused case studies, and production observability.",
            "A full-stack portfolio built without a frontend framework, backed by a protected Spring Boot admin API and MongoDB persistence.",
            "Static developer portfolios lack dynamic content management, real security hardening, and observability.",
            "Vanilla ES6+ frontend on GitHub Pages → Spring Boot 3 on Java 21 via Render → MongoDB Atlas, with HMAC/TTL rate limiting, short-lived Bearer JWT admin authentication, health monitoring, and CI/CD gates.",
            List.of(
                "Vanilla ES6+ browser UI",
                "GitHub Pages HTTPS and browser CSP",
                "Spring Boot 3 REST and admin API",
                "MongoDB Atlas documents and TTL indexes",
                "HMAC rate limiting and revocable Bearer JWT auth",
                "GitHub Actions CI and health monitoring"
            ),
            List.of(
                "Zero Frontend Frameworks",
                "HMAC + TTL Rate Limiting",
                "100% XSS-Safe DOM",
                "Automated CI/CD"
            ),
            List.of("Java 21", "Spring Boot", "MongoDB", "Vanilla JavaScript", "Nginx", "GitHub Actions"),
            "https://github.com/mufaddalbatti53",
            null,
            5,
            now
        );

        upsertCaseStudy(
            "complisca-prototype",
            "Complisca Prototype",
            "An end-to-end compliance workflow prototype that turns uploaded documents into OCR-driven checks, alerts, and an approval path.",
            "A JavaScript-based prototype that connects document upload, OCR extraction, compliance alerts, and officer verification in one workflow.",
            "Manual compliance review can scatter document checks, alerting, and approvals across disconnected steps, making it harder to track what still needs officer verification.",
            "Browser upload workflow → OCR extraction → rule-driven compliance checks → alert generation → approval workflow, with officer verification retained as the final human decision point.",
            List.of(
                "Document upload",
                "OCR text extraction",
                "Compliance rule checks",
                "Alert generation",
                "Approval workflow",
                "Officer verification"
            ),
            List.of(
                "End-to-End Upload → OCR Flow",
                "Automated Compliance Alerts",
                "Officer Verification Workflow"
            ),
            List.of("JavaScript", "Compliance Workflow", "OCR Integration", "Web App"),
            "https://github.com/prototype-complisca",
            null,
            10,
            now
        );

        upsertCaseStudy(
            "wgs-order-processing",
            "WGS Order Processing",
            "A responsive order-management workflow built with HTML, CSS, and JavaScript for a live production website.",
            "A lightweight browser application that organizes order intake and processing without introducing a frontend framework dependency.",
            "Order intake and status handling need a responsive workflow that keeps order information organized across desktop and mobile without relying on a heavy frontend framework.",
            "Responsive HTML/CSS/JavaScript client → order form and validation → order-processing workflow → production web deployment at webgraphicsstudio.in.",
            List.of(
                "Responsive order interface",
                "Client-side validation",
                "Order-processing workflow",
                "Production web deployment"
            ),
            List.of(
                "Live Production Deployment",
                "Responsive Mobile Order Flow",
                "Vanilla JavaScript UI"
            ),
            List.of("HTML", "CSS", "JavaScript", "Order Management System"),
            "https://github.com/WGS-Order-Processing",
            "https://webgraphicsstudio.in",
            20,
            now
        );
    }

    private void upsertCaseStudy(
            String slug,
            String title,
            String summary,
            String description,
            String problemStatement,
            String architectureOverview,
            List<String> architectureFlow,
            List<String> keyMetrics,
            List<String> technologies,
            String githubUrl,
            String liveUrl,
            int defaultOrder,
            Instant now) {
        Project project = projects.findBySlug(slug).orElseGet(Project::new);
        boolean isNew = project.getId() == null;

        project.setSlug(slug);
        project.setTitle(title);
        project.setSummary(summary);
        project.setDescription(description);
        project.setProblemStatement(problemStatement);
        project.setArchitectureOverview(architectureOverview);
        project.setArchitectureFlow(architectureFlow);
        project.setKeyMetrics(keyMetrics);
        project.setTechnologies(technologies);
        project.setGithubUrl(githubUrl);
        project.setLiveUrl(liveUrl);
        if (isNew) {
            project.setPublished(true);
            project.setFeatured(true);
            project.setSortOrder(defaultOrder);
            project.setCreatedAt(now);
        } else if (project.getCreatedAt() == null) {
            project.setCreatedAt(now);
        }
        project.setUpdatedAt(now);
        projects.save(project);
    }

    private void validateSecurityEnvironment() {
        if (adminUsername == null || !adminUsername.matches("[A-Za-z0-9_.-]{3,64}")) {
            throw new IllegalStateException("ADMIN_USERNAME must contain 3–64 safe characters");
        }
        if (adminPasswordHash == null || !BCRYPT.matcher(adminPasswordHash).matches()) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH must be a complete BCrypt hash");
        }
        int bcryptCost = Integer.parseInt(adminPasswordHash.substring(4, 6));
        if (bcryptCost < 12 || bcryptCost > 16) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH must use BCrypt cost 12–16");
        }
        if (jwtTtlMinutes < 1 || jwtTtlMinutes > 30) {
            throw new IllegalStateException("JWT_TTL_MINUTES must be between 1 and 30");
        }
        if (signingKey == null
                || signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SIGNING_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes");
        }
    }


    private static Experience initialExperience(Instant now) {
        Experience experience = new Experience();
        experience.setRole("Software Developer Intern");
        experience.setCompany("Web Graphics Studio");
        experience.setEmploymentType("Internship · Part-Time");
        experience.setWorkMode("Remote (Work From Home)");
        experience.setLocation("Remote · India");
        experience.setStartDate(LocalDate.parse("2026-01-01"));
        experience.setEndDate(LocalDate.parse("2026-06-30"));
        experience.setCurrent(false);
        experience.setSummary("Developed and maintained responsive web interfaces and structured order-processing workflows for Web Graphics Studio in a remote, part-time engineering workflow.");
        experience.setHighlights(List.of(
            "Developed and maintained responsive web interfaces and structured order-processing workflows, including the WGS Order Processing web application (oder.webgraphicsstudio.in).",
            "Delivered front-end UI/UX components, client-side form validation, and performance optimizations in a remote, part-time engineering workflow."
        ));
        experience.setTechnologies(List.of("HTML5", "CSS3", "JavaScript", "UI/UX", "Web Optimization"));
        experience.setSortOrder(10);
        experience.setPublished(true);
        experience.setCreatedAt(now);
        experience.setUpdatedAt(now);
        return experience;
    }

    private static PortfolioProfile initialProfile(Instant now) {
        PortfolioProfile profile = new PortfolioProfile();
        profile.setFullName("Mufaddal Battiwala");
        profile.setBrand("MLB");
        profile.setTagline("Full Stack Developer | Java + Spring Boot & Modern Web Developer");
        profile.setPublicEmail("mufaddal.techpro@gmail.com");
        profile.setPhone("+91 907933526");
        profile.setLocation("Kolkata, India");
        profile.setWebsiteUrl("https://mufaddalbatti53.github.io/Mufaddal-Portfolio/");
        profile.setGithubUrl("https://github.com/mufaddalbatti53");
        profile.setLinkedinUrl("https://www.linkedin.com/in/mufaddal-tech/");
        profile.setInstagramHandle("@mlb1606");
        profile.setEducation(List.of(
            new PortfolioProfile.Education("B.Tech (CSE)", "Netaji Subhash Engineering College", "Kolkata", null),
            new PortfolioProfile.Education("12th Grade", "Maharshi Sandipan Vidhyapeeth Senior Secondary School", "Kota", "81.2%"),
            new PortfolioProfile.Education("10th Grade", "New Haideriya English Senior Secondary School", null, "82.33%")
        ));
        profile.setUpdatedAt(now);
        return profile;
    }
}
