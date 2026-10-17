package edu.assignment.patient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.assignment.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:patient-tests?mode=memory&cache=shared",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PatientServiceApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired PatientRepository patients;

    @BeforeEach
    void clearPatients() { patients.deleteAll(); }

    @Test
    void createsAndRetrievesPatient() throws Exception {
        String response = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asLong();
        mvc.perform(get("/api/patients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void returnsNotFoundForMissingPatient() throws Exception {
        mvc.perform(get("/api/patients/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Patient not found"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid patient input"));
    }

    @Test
    void inactivePatientIsNotEligible() throws Exception {
        String response = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Grace Hopper\",\"email\":\"grace@example.com\",\"active\":false}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asLong();
        mvc.perform(get("/api/patients/{id}/eligibility", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.eligible").value(false));
    }
}
