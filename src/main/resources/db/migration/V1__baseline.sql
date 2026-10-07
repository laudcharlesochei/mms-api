-- Mentor Management System - V1 baseline schema (MySQL 8 / JawsDB; also runs on H2 in MySQL mode for local dev).
-- utf8mb4 / InnoDB are set at database level on JawsDB. All timestamps are UTC.
-- Enumerations are stored as VARCHAR (portable); the API validates allowed values.

CREATE TABLE academic_year (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    label             VARCHAR(9)  NOT NULL,
    start_date        DATE        NOT NULL,
    end_date          DATE        NOT NULL,
    required_meetings INT         NOT NULL DEFAULT 3,
    is_current        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_academic_year_label UNIQUE (label)
);

CREATE TABLE meeting_period (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    academic_year_id BIGINT NOT NULL,
    seq              INT    NOT NULL,
    start_date       DATE   NOT NULL,
    end_date         DATE   NOT NULL,
    CONSTRAINT uq_period_year_seq UNIQUE (academic_year_id, seq),
    CONSTRAINT fk_period_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id)
);

CREATE TABLE app_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    display_name   VARCHAR(160) NOT NULL,
    entra_oid      CHAR(36)     NULL,
    password_hash  VARCHAR(100) NULL,
    mfa_secret     VARCHAR(255) NULL,
    mfa_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_logins  INT          NOT NULL DEFAULT 0,
    locked_until   DATETIME(6)  NULL,
    last_login_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NULL,
    CONSTRAINT uq_user_email UNIQUE (email),
    CONSTRAINT uq_user_entra_oid UNIQUE (entra_oid)
);

CREATE TABLE user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE programme (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    lead_user_id BIGINT       NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_programme_code UNIQUE (code),
    CONSTRAINT fk_programme_lead FOREIGN KEY (lead_user_id) REFERENCES app_user (id)
);

CREATE TABLE employer (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(160) NOT NULL,
    address VARCHAR(255) NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_employer_name UNIQUE (name)
);

CREATE TABLE workplace_mentor (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    employer_id BIGINT       NOT NULL,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(254) NULL,
    phone       VARCHAR(30)  NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_mentor_employer FOREIGN KEY (employer_id) REFERENCES employer (id)
);

CREATE TABLE student (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    matric_no     VARCHAR(12)  NOT NULL,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    uni_email     VARCHAR(254) NULL,
    programme_id  BIGINT       NOT NULL,
    employer_id   BIGINT       NULL,
    mentor_id     BIGINT       NULL,
    year_of_study INT          NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_student_matric UNIQUE (matric_no),
    CONSTRAINT fk_student_programme FOREIGN KEY (programme_id) REFERENCES programme (id),
    CONSTRAINT fk_student_employer FOREIGN KEY (employer_id) REFERENCES employer (id),
    CONSTRAINT fk_student_mentor FOREIGN KEY (mentor_id) REFERENCES workplace_mentor (id)
);
CREATE INDEX ix_student_programme ON student (programme_id);

CREATE TABLE advisor_allocation (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id       BIGINT      NOT NULL,
    aos_user_id      BIGINT      NOT NULL,
    academic_year_id BIGINT      NOT NULL,
    valid_from       DATE        NOT NULL,
    valid_to         DATE        NULL,
    source           VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    CONSTRAINT fk_alloc_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_alloc_aos FOREIGN KEY (aos_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_alloc_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id)
);
CREATE INDEX ix_alloc_year ON advisor_allocation (academic_year_id);
CREATE INDEX ix_alloc_aos ON advisor_allocation (aos_user_id);
CREATE INDEX ix_alloc_student ON advisor_allocation (student_id);

