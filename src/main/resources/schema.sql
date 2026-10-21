-- Per-team inputs of the odds engine. Statistics stay NULL when there is no data behind them (no
-- COALESCE to 0), and every group exposes its sample size, so the engine can tell a genuine 0 from
-- "no data" and shrink thin samples toward the league default.
CREATE OR REPLACE VIEW view_match_calculation_raw_data AS
WITH finished_matches AS (
         SELECT m.id,
            m.start_time,
            m.home_team_id,
            m.away_team_id,
            m.ft_home,
            m.ft_away
           FROM matches m
          WHERE (((m.status)::text = 'FINISHED'::text) AND (m.ft_home IS NOT NULL) AND (m.ft_away IS NOT NULL))
        ), team_match_rows AS (
         SELECT fm.home_team_id AS team_id,
            fm.start_time,
            'HOME'::text AS side,
            fm.ft_home AS goals_for,
            fm.ft_away AS goals_against,
            (fm.ft_home + fm.ft_away) AS total_goals
           FROM finished_matches fm
        UNION ALL
         SELECT fm.away_team_id AS team_id,
            fm.start_time,
            'AWAY'::text AS side,
            fm.ft_away AS goals_for,
            fm.ft_home AS goals_against,
            (fm.ft_home + fm.ft_away) AS total_goals
           FROM finished_matches fm
        ), season_stats AS (
         SELECT tmr.team_id,
            (count(*) FILTER (WHERE (tmr.side = 'HOME'::text)))::integer AS season_home_matches,
            (count(*) FILTER (WHERE (tmr.side = 'AWAY'::text)))::integer AS season_away_matches,
            (count(*))::integer AS season_matches,
            (avg(
                CASE
                    WHEN (tmr.side = 'HOME'::text) THEN tmr.goals_for
                    ELSE NULL::integer
                END))::double precision AS season_home_avg_scored,
            (avg(
                CASE
                    WHEN (tmr.side = 'HOME'::text) THEN tmr.goals_against
                    ELSE NULL::integer
                END))::double precision AS season_home_avg_conceded,
            (avg(
                CASE
                    WHEN (tmr.side = 'AWAY'::text) THEN tmr.goals_for
                    ELSE NULL::integer
                END))::double precision AS season_away_avg_scored,
            (avg(
                CASE
                    WHEN (tmr.side = 'AWAY'::text) THEN tmr.goals_against
                    ELSE NULL::integer
                END))::double precision AS season_away_avg_conceded,
            (avg(
                CASE
                    WHEN (tmr.goals_for > tmr.goals_against) THEN 1.0
                    ELSE 0.0
                END))::double precision AS season_win_rate,
            (avg(
                CASE
                    WHEN (tmr.goals_for = tmr.goals_against) THEN 1.0
                    ELSE 0.0
                END))::double precision AS season_draw_rate,
            (avg(
                CASE
                    WHEN (tmr.total_goals >= 1) THEN 1.0
                    ELSE 0.0
                END))::double precision AS season_over05_rate,
            (avg(
                CASE
                    WHEN (tmr.total_goals >= 2) THEN 1.0
                    ELSE 0.0
                END))::double precision AS season_over15_rate,
            (avg(
                CASE
                    WHEN (tmr.total_goals >= 3) THEN 1.0
                    ELSE 0.0
                END))::double precision AS season_over25_rate,
            (avg(
                CASE
                    WHEN ((tmr.goals_for > 0) AND (tmr.goals_against > 0)) THEN 1.0
                    ELSE 0.0
                END))::double precision AS season_btts_rate
           FROM team_match_rows tmr
          WHERE (tmr.start_time >= (now() - interval '12 months'))
          GROUP BY tmr.team_id
        ), last5_ranked AS (
         SELECT tmr.team_id,
            tmr.start_time,
            tmr.side,
            tmr.goals_for,
            tmr.goals_against,
            tmr.total_goals,
            row_number() OVER (PARTITION BY tmr.team_id ORDER BY tmr.start_time DESC, tmr.team_id) AS rn
           FROM team_match_rows tmr
          -- Form only counts while it is recent: 18 months still covers a team back after one season away
          WHERE (tmr.start_time >= (now() - interval '18 months'))
        ), last5_stats AS (
         SELECT l5_1.team_id,
            (count(*))::integer AS last_5_matches,
            (avg(l5_1.goals_for))::double precision AS last_5_avg_scored,
            (avg(l5_1.goals_against))::double precision AS last_5_avg_conceded,
            (avg(
                CASE
                    WHEN (l5_1.goals_for = l5_1.goals_against) THEN 1.0
                    ELSE 0.0
                END))::double precision AS last_5_draw_rate,
            (avg(
                CASE
                    WHEN (l5_1.total_goals >= 2) THEN 1.0
                    ELSE 0.0
                END))::double precision AS last_5_over15_rate,
            (avg(
                CASE
                    WHEN (l5_1.total_goals >= 3) THEN 1.0
                    ELSE 0.0
                END))::double precision AS last_5_over25_rate,
            (avg(
                CASE
                    WHEN ((l5_1.goals_for > 0) AND (l5_1.goals_against > 0)) THEN 1.0
                    ELSE 0.0
                END))::double precision AS last_5_btts_rate
           FROM last5_ranked l5_1
          WHERE (l5_1.rn <= 5)
          GROUP BY l5_1.team_id
        )
SELECT t.id AS team_id,
       ss.season_home_avg_scored,
       ss.season_home_avg_conceded,
       ss.season_away_avg_scored,
       ss.season_away_avg_conceded,
       ss.season_win_rate,
       ss.season_draw_rate,
       ss.season_over05_rate,
       ss.season_over15_rate,
       ss.season_over25_rate,
       ss.season_btts_rate,
       l5.last_5_avg_scored,
       l5.last_5_avg_conceded,
       l5.last_5_draw_rate,
       l5.last_5_over15_rate,
       l5.last_5_over25_rate,
       l5.last_5_btts_rate,
       -- sample sizes appended last, so CREATE OR REPLACE VIEW can replace the previous definition
       COALESCE(ss.season_home_matches, 0) AS season_home_matches,
       COALESCE(ss.season_away_matches, 0) AS season_away_matches,
       COALESCE(ss.season_matches, 0) AS season_matches,
       COALESCE(l5.last_5_matches, 0) AS last_5_matches
FROM ((teams t
    LEFT JOIN season_stats ss ON ((ss.team_id = t.id)))
    LEFT JOIN last5_stats l5 ON ((l5.team_id = t.id)));