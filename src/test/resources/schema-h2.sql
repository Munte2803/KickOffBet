-- H2 port of src/main/resources/schema.sql (PostgreSQL-only casts and interval syntax replaced).
CREATE OR REPLACE VIEW view_match_calculation_raw_data AS
WITH finished_matches AS (
    SELECT m.id, m.start_time, m.home_team_id, m.away_team_id, m.ft_home, m.ft_away
    FROM matches m
    WHERE m.status = 'FINISHED' AND m.ft_home IS NOT NULL AND m.ft_away IS NOT NULL
), team_match_rows AS (
    SELECT fm.home_team_id AS team_id, fm.start_time, 'HOME' AS side,
           fm.ft_home AS goals_for, fm.ft_away AS goals_against, (fm.ft_home + fm.ft_away) AS total_goals
    FROM finished_matches fm
    UNION ALL
    SELECT fm.away_team_id AS team_id, fm.start_time, 'AWAY' AS side,
           fm.ft_away AS goals_for, fm.ft_home AS goals_against, (fm.ft_home + fm.ft_away) AS total_goals
    FROM finished_matches fm
), season_stats AS (
    SELECT tmr.team_id,
           CAST(AVG(CASE WHEN tmr.side = 'HOME' THEN CAST(tmr.goals_for AS DOUBLE PRECISION) END) AS DOUBLE PRECISION) AS season_home_avg_scored,
           CAST(AVG(CASE WHEN tmr.side = 'HOME' THEN CAST(tmr.goals_against AS DOUBLE PRECISION) END) AS DOUBLE PRECISION) AS season_home_avg_conceded,
           CAST(AVG(CASE WHEN tmr.side = 'AWAY' THEN CAST(tmr.goals_for AS DOUBLE PRECISION) END) AS DOUBLE PRECISION) AS season_away_avg_scored,
           CAST(AVG(CASE WHEN tmr.side = 'AWAY' THEN CAST(tmr.goals_against AS DOUBLE PRECISION) END) AS DOUBLE PRECISION) AS season_away_avg_conceded,
           CAST(AVG(CASE WHEN tmr.goals_for > tmr.goals_against THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS season_win_rate,
           CAST(AVG(CASE WHEN tmr.goals_for = tmr.goals_against THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS season_draw_rate,
           CAST(AVG(CASE WHEN tmr.total_goals >= 1 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS season_over05_rate,
           CAST(AVG(CASE WHEN tmr.total_goals >= 2 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS season_over15_rate,
           CAST(AVG(CASE WHEN tmr.total_goals >= 3 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS season_over25_rate,
           CAST(AVG(CASE WHEN tmr.goals_for > 0 AND tmr.goals_against > 0 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS season_btts_rate
    FROM team_match_rows tmr
    WHERE tmr.start_time >= DATEADD('MONTH', -12, CURRENT_TIMESTAMP)
    GROUP BY tmr.team_id
), last5_ranked AS (
    SELECT tmr.*, ROW_NUMBER() OVER (PARTITION BY tmr.team_id ORDER BY tmr.start_time DESC, tmr.team_id) AS rn
    FROM team_match_rows tmr
), last5_stats AS (
    SELECT l5.team_id,
           CAST(AVG(CAST(l5.goals_for AS DOUBLE PRECISION)) AS DOUBLE PRECISION) AS last_5_avg_scored,
           CAST(AVG(CAST(l5.goals_against AS DOUBLE PRECISION)) AS DOUBLE PRECISION) AS last_5_avg_conceded,
           CAST(AVG(CASE WHEN l5.goals_for = l5.goals_against THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS last_5_draw_rate,
           CAST(AVG(CASE WHEN l5.total_goals >= 2 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS last_5_over15_rate,
           CAST(AVG(CASE WHEN l5.total_goals >= 3 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS last_5_over25_rate,
           CAST(AVG(CASE WHEN l5.goals_for > 0 AND l5.goals_against > 0 THEN 1.0 ELSE 0.0 END) AS DOUBLE PRECISION) AS last_5_btts_rate
    FROM last5_ranked l5
    WHERE l5.rn <= 5
    GROUP BY l5.team_id
)
SELECT t.id AS team_id,
       COALESCE(ss.season_home_avg_scored, 0) AS season_home_avg_scored,
       COALESCE(ss.season_home_avg_conceded, 0) AS season_home_avg_conceded,
       COALESCE(ss.season_away_avg_scored, 0) AS season_away_avg_scored,
       COALESCE(ss.season_away_avg_conceded, 0) AS season_away_avg_conceded,
       COALESCE(ss.season_win_rate, 0) AS season_win_rate,
       COALESCE(ss.season_draw_rate, 0) AS season_draw_rate,
       COALESCE(ss.season_over05_rate, 0) AS season_over05_rate,
       COALESCE(ss.season_over15_rate, 0) AS season_over15_rate,
       COALESCE(ss.season_over25_rate, 0) AS season_over25_rate,
       COALESCE(ss.season_btts_rate, 0) AS season_btts_rate,
       COALESCE(l5.last_5_avg_scored, 0) AS last_5_avg_scored,
       COALESCE(l5.last_5_avg_conceded, 0) AS last_5_avg_conceded,
       COALESCE(l5.last_5_draw_rate, 0) AS last_5_draw_rate,
       COALESCE(l5.last_5_over15_rate, 0) AS last_5_over15_rate,
       COALESCE(l5.last_5_over25_rate, 0) AS last_5_over25_rate,
       COALESCE(l5.last_5_btts_rate, 0) AS last_5_btts_rate
FROM teams t
LEFT JOIN season_stats ss ON ss.team_id = t.id
LEFT JOIN last5_stats l5 ON l5.team_id = t.id;

-- PostgreSQL's date(timestamp), used by the daily time-series native queries
CREATE ALIAS IF NOT EXISTS "date" FOR 'com.munte.KickOffBet.support.H2Functions.date';

-- ShedLock: one row per lock name, used by @SchedulerLock jobs and the manual sync endpoints (see SchedulingConfig)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
