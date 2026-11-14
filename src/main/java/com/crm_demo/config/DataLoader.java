package com.crm_demo.config;

import com.crm_demo.entity.Candidate;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.repository.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Configuration
public class DataLoader {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    @Bean
    public CommandLineRunner initDatabase(CandidateRepository candidateRepository) {
        return args -> {
            if (candidateRepository.count() == 0) {
                log.info("Seeding initial candidate data into database...");

                Candidate c1 = new Candidate(
                        "John", "Doe", "john.doe@example.com", "+1-555-0101",
                        "Senior Java Engineer", 7, new BigDecimal("135000.00"),
                        "New York, NY", Set.of("Java", "Spring Boot", "Microservices", "Docker"),
                        CandidateStatus.SCREENING,
                        "Experienced backend developer specializing in high-throughput distributed systems."
                );

                Candidate c2 = new Candidate(
                        "Alice", "Smith", "alice.smith@example.com", "+1-555-0102",
                        "Full Stack Developer", 4, new BigDecimal("110000.00"),
                        "San Francisco, CA", Set.of("Java", "React", "PostgreSQL", "REST"),
                        CandidateStatus.INTERVIEWED,
                        "Full stack engineer passionate about modern frontend frameworks and clean API design."
                );

                Candidate c3 = new Candidate(
                        "Bob", "Johnson", "bob.johnson@example.com", "+1-555-0103",
                        "DevOps / Cloud Engineer", 6, new BigDecimal("130000.00"),
                        "Remote", Set.of("Kubernetes", "AWS", "Terraform", "Docker", "CI/CD"),
                        CandidateStatus.APPLIED,
                        "Infrastructure engineer with extensive multi-cloud and container orchestration expertise."
                );

                Candidate c4 = new Candidate(
                        "Sarah", "Connor", "sarah.wilson@example.com", "+1-555-0104",
                        "Data Platform Engineer", 5, new BigDecimal("125000.00"),
                        "Austin, TX", Set.of("Python", "Java", "Apache Spark", "Kafka"),
                        CandidateStatus.OFFERED,
                        "Data systems engineer focused on streaming pipelines and real-time analytics."
                );

                Candidate c5 = new Candidate(
                        "David", "Miller", "david.miller@example.com", "+1-555-0105",
                        "Junior Backend Developer", 2, new BigDecimal("85000.00"),
                        "Chicago, IL", Set.of("Java", "Spring Boot", "MySQL", "Git"),
                        CandidateStatus.APPLIED,
                        "Enthusiastic junior developer with foundational Spring Boot and database experience."
                );

                candidateRepository.saveAll(List.of(c1, c2, c3, c4, c5));
                log.info("Initialized database with 5 sample candidate profiles.");
            }
        };
    }
}
