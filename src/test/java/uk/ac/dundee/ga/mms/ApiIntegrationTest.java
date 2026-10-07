package uk.ac.dundee.ga.mms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API tests against H2 with the synthetic demo data (scenarios E1-E6, S1-S5). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode getJson(String token, String url) throws Exception {
        MvcResult r = mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    private Map<String, Object> meetingBody(long studentId, LocalDate date, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", studentId);
        m.put("meetingDate", date.toString());
        m.put("format", "ONLINE");
        m.put("studentPresent", true);
        m.put("mentorPresent", true);
        m.put("workActivities", "Building ETL jobs <script>alert('x')</script>");
        m.put("agreedActions", "Share project plan by December.");
        m.put("concernFlag", false);
        m.put("status", status);
        m.put("confirmDuplicate", true);
        return m;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/London"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/config")).andExpect(status().isOk());
    }

    @Test
    void aosRecordsMeetingAndDashboardCounts() throws Exception {
        String aos = login("aos1@mms.local");
        JsonNode advisees = getJson(aos, "/api/v1/advisees").get("advisees");
        assertThat(advisees.size()).isGreaterThan(0);
        long studentId = advisees.get(0).get("studentId").asLong();
        int before = advisees.get(0).get("meetingsDone").asInt();

        LocalDate date = today().isBefore(LocalDate.of(2026, 9, 1)) ? LocalDate.of(2026, 9, 1) : today();
        MvcResult created = mvc.perform(post("/api/v1/meetings").header("Authorization", "Bearer " + aos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(meetingBody(studentId, date, "SUBMITTED"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode m = json.readTree(created.getResponse().getContentAsString());
        assertThat(m.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(m.get("workActivities").asText()).isEqualTo("Building ETL jobs"); // S4: script stripped
        long meetingId = m.get("id").asLong();

        JsonNode after = getJson(aos, "/api/v1/advisees").get("advisees");
        for (JsonNode row : after) {
            if (row.get("studentId").asLong() == studentId) {
                assertThat(row.get("meetingsDone").asInt()).isEqualTo(before + 1); // E1
            }
        }

        // E2: previous meeting panel
        JsonNode latest = getJson(aos, "/api/v1/students/" + studentId + "/meetings/latest");
        assertThat(latest.get("id").asLong()).isEqualTo(meetingId);

        // S3: stale version -> 412
        mvc.perform(put("/api/v1/meetings/" + meetingId).header("Authorization", "Bearer " + aos)
                        .header("If-Match", "\"999\"").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(meetingBody(studentId, date, "SUBMITTED"))))
                .andExpect(status().isPreconditionFailed());

        // Amend within window with the right version creates a revision
        String etag = "\"" + m.get("version").asInt() + "\"";
        mvc.perform(put("/api/v1/meetings/" + meetingId).header("Authorization", "Bearer " + aos)
                        .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(meetingBody(studentId, date, "SUBMITTED"))))
                .andExpect(status().isOk());
        assertThat(getJson(aos, "/api/v1/meetings/" + meetingId + "/revisions").size()).isEqualTo(1);

        // E6: PDF
        mvc.perform(get("/api/v1/meetings/" + meetingId + "/pdf").header("Authorization", "Bearer " + aos))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void futureDateIsRejected() throws Exception {
        String aos = login("aos1@mms.local");
        long studentId = getJson(aos, "/api/v1/advisees").get("advisees").get(0).get("studentId").asLong();
        mvc.perform(post("/api/v1/meetings").header("Authorization", "Bearer " + aos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(meetingBody(studentId, today().plusDays(3), "SUBMITTED"))))
                .andExpect(status().isBadRequest()); // S2
    }

    @Test
    void aosCannotRecordForSomeoneElsesAdvisee() throws Exception {
        String aos2 = login("aos2@mms.local");
        String aos1 = login("aos1@mms.local");
        long notMine = getJson(aos2, "/api/v1/advisees").get("advisees").get(0).get("studentId").asLong();
        mvc.perform(post("/api/v1/meetings").header("Authorization", "Bearer " + aos1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(meetingBody(notMine, today(), "SUBMITTED"))))
                .andExpect(status().isForbidden()); // E3
    }

    @Test
    void directorSeesDashboardAndExport() throws Exception {
        String dir = login("director@mms.local");
        JsonNode summary = getJson(dir, "/api/v1/dashboard/summary");
        assertThat(summary.get("students").asInt()).isGreaterThanOrEqualTo(40);
        JsonNode overdueOnly = getJson(dir, "/api/v1/dashboard/students?status=OVERDUE");
        for (JsonNode r : overdueOnly) {
            assertThat(r.get("status").asText()).isEqualTo("OVERDUE"); // E4
        }
        mvc.perform(get("/api/v1/dashboard/students/export").header("Authorization", "Bearer " + dir))
                .andExpect(status().isOk());
        assertThat(getJson(dir, "/api/v1/dashboard/advisors").size()).isGreaterThan(0);
    }

    @Test
    void programmeLeadCannotSeeAnotherProgrammesStudent() throws Exception {
        String dir = login("director@mms.local");
        String lead = login("lead.se@mms.local");
        JsonNode all = getJson(dir, "/api/v1/students?size=500").get("content");
        java.util.Set<Long> leadVisible = new java.util.HashSet<>();
        for (JsonNode s : getJson(lead, "/api/v1/students?size=500").get("content")) {
            leadVisible.add(s.get("id").asLong());
        }
        assertThat(leadVisible).isNotEmpty();
        Long other = null;
        for (JsonNode s : all) {
            if (!leadVisible.contains(s.get("id").asLong())) {
                other = s.get("id").asLong();
                break;
            }
        }
        assertThat(other).isNotNull();
        mvc.perform(get("/api/v1/students/" + other).header("Authorization", "Bearer " + lead))
                .andExpect(status().isForbidden()); // S1
    }

    @Test
    void adminImportsAllocationsWithPreview() throws Exception {
        String admin = login("admin.ops@mms.local");
        String csv = "matric_no,first_name,last_name,student_email,programme_code,aos_email,employer_name,mentor_name,mentor_email,academic_year\n"
                + "2599001,Test,One,2599001@dundee.ac.uk,GA-SE,aos1@mms.local,New Co,Pat Mentor,pat@newco.example,2026/27\n"
                + "2599002,Test,Two,,GA-DS,aos2@mms.local,,,,2026/27\n"
                + "BAD!,Test,Three,,NOPE,nobody@mms.local,,,,2026/27\n";
        MvcResult up = mvc.perform(multipart("/api/v1/imports/allocations")
                        .file(new MockMultipartFile("file", "alloc.csv", "text/csv", csv.getBytes()))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode preview = json.readTree(up.getResponse().getContentAsString());
        assertThat(preview.get("rowsOk").asInt()).isEqualTo(2);
        assertThat(preview.get("rowsError").asInt()).isEqualTo(1); // E5
        long batchId = preview.get("batchId").asLong();
        mvc.perform(post("/api/v1/imports/" + batchId + "/commit").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        JsonNode found = getJson(admin, "/api/v1/students?q=2599001").get("content");
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).get("aosName").asText()).isEqualTo("Dr Alan Advisor");
    }

    @Test
    void aosCannotUseAdminEndpoints() throws Exception {
        String aos = login("aos1@mms.local");
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + aos)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", "Bearer " + aos)).andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"aos3@mms.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }
}
