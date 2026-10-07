-- v_student_progress: per student and academic year, the submitted (non-deleted) meeting count,
-- required count, last meeting date and whether the latest meeting raised a concern.
-- Used for reporting (e.g. Power BI / ad-hoc SQL). The API's DashboardService computes the
-- period-based status (On track / Due / Overdue / Concern) on read so no counters are stored.
CREATE VIEW v_student_progress AS
SELECT a.student_id,
       a.academic_year_id,
       a.aos_user_id,
       s.programme_id,
       y.required_meetings,
       (SELECT COUNT(*) FROM mentor_meeting m
         WHERE m.student_id = a.student_id AND m.academic_year_id = a.academic_year_id
           AND m.status = 'SUBMITTED' AND m.deleted_at IS NULL) AS meetings_done,
       (SELECT MAX(m.meeting_date) FROM mentor_meeting m
         WHERE m.student_id = a.student_id AND m.academic_year_id = a.academic_year_id
           AND m.status = 'SUBMITTED' AND m.deleted_at IS NULL) AS last_meeting_date,
       (SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM mentor_meeting m
         WHERE m.student_id = a.student_id AND m.academic_year_id = a.academic_year_id
           AND m.status = 'SUBMITTED' AND m.deleted_at IS NULL AND m.concern_flag = TRUE
           AND m.meeting_date = (SELECT MAX(m2.meeting_date) FROM mentor_meeting m2
                                  WHERE m2.student_id = a.student_id AND m2.academic_year_id = a.academic_year_id
                                    AND m2.status = 'SUBMITTED' AND m2.deleted_at IS NULL)) AS open_concern
FROM advisor_allocation a
JOIN student s ON s.id = a.student_id
JOIN academic_year y ON y.id = a.academic_year_id
WHERE a.valid_to IS NULL;