CREATE TABLE mentor_meeting (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id             BIGINT       NOT NULL,
    aos_user_id            BIGINT       NOT NULL,
    mentor_id              BIGINT       NULL,
    academic_year_id       BIGINT       NOT NULL,
    period_id              BIGINT       NULL,
    meeting_date           DATE         NOT NULL,
    format                 VARCHAR(10)  NULL,
    student_present        BOOLEAN      NOT NULL DEFAULT FALSE,
    mentor_present         BOOLEAN      NOT NULL DEFAULT FALSE,
    work_activities        TEXT         NULL,
    university_progress    TEXT         NULL,
    university_feedback    TEXT         NULL,
    training_opportunities TEXT         NULL,
    agreed_actions         TEXT         NULL,
    concern_flag           BOOLEAN      NOT NULL DEFAULT FALSE,
    concern_note           TEXT         NULL,
    next_meeting_date      DATE         NULL,
    status                 VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    submitted_at           DATETIME(6)  NULL,
    version                INT          NOT NULL DEFAULT 0,
    created_by             BIGINT       NULL,
    created_at             DATETIME(6)  NULL,
    updated_by             BIGINT       NULL,
    updated_at             DATETIME(6)  NULL,
    deleted_at             DATETIME(6)  NULL,
    deleted_by             BIGINT       NULL,
    delete_reason          VARCHAR(500) NULL,
    client_request_id      VARCHAR(64)  NULL,
    CONSTRAINT uq_meeting_client_request UNIQUE (client_request_id),
    CONSTRAINT fk_meeting_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_meeting_aos FOREIGN KEY (aos_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_meeting_mentor FOREIGN KEY (mentor_id) REFERENCES workplace_mentor (id),
    CONSTRAINT fk_meeting_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id),
    CONSTRAINT fk_meeting_period FOREIGN KEY (period_id) REFERENCES meeting_period (id)
);
CREATE INDEX ix_meeting_student_date ON mentor_meeting (student_id, meeting_date);
CREATE INDEX ix_meeting_aos ON mentor_meeting (aos_user_id);
CREATE INDEX ix_meeting_year_status ON mentor_meeting (academic_year_id, status);

CREATE TABLE meeting_revision (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id    BIGINT      NOT NULL,
    version       INT         NOT NULL,
    snapshot_json LONGTEXT    NOT NULL,
    changed_by    BIGINT      NULL,
    changed_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_revision_meeting FOREIGN KEY (meeting_id) REFERENCES mentor_meeting (id)
);
CREATE INDEX ix_revision_meeting ON meeting_revision (meeting_id);

CREATE TABLE import_batch (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL,
    uploaded_by  BIGINT       NULL,
    uploaded_at  DATETIME(6)  NOT NULL,
    rows_total   INT          NOT NULL DEFAULT 0,
    rows_ok      INT          NOT NULL DEFAULT 0,
    rows_error   INT          NOT NULL DEFAULT 0,
    rows_add     INT          NOT NULL DEFAULT 0,
    rows_update  INT          NOT NULL DEFAULT 0,
    status       VARCHAR(10)  NOT NULL,
    payload_json LONGTEXT     NULL,
    committed_at DATETIME(6)  NULL
);

CREATE TABLE import_row_error (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT       NOT NULL,
    row_no   INT          NOT NULL,
    message  VARCHAR(500) NOT NULL,
    CONSTRAINT fk_import_error_batch FOREIGN KEY (batch_id) REFERENCES import_batch (id)
);

CREATE TABLE audit_event (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at  DATETIME(6)  NOT NULL,
    user_id      BIGINT       NULL,
    user_email   VARCHAR(254) NULL,
    action       VARCHAR(40)  NOT NULL,
    entity_type  VARCHAR(40)  NULL,
    entity_id    VARCHAR(40)  NULL,
    ip_hash      VARCHAR(64)  NULL,
    details_json VARCHAR(2000) NULL
);
CREATE INDEX ix_audit_time ON audit_event (occurred_at);
CREATE INDEX ix_audit_user ON audit_event (user_id);

-- Carried over from MentorSync: direct messages, invitations, reminder log
CREATE TABLE staff_message (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id    BIGINT        NOT NULL,
    recipient_id BIGINT        NOT NULL,
    body         VARCHAR(4000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    read_at      DATETIME(6)   NULL,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES app_user (id),
    CONSTRAINT fk_msg_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id)
);
CREATE INDEX ix_msg_recipient ON staff_message (recipient_id);
CREATE INDEX ix_msg_sender ON staff_message (sender_id);

CREATE TABLE user_invite (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL,
    invited_by BIGINT       NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT uq_invite_token UNIQUE (token_hash),
    CONSTRAINT fk_invite_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE reminder_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    aos_user_id BIGINT      NOT NULL,
    period_id   BIGINT      NOT NULL,
    kind        VARCHAR(20) NOT NULL,
    sent_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_reminder UNIQUE (aos_user_id, period_id, kind)
);
