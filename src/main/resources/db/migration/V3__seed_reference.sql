-- Seed: current academic year with three evenly spaced meeting periods, and the GA programmes.
-- Roles are an enumeration in code (Role.java). Users are created at start-up (bootstrap admin)
-- or by an Administrator. Never put real student data in seed scripts.
INSERT INTO academic_year (label, start_date, end_date, required_meetings, is_current)
VALUES ('2026/27', DATE '2026-09-01', DATE '2027-08-31', 3, TRUE);

INSERT INTO meeting_period (academic_year_id, seq, start_date, end_date)
SELECT id, 1, DATE '2026-09-01', DATE '2026-12-31' FROM academic_year WHERE label = '2026/27';
INSERT INTO meeting_period (academic_year_id, seq, start_date, end_date)
SELECT id, 2, DATE '2027-01-01', DATE '2027-04-30' FROM academic_year WHERE label = '2026/27';
INSERT INTO meeting_period (academic_year_id, seq, start_date, end_date)
SELECT id, 3, DATE '2027-05-01', DATE '2027-08-31' FROM academic_year WHERE label = '2026/27';

INSERT INTO programme (code, name, active) VALUES ('GA-SE', 'Software Engineering', TRUE);
INSERT INTO programme (code, name, active) VALUES ('GA-DS', 'Data Science', TRUE);
INSERT INTO programme (code, name, active) VALUES ('GA-CS', 'Cyber Security', TRUE);
INSERT INTO programme (code, name, active) VALUES ('GA-ITM', 'IT Management for Business', TRUE);
