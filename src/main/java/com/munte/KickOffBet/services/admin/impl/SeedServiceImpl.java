package com.munte.KickOffBet.services.admin.impl;

import com.munte.KickOffBet.domain.dto.api.response.SeedResultDto;
import com.munte.KickOffBet.services.admin.SeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeedServiceImpl implements SeedService {

    private static final String EMAIL_PREFIX = "seed.user.";
    private static final String EMAIL_DOMAIN = "@kickoffbet.test";
    private static final int USER_COUNT = 510;
    private static final int DAYS_BACK = 365;
    private static final Random RNG = new Random(42L);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.user-password:Seed1234!}")
    private String seedUserPassword;

    @Override
    @Transactional
    public SeedResultDto runSeed() {
        long start = System.currentTimeMillis();
        log.info("[SEED] Starting seed run");

        cleanupExistingSeed();

        List<MatchRow> finishedMatches = fetchMatches("FINISHED");
        List<MatchRow> scheduledMatches = fetchMatches("SCHEDULED");
        Map<UUID, List<OfferRow>> offersByMatch = fetchOffersGroupedByMatch();
        log.info("[SEED] Available: {} finished, {} scheduled matches, offers on {} matches",
                finishedMatches.size(), scheduledMatches.size(), offersByMatch.size());

        if (finishedMatches.isEmpty() && scheduledMatches.isEmpty()) {
            log.warn("[SEED] No matches available. Run sync first.");
            return new SeedResultDto(0, 0, 0, System.currentTimeMillis() - start);
        }

        String passwordHash = passwordEncoder.encode(seedUserPassword);

        List<UserSeed> users = buildUsers(passwordHash);
        insertUsers(users);

        TicketBatch ticketBatch = buildTickets(users, finishedMatches, scheduledMatches, offersByMatch);
        insertTickets(ticketBatch);

        List<TxnSeed> transactions = buildTransactions(users, ticketBatch);
        insertTransactions(transactions);

        updateBalances(users, transactions);

        long duration = System.currentTimeMillis() - start;
        log.info("[SEED] Done. {} users, {} tickets, {} transactions in {}ms",
                users.size(), ticketBatch.tickets.size(), transactions.size(), duration);

        return new SeedResultDto(users.size(), ticketBatch.tickets.size(), transactions.size(), duration);
    }

    // -------------------- Cleanup --------------------

    private void cleanupExistingSeed() {
        log.info("[SEED] Cleaning up existing seed data");
        jdbc.update("""
                DELETE FROM transactions WHERE user_id IN
                  (SELECT id FROM users WHERE email LIKE ?)
                """, EMAIL_PREFIX + "%" + EMAIL_DOMAIN);
        jdbc.update("""
                DELETE FROM ticket_selections WHERE ticket_id IN
                  (SELECT id FROM tickets WHERE user_id IN
                    (SELECT id FROM users WHERE email LIKE ?))
                """, EMAIL_PREFIX + "%" + EMAIL_DOMAIN);
        jdbc.update("""
                DELETE FROM tickets WHERE user_id IN
                  (SELECT id FROM users WHERE email LIKE ?)
                """, EMAIL_PREFIX + "%" + EMAIL_DOMAIN);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%" + EMAIL_DOMAIN);
    }

    // -------------------- Fetch helpers --------------------

    private record MatchRow(UUID id, LocalDateTime startTime, Integer ftHome, Integer ftAway) {}
    private record OfferRow(UUID id, String marketType, String option, BigDecimal line, BigDecimal odds) {}

    private List<MatchRow> fetchMatches(String status) {
        return jdbc.query(
                "SELECT id, start_time, ft_home, ft_away FROM matches WHERE status = ? AND active = TRUE",
                (rs, i) -> new MatchRow(
                        (UUID) rs.getObject("id"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        (Integer) rs.getObject("ft_home"),
                        (Integer) rs.getObject("ft_away")
                ),
                status
        );
    }

    private Map<UUID, List<OfferRow>> fetchOffersGroupedByMatch() {
        Map<UUID, List<OfferRow>> map = new HashMap<>();
        jdbc.query("""
                SELECT id, match_id, market_type, option, line, odds
                FROM market_offers WHERE active = TRUE
                """, rs -> {
            UUID matchId = (UUID) rs.getObject("match_id");
            map.computeIfAbsent(matchId, k -> new ArrayList<>()).add(new OfferRow(
                    (UUID) rs.getObject("id"),
                    rs.getString("market_type"),
                    rs.getString("option"),
                    rs.getBigDecimal("line"),
                    rs.getBigDecimal("odds")
            ));
        });
        return map;
    }

    // -------------------- Users --------------------

    private record UserSeed(UUID id, String email, String firstName, String lastName,
                            LocalDate birthDate, LocalDateTime createdAt,
                            String status, boolean emailVerified, boolean idCardVerified) {}

    private List<UserSeed> buildUsers(String passwordHash) {
        List<UserSeed> users = new ArrayList<>(USER_COUNT);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String[] firstNames = {"Andrei", "Mihai", "Cristian", "Alexandru", "Stefan", "Ioana", "Maria", "Elena",
                "George", "Bogdan", "Vlad", "Dan", "Catalin", "Adrian", "Sorin", "Razvan",
                "Diana", "Andreea", "Raluca", "Cristina"};
        String[] lastNames = {"Popescu", "Ionescu", "Popa", "Stoica", "Dumitrescu", "Stan", "Munteanu", "Marin",
                "Constantin", "Radu", "Tudor", "Diaconu", "Nistor", "Iliescu", "Lupu", "Sandu"};

        for (int i = 0; i < USER_COUNT; i++) {
            UUID id = UUID.randomUUID();
            String email = EMAIL_PREFIX + i + EMAIL_DOMAIN;
            String first = firstNames[RNG.nextInt(firstNames.length)];
            String last = lastNames[RNG.nextInt(lastNames.length)];
            LocalDate birth = LocalDate.now().minusYears(20 + RNG.nextInt(30)).minusDays(RNG.nextInt(365));
            int daysBack = RNG.nextInt(DAYS_BACK);
            LocalDateTime createdAt = now.minusDays(daysBack).minusMinutes(RNG.nextInt(1440));

            String status;
            boolean emailV;
            boolean idV;
            if (i < 500) { status = "ACTIVE"; emailV = true; idV = true; }
            else if (i < 505) { status = "PENDING"; emailV = true; idV = false; }
            else { status = "SUSPENDED"; emailV = true; idV = true; }

            users.add(new UserSeed(id, email, first, last, birth, createdAt, status, emailV, idV));
        }
        // Note: passwordHash is captured in calling scope via insertUsers
        return users;
    }

    private void insertUsers(List<UserSeed> users) {
        log.info("[SEED] Inserting {} users", users.size());
        String hash = passwordEncoder.encode(seedUserPassword);
        String sql = """
                INSERT INTO users (id, first_name, last_name, email, password, balance, birth_date,
                                   role, status, email_verified, id_card_verified, version,
                                   created_at, updated_at, failed_login_attempts)
                VALUES (?, ?, ?, ?, ?, 0, ?, 'USER', ?, ?, ?, 0, ?, ?, 0)
                """;
        jdbc.batchUpdate(sql, users, 100, (ps, u) -> {
            ps.setObject(1, u.id);
            ps.setString(2, u.firstName);
            ps.setString(3, u.lastName);
            ps.setString(4, u.email);
            ps.setString(5, hash);
            ps.setObject(6, u.birthDate);
            ps.setString(7, u.status);
            ps.setBoolean(8, u.emailVerified);
            ps.setBoolean(9, u.idCardVerified);
            ps.setTimestamp(10, Timestamp.valueOf(u.createdAt));
            ps.setTimestamp(11, Timestamp.valueOf(u.createdAt));
        });
    }

    // -------------------- Tickets --------------------

    private record TicketSeed(UUID id, UUID userId, BigDecimal stake, BigDecimal totalOdd,
                              BigDecimal potentialPayout, String status, LocalDateTime createdAt) {}
    private record SelectionSeed(UUID id, UUID ticketId, UUID matchId, UUID marketOfferId,
                                 BigDecimal oddsAtPlacement, String marketType, String selectedOption,
                                 BigDecimal line, String status) {}
    private record TicketBatch(List<TicketSeed> tickets, List<SelectionSeed> selections) {}

    private TicketBatch buildTickets(List<UserSeed> users,
                                     List<MatchRow> finished,
                                     List<MatchRow> scheduled,
                                     Map<UUID, List<OfferRow>> offersByMatch) {
        List<TicketSeed> tickets = new ArrayList<>();
        List<SelectionSeed> selections = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // filter finished matches that have offers
        List<MatchRow> finishedWithOffers = finished.stream()
                .filter(m -> offersByMatch.containsKey(m.id) && !offersByMatch.get(m.id).isEmpty())
                .toList();
        List<MatchRow> scheduledWithOffers = scheduled.stream()
                .filter(m -> offersByMatch.containsKey(m.id) && !offersByMatch.get(m.id).isEmpty())
                .toList();

        for (UserSeed user : users) {
            if (!"ACTIVE".equals(user.status)) continue;
            int ticketCount = 5 + RNG.nextInt(36); // 5-40
            for (int t = 0; t < ticketCount; t++) {
                // outcome distribution: 60% LOST, 30% WON, 5% PENDING, 5% CANCELLED
                int r = RNG.nextInt(100);
                String ticketStatus;
                boolean useFinished;
                if (r < 60) { ticketStatus = "LOST"; useFinished = true; }
                else if (r < 90) { ticketStatus = "WON"; useFinished = true; }
                else if (r < 95) { ticketStatus = "PENDING"; useFinished = false; }
                else { ticketStatus = "CANCELLED"; useFinished = true; }

                List<MatchRow> pool = useFinished ? finishedWithOffers : scheduledWithOffers;
                if (pool.isEmpty()) continue;

                // ticket createdAt: between user.createdAt and (now OR earliest selection match start)
                LocalDateTime earliestStart = useFinished ? LocalDateTime.now(ZoneOffset.UTC) : LocalDateTime.MAX;
                int selectionCount = 1 + RNG.nextInt(4); // 1-4
                List<MatchRow> chosen = new ArrayList<>(selectionCount);
                Set<UUID> chosenMatchIds = new HashSet<>();
                int attempts = 0;
                while (chosen.size() < selectionCount && attempts < selectionCount * 5) {
                    MatchRow m = pool.get(RNG.nextInt(pool.size()));
                    if (chosenMatchIds.add(m.id)) {
                        chosen.add(m);
                        if (m.startTime.isBefore(earliestStart)) earliestStart = m.startTime;
                    }
                    attempts++;
                }
                if (chosen.isEmpty()) continue;

                LocalDateTime ticketCreated = randomBetween(user.createdAt, earliestStart.minusHours(1).isBefore(user.createdAt)
                        ? earliestStart.minusMinutes(5)
                        : earliestStart.minusHours(1));

                UUID ticketId = UUID.randomUUID();
                BigDecimal stake = BigDecimal.valueOf(10 + RNG.nextInt(491)); // 10-500
                BigDecimal totalOdd = BigDecimal.ONE;

                for (MatchRow m : chosen) {
                    OfferRow offer = offersByMatch.get(m.id).get(RNG.nextInt(offersByMatch.get(m.id).size()));
                    // selection status coherent with ticket status
                    String selStatus = switch (ticketStatus) {
                        case "WON" -> "WON";
                        case "LOST" -> RNG.nextInt(chosen.size()) == 0 ? "LOST" : "WON"; // ensure at least one LOST
                        case "CANCELLED" -> "CANCELLED";
                        default -> "PENDING";
                    };
                    BigDecimal odds = offer.odds;
                    if (odds == null || odds.compareTo(BigDecimal.ONE) <= 0) {
                        odds = BigDecimal.valueOf(1.5 + RNG.nextDouble() * 3.0).setScale(4, RoundingMode.HALF_UP);
                    }
                    selections.add(new SelectionSeed(
                            UUID.randomUUID(), ticketId, m.id, offer.id,
                            odds, offer.marketType, offer.option, offer.line, selStatus
                    ));
                    totalOdd = totalOdd.multiply(odds);
                }
                // For LOST tickets: ensure exactly one LOST selection (fix if all came up WON)
                if ("LOST".equals(ticketStatus)) {
                    boolean anyLost = selections.stream()
                            .filter(s -> s.ticketId.equals(ticketId))
                            .anyMatch(s -> "LOST".equals(s.status));
                    if (!anyLost && !selections.isEmpty()) {
                        // mark last selection as LOST
                        SelectionSeed last = selections.get(selections.size() - 1);
                        selections.set(selections.size() - 1, new SelectionSeed(
                                last.id, last.ticketId, last.matchId, last.marketOfferId,
                                last.oddsAtPlacement, last.marketType, last.selectedOption,
                                last.line, "LOST"
                        ));
                    }
                }

                totalOdd = totalOdd.setScale(4, RoundingMode.HALF_UP);
                BigDecimal potentialPayout = stake.multiply(totalOdd)
                        .setScale(2, RoundingMode.HALF_UP)
                        .min(new BigDecimal("200000.00"));

                tickets.add(new TicketSeed(
                        ticketId, user.id, stake, totalOdd, potentialPayout,
                        ticketStatus, ticketCreated
                ));
            }
        }
        return new TicketBatch(tickets, selections);
    }

    private void insertTickets(TicketBatch batch) {
        log.info("[SEED] Inserting {} tickets, {} selections", batch.tickets.size(), batch.selections.size());
        String ticketSql = """
                INSERT INTO tickets (id, user_id, stake, total_odd, potential_payout, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(ticketSql, batch.tickets, 200, (ps, t) -> {
            ps.setObject(1, t.id);
            ps.setObject(2, t.userId);
            ps.setBigDecimal(3, t.stake);
            ps.setBigDecimal(4, t.totalOdd);
            ps.setBigDecimal(5, t.potentialPayout);
            ps.setString(6, t.status);
            ps.setTimestamp(7, Timestamp.valueOf(t.createdAt));
            ps.setTimestamp(8, Timestamp.valueOf(t.createdAt));
        });

        String selectionSql = """
                INSERT INTO ticket_selections (id, ticket_id, match_id, market_offer_id,
                                               odds_at_placement, market_type, selected_option, line, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(selectionSql, batch.selections, 500, (ps, s) -> {
            ps.setObject(1, s.id);
            ps.setObject(2, s.ticketId);
            ps.setObject(3, s.matchId);
            ps.setObject(4, s.marketOfferId);
            ps.setBigDecimal(5, s.oddsAtPlacement);
            ps.setString(6, s.marketType);
            ps.setString(7, s.selectedOption);
            if (s.line == null) ps.setNull(8, Types.NUMERIC);
            else ps.setBigDecimal(8, s.line);
            ps.setString(9, s.status);
        });
    }

    // -------------------- Transactions --------------------

    private record TxnSeed(UUID id, UUID userId, BigDecimal amount, String type, String status,
                           UUID referenceId, String referenceType, LocalDateTime createdAt) {}

    private List<TxnSeed> buildTransactions(List<UserSeed> users, TicketBatch ticketBatch) {
        List<TxnSeed> txns = new ArrayList<>();
        Map<UUID, List<TicketSeed>> ticketsByUser = new HashMap<>();
        for (TicketSeed t : ticketBatch.tickets) {
            ticketsByUser.computeIfAbsent(t.userId, k -> new ArrayList<>()).add(t);
        }

        for (UserSeed user : users) {
            if (!"ACTIVE".equals(user.status)) continue;

            // 2-6 deposits, distributed between user.createdAt and now
            int depositCount = 2 + RNG.nextInt(5);
            for (int d = 0; d < depositCount; d++) {
                LocalDateTime depositAt = randomBetween(user.createdAt, LocalDateTime.now(ZoneOffset.UTC));
                BigDecimal amount = BigDecimal.valueOf(100 + RNG.nextInt(4901)); // 100-5000
                String status = RNG.nextInt(100) < 95 ? "COMPLETED" : "PENDING";
                txns.add(new TxnSeed(UUID.randomUUID(), user.id, amount, "DEPOSIT", status,
                        null, null, depositAt));
            }

            // BET + settlement transactions for each ticket
            List<TicketSeed> userTickets = ticketsByUser.getOrDefault(user.id, List.of());
            for (TicketSeed t : userTickets) {
                // BET transaction at ticket created time
                txns.add(new TxnSeed(UUID.randomUUID(), user.id, t.stake, "BET", "COMPLETED",
                        t.id, "TICKET", t.createdAt));

                // settlement transactions
                if ("WON".equals(t.status)) {
                    LocalDateTime settledAt = t.createdAt.plusDays(1 + RNG.nextInt(5));
                    txns.add(new TxnSeed(UUID.randomUUID(), user.id, t.potentialPayout, "PAYOUT", "COMPLETED",
                            t.id, "TICKET", settledAt));
                } else if ("CANCELLED".equals(t.status)) {
                    LocalDateTime refundedAt = t.createdAt.plusHours(1 + RNG.nextInt(48));
                    txns.add(new TxnSeed(UUID.randomUUID(), user.id, t.stake, "REFUND", "COMPLETED",
                            t.id, "TICKET", refundedAt));
                }
            }

            // 0-2 withdrawals (mostly users with winnings)
            int withdrawalCount = RNG.nextInt(3);
            for (int w = 0; w < withdrawalCount; w++) {
                LocalDateTime withdrawAt = randomBetween(user.createdAt.plusDays(7), LocalDateTime.now(ZoneOffset.UTC));
                BigDecimal amount = BigDecimal.valueOf(50 + RNG.nextInt(951)); // 50-1000
                String status = RNG.nextInt(100) < 90 ? "COMPLETED" : "PENDING";
                txns.add(new TxnSeed(UUID.randomUUID(), user.id, amount, "WITHDRAWAL", status,
                        null, null, withdrawAt));
            }
        }
        return txns;
    }

    private void insertTransactions(List<TxnSeed> txns) {
        log.info("[SEED] Inserting {} transactions", txns.size());
        String sql = """
                INSERT INTO transactions (id, user_id, amount, transaction_type, status,
                                          reference_id, reference_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, txns, 500, (ps, t) -> {
            ps.setObject(1, t.id);
            ps.setObject(2, t.userId);
            ps.setBigDecimal(3, t.amount);
            ps.setString(4, t.type);
            ps.setString(5, t.status);
            if (t.referenceId == null) ps.setNull(6, Types.OTHER);
            else ps.setObject(6, t.referenceId);
            if (t.referenceType == null) ps.setNull(7, Types.VARCHAR);
            else ps.setString(7, t.referenceType);
            ps.setTimestamp(8, Timestamp.valueOf(t.createdAt));
            ps.setTimestamp(9, Timestamp.valueOf(t.createdAt));
        });
    }

    // -------------------- Balance recomputation --------------------

    private void updateBalances(List<UserSeed> users, List<TxnSeed> txns) {
        log.info("[SEED] Recomputing user balances");
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (TxnSeed t : txns) {
            if (!"COMPLETED".equals(t.status)) continue;
            BigDecimal current = balances.getOrDefault(t.userId, BigDecimal.ZERO);
            BigDecimal delta = switch (t.type) {
                case "DEPOSIT", "PAYOUT", "REFUND" -> t.amount;
                case "BET", "WITHDRAWAL" -> t.amount.negate();
                default -> BigDecimal.ZERO;
            };
            balances.put(t.userId, current.add(delta));
        }

        String sql = "UPDATE users SET balance = ? WHERE id = ?";
        List<Map.Entry<UUID, BigDecimal>> entries = new ArrayList<>(balances.entrySet());
        jdbc.batchUpdate(sql, entries, 200, (ps, e) -> {
            BigDecimal bal = e.getValue().max(BigDecimal.ZERO); // never negative
            ps.setBigDecimal(1, bal.setScale(2, RoundingMode.HALF_UP));
            ps.setObject(2, e.getKey());
        });
    }

    // -------------------- Helpers --------------------

    private LocalDateTime randomBetween(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) return start;
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (minutes <= 0) return start;
        return start.plusMinutes(RNG.nextLong(minutes));
    }
}
