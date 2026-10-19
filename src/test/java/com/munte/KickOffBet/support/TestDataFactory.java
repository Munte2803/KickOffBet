package com.munte.KickOffBet.support;

import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Seeds and cleans data for integration tests directly through the repositories.
 * Everything is persisted in committed transactions, exactly as the app would see it over HTTP.
 */
@TestComponent
public class TestDataFactory {

    public static final String DEFAULT_PASSWORD = "Password1";

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private UserRepository userRepository;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired @Qualifier("threadPoolTaskExecutor") private Executor asyncExecutor;

    public static int next() {
        return SEQ.incrementAndGet();
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------ cleanup / async

    /** Waits for in-flight async listeners, then truncates every table. */
    public void cleanDatabase() {
        awaitAsyncIdle();
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        if (tables.isEmpty()) return;
        // The suite normally runs on H2, but can be pointed at a real PostgreSQL (see README)
        boolean postgres = Boolean.TRUE.equals(jdbc.execute((java.sql.Connection c) ->
                c.getMetaData().getDatabaseProductName().equalsIgnoreCase("PostgreSQL")));
        if (postgres) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList()) + " CASCADE");
            return;
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            tables.forEach(t -> jdbc.execute("TRUNCATE TABLE \"" + t + "\""));
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /** Blocks until the shared async executor has no running or queued tasks. */
    public void awaitAsyncIdle() {
        if (asyncExecutor instanceof ThreadPoolTaskExecutor pool) {
            await(Duration.ofSeconds(20), "async executor to become idle", () ->
                    pool.getActiveCount() == 0 && pool.getThreadPoolExecutor().getQueue().isEmpty());
        }
    }

    /** Small polling helper for effects produced by @Async / @TransactionalEventListener code. */
    public static void await(Duration timeout, String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out after " + timeout + " waiting for " + description);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description, e);
            }
        }
    }

    public <T> T inTx(java.util.function.Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    // ------------------------------------------------------------------ users

    public User user(String email) {
        return createUser(email, UserRole.USER, UserStatus.ACTIVE, BigDecimal.ZERO);
    }

    public User userWithBalance(String email, String balance) {
        return createUser(email, UserRole.USER, UserStatus.ACTIVE, new BigDecimal(balance));
    }

    public User admin(String email) {
        return createUser(email, UserRole.ADMIN, UserStatus.ACTIVE, BigDecimal.ZERO);
    }

    public User createUser(String email, UserRole role, UserStatus status, BigDecimal balance) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User" + next());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setBalance(balance);
        user.setRole(role);
        user.setStatus(status);
        boolean verified = status == UserStatus.ACTIVE;
        user.setEmailVerified(verified);
        user.setIdCardVerified(verified);
        user.setIdCardUrl("http://minio.test/id-cards/" + UUID.randomUUID() + ".png");
        return userRepository.save(user);
    }

    public User reloadUser(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }

    public BigDecimal balanceOf(UUID userId) {
        return reloadUser(userId).getBalance();
    }

    public Transaction transaction(User user, TransactionType type, TransactionStatus status, String amount) {
        Transaction t = new Transaction();
        t.setUser(user);
        t.setTransactionType(type);
        t.setStatus(status);
        t.setAmount(new BigDecimal(amount));
        return transactionRepository.save(t);
    }

    // ------------------------------------------------------------------ catalogue

    public League league(String code, String name) {
        League league = new League();
        league.setCode(code);
        league.setName(name);
        league.setActive(true);
        league.setEmblemUrl("http://minio.test/leagues/" + code + ".png");
        return leagueRepository.save(league);
    }

    public Team team(String name, League... leagues) {
        return team(name, null, leagues);
    }

    public Team team(String name, Long externalId, League... leagues) {
        Team team = new Team();
        team.setName(name);
        team.setShortName(name);
        team.setTla(name.length() > 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase());
        team.setCrestUrl("http://minio.test/teams/" + name + ".png");
        team.setActive(true);
        team.setExternalId(externalId);
        for (League l : leagues) {
            team.getLeagues().add(l);
        }
        return teamRepository.save(team);
    }

    /** A scheduled, active match starting in the future with a full 1X2 + O/U 2.5 + BTTS offer set. */
    public Match scheduledMatch(League league, Team home, Team away) {
        return match(league, home, away, nowUtc().plusDays(2), MatchStatus.SCHEDULED);
    }

    public Match match(League league, Team home, Team away, LocalDateTime start, MatchStatus status) {
        Match match = new Match();
        match.setLeague(league);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setStartTime(start);
        match.setStatus(status);
        match.setActive(true);
        match.setManualUpdate(true);
        match.addOffer(offer(MarketType.H2H, BetOption.HOME, null, "2.00"));
        match.addOffer(offer(MarketType.H2H, BetOption.DRAW, null, "3.20"));
        match.addOffer(offer(MarketType.H2H, BetOption.AWAY, null, "3.50"));
        match.addOffer(offer(MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, null, "1.30"));
        match.addOffer(offer(MarketType.OVER_UNDER, BetOption.OVER, new BigDecimal("2.5"), "1.90"));
        match.addOffer(offer(MarketType.OVER_UNDER, BetOption.UNDER, new BigDecimal("2.5"), "1.85"));
        match.addOffer(offer(MarketType.BTTS, BetOption.YES, null, "1.75"));
        match.addOffer(offer(MarketType.BTTS, BetOption.NO, null, "2.05"));
        return matchRepository.save(match);
    }

    public Match finishedMatch(League league, Team home, Team away, LocalDateTime start, int ftHome, int ftAway) {
        Match m = match(league, home, away, start, MatchStatus.FINISHED);
        m.setFtHome(ftHome);
        m.setFtAway(ftAway);
        return matchRepository.save(m);
    }

    private MarketOffer offer(MarketType type, BetOption option, BigDecimal line, String odds) {
        MarketOffer o = new MarketOffer();
        o.setMarketType(type);
        o.setOption(option);
        o.setLine(line);
        o.setOdds(new BigDecimal(odds));
        o.setActive(true);
        return o;
    }

    /** Re-reads a match with its offers (the repository's findById uses an entity graph). */
    public Match reloadMatch(UUID id) {
        return matchRepository.findById(id).orElseThrow();
    }

    public MarketOffer offerOf(Match match, BetOption option) {
        return reloadMatch(match.getId()).getAvailableOffers().stream()
                .filter(o -> o.getOption() == option)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + option + " offer on match " + match.getId()));
    }
}
