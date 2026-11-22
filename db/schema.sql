-- =====================================================================
-- Assay schema (MySQL 8, 13 tables). Portable to PostgreSQL:
-- no ENUM, no vendor functions. Allowed values are in column comments.
-- Hierarchy: forms > form_versions > sections > dimensions > questions > question_options
-- Run once on an empty database:  CREATE DATABASE assay CHARACTER SET utf8mb4;  USE assay;
-- =====================================================================

CREATE TABLE users (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  name             VARCHAR(150) NOT NULL,
  email            VARCHAR(255) NOT NULL,
  email_normalized VARCHAR(255) NOT NULL COMMENT 'lowercase, unique',
  password_hash    VARCHAR(255) NOT NULL COMMENT 'BCrypt',
  role             VARCHAR(12) NOT NULL COMMENT 'ADMIN, RESPONDENT',
  status           VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DISABLED',
  created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_login_at    TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email_normalized)
) ENGINE=InnoDB COMMENT='Admins and respondents who can log in';

CREATE TABLE refresh_tokens (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  token_hash  CHAR(64) NOT NULL COMMENT 'SHA-256 of the refresh token',
  expires_at  TIMESTAMP(6) NOT NULL,
  revoked_at  TIMESTAMP(6) NULL,
  created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY ix_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Rotating refresh tokens for JWT sessions';

CREATE TABLE forms (
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  owner_id              BIGINT NOT NULL COMMENT 'admin who owns the form',
  title                 VARCHAR(255) NOT NULL,
  description           TEXT NULL,
  status                VARCHAR(10) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, SAVED, PUBLISHED, CLOSED (OPEN and EXPIRED are computed from dates)',
  share_token           VARCHAR(64) NULL COMMENT 'random token used in /f/{token}',
  link_status           VARCHAR(8) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, REVOKED',
  published_version_id  BIGINT NULL COMMENT 'version respondents currently get',
  created_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at            TIMESTAMP(6) NULL COMMENT 'soft delete',
  PRIMARY KEY (id),
  UNIQUE KEY uq_forms_share (share_token),
  KEY ix_forms_owner (owner_id, status),
  CONSTRAINT fk_forms_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='One row per assessment form';

CREATE TABLE form_versions (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  form_id        BIGINT NOT NULL,
  version_no     INT NOT NULL,
  status         VARCHAR(10) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, PUBLISHED, ARCHIVED',
  opens_at       TIMESTAMP(6) NULL,
  expires_at     TIMESTAMP(6) NULL COMMENT 'enforced server-side on save and submit',
  published_at   TIMESTAMP(6) NULL,
  settings_json  JSON NULL COMMENT '{"target":3.0,"allowEditAfterSubmit":true}',
  created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_version (form_id, version_no),
  CONSTRAINT fk_ver_form FOREIGN KEY (form_id) REFERENCES forms (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Snapshots of a form; published versions are never edited';

CREATE TABLE sections (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  version_id  BIGINT NOT NULL,
  code        VARCHAR(10) NULL COMMENT 'letter from the sheet, e.g. A',
  name        VARCHAR(200) NOT NULL COMMENT 'CSV Section column; respondent chapters',
  sort_order  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_sec_version (version_id, sort_order),
  CONSTRAINT fk_sec_version FOREIGN KEY (version_id) REFERENCES form_versions (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Chapters of a version (A to K in the sample sheet)';

CREATE TABLE dimensions (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  section_id  BIGINT NOT NULL,
  name        VARCHAR(200) NOT NULL COMMENT 'CSV Dimension column; label and score group',
  sort_order  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_dim_section (section_id, sort_order),
  CONSTRAINT fk_dim_section FOREIGN KEY (section_id) REFERENCES sections (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Subject groups inside a section';

CREATE TABLE questions (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  version_id       BIGINT NOT NULL,
  section_id       BIGINT NOT NULL,
  dimension_id     BIGINT NOT NULL,
  external_id      VARCHAR(40) NULL COMMENT 'Q_ID from the sheet',
  question_text    TEXT NOT NULL,
  question_type    VARCHAR(16) NOT NULL COMMENT 'SHORT, LONG, SINGLE, MULTI, DROPDOWN, YES_NO, SCALE, RATING, LIKERT, NPS, RANKING, DATE, TIME, NUMBER, EMAIL',
  help_text        TEXT NULL COMMENT 'Evidence_or_Example column',
  mandatory        BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order       INT NOT NULL DEFAULT 0,
  validation_json  JSON NULL COMMENT 'e.g. {"minLength":20} or {"min":0,"max":100}',
  config_json      JSON NULL COMMENT 'e.g. {"allowDontKnow":true}',
  PRIMARY KEY (id),
  UNIQUE KEY uq_q_external (version_id, external_id),
  KEY ix_q_order (version_id, section_id, sort_order),
  CONSTRAINT fk_q_version FOREIGN KEY (version_id) REFERENCES form_versions (id) ON DELETE CASCADE,
  CONSTRAINT fk_q_section FOREIGN KEY (section_id) REFERENCES sections (id) ON DELETE CASCADE,
  CONSTRAINT fk_q_dim FOREIGN KEY (dimension_id) REFERENCES dimensions (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Questions of a version';

CREATE TABLE question_options (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  question_id  BIGINT NOT NULL,
  label        VARCHAR(500) NOT NULL,
  score        DECIMAL(6,2) NULL COMMENT 'NULL = not scored',
  is_na        BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Dont know; excluded from scoring',
  sort_order   INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_opt_q (question_id, sort_order),
  CONSTRAINT fk_opt_q FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Choices of choice-type questions';

CREATE TABLE form_assignments (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  form_id      BIGINT NOT NULL,
  email        VARCHAR(255) NOT NULL COMMENT 'lowercase invited email',
  assigned_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_assign (form_id, email),
  CONSTRAINT fk_assign_form FOREIGN KEY (form_id) REFERENCES forms (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Optional invited respondents; gives Assigned counts';

CREATE TABLE submissions (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  form_id        BIGINT NOT NULL,
  version_id     BIGINT NOT NULL COMMENT 'version the respondent answers',
  user_id        BIGINT NOT NULL,
  status         VARCHAR(10) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, SUBMITTED',
  started_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_saved_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  submitted_at   TIMESTAMP(6) NULL,
  row_version    INT NOT NULL DEFAULT 0 COMMENT 'optimistic lock for autosave',
  PRIMARY KEY (id),
  UNIQUE KEY uq_one_per_user (form_id, user_id),
  KEY ix_sub_status (form_id, status),
  CONSTRAINT fk_sub_form FOREIGN KEY (form_id) REFERENCES forms (id),
  CONSTRAINT fk_sub_version FOREIGN KEY (version_id) REFERENCES form_versions (id),
  CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='One response per respondent per form';

CREATE TABLE answers (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  submission_id       BIGINT NOT NULL,
  question_id         BIGINT NOT NULL,
  value_text          TEXT NULL,
  value_number        DECIMAL(18,4) NULL,
  value_date          DATE NULL,
  selected_option_id  BIGINT NULL COMMENT 'single-choice answer',
  remarks             TEXT NULL COMMENT 'respondent comment',
  state               VARCHAR(8) NOT NULL DEFAULT 'ANSWERED' COMMENT 'ANSWERED, REVIEW (drives the dot matrix)',
  answered_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_answer (submission_id, question_id),
  KEY ix_answers_q (question_id, selected_option_id),
  CONSTRAINT fk_ans_sub FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
  CONSTRAINT fk_ans_q FOREIGN KEY (question_id) REFERENCES questions (id),
  CONSTRAINT fk_ans_opt FOREIGN KEY (selected_option_id) REFERENCES question_options (id)
) ENGINE=InnoDB COMMENT='One row per answered question';

CREATE TABLE answer_options (
  answer_id  BIGINT NOT NULL,
  option_id  BIGINT NOT NULL,
  rank_no    INT NULL COMMENT 'position for ranking questions',
  PRIMARY KEY (answer_id, option_id),
  CONSTRAINT fk_ao_answer FOREIGN KEY (answer_id) REFERENCES answers (id) ON DELETE CASCADE,
  CONSTRAINT fk_ao_option FOREIGN KEY (option_id) REFERENCES question_options (id)
) ENGINE=InnoDB COMMENT='Selected options of multi-choice and ranking answers';

CREATE TABLE submission_events (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  submission_id  BIGINT NOT NULL,
  event_type     VARCHAR(10) NOT NULL COMMENT 'CREATED, SAVED, SUBMITTED, EDITED',
  actor_id       BIGINT NULL,
  detail_json    JSON NULL,
  created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY ix_ev_sub (submission_id, created_at),
  CONSTRAINT fk_ev_sub FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='History of saves, submits and edits';

-- Circular reference added last: forms -> form_versions
ALTER TABLE forms ADD CONSTRAINT fk_forms_published_version
  FOREIGN KEY (published_version_id) REFERENCES form_versions (id) ON DELETE SET NULL;
