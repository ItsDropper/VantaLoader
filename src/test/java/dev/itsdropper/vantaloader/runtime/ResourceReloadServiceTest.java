package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ResourceReloadPlan;
import dev.itsdropper.vantaloader.api.ResourceReloadReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResourceReloadServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void commitsPreparedResourcesAfterFilesystemChanges() throws Exception {
        Path game = temporaryDirectory.resolve("game");
        Path resources = game.resolve("config/vantaloader/example/resources");
        AtomicBoolean committed = new AtomicBoolean();
        try (ResourceReloadService service = new ResourceReloadService(game)) {
            service.register("example", "translations", resources, context -> {
                assertEquals(resources, context.directory());
                assertTrue(context.changedPaths().stream().anyMatch(path -> path.endsWith("en_us.json")));
                return new ResourceReloadPlan() {
                    @Override public void commit() { committed.set(true); }
                    @Override public void rollback() { committed.set(false); }
                };
            });
            Files.writeString(resources.resolve("en_us.json"), "{\"hello\":\"world\"}");
            ResourceReloadReport report = awaitReport(service, 1);
            assertTrue(committed.get());
            assertEquals(ResourceReloadReport.Status.RELOADED, report.entries().get(0).status());
        }
    }

    @Test
    void preparationFailureRollsBackPreviouslyPreparedRegistrations() throws Exception {
        Path game = temporaryDirectory.resolve("prepare-failure-game");
        Path resources = game.resolve("config/vantaloader/shared");
        AtomicBoolean rolledBack = new AtomicBoolean();
        try (ResourceReloadService service = new ResourceReloadService(game)) {
            service.register("alpha", "first", resources, context -> new ResourceReloadPlan() {
                @Override public void commit() { fail("commit must not run when preparation fails"); }
                @Override public void rollback() { rolledBack.set(true); }
            });
            service.register("beta", "second", resources, context -> {
                throw new IllegalStateException("invalid replacement data");
            });
            Files.writeString(resources.resolve("changed.json"), "{}");
            ResourceReloadReport report = awaitReport(service, 2);
            assertTrue(rolledBack.get());
            assertTrue(report.entries().stream().allMatch(e -> e.status() == ResourceReloadReport.Status.FAILED));
            assertTrue(report.entries().stream().allMatch(e -> e.message().contains("rolled back")));
        }
    }

    @Test
    void commitFailureRollsBackEveryPreparedRegistrationInReverseOrder() throws Exception {
        Path game = temporaryDirectory.resolve("commit-failure-game");
        Path resources = game.resolve("config/vantaloader/shared");
        List<String> calls = new ArrayList<>();
        try (ResourceReloadService service = new ResourceReloadService(game)) {
            service.register("alpha", "first", resources, context -> new ResourceReloadPlan() {
                @Override public void commit() { calls.add("commit-alpha"); }
                @Override public void rollback() { calls.add("rollback-alpha"); }
            });
            service.register("beta", "second", resources, context -> new ResourceReloadPlan() {
                @Override public void commit() { calls.add("commit-beta"); throw new IllegalStateException("commit failed"); }
                @Override public void rollback() { calls.add("rollback-beta"); }
            });
            Files.writeString(resources.resolve("changed.json"), "{}");
            ResourceReloadReport report = awaitReport(service, 2);
            assertEquals(List.of("commit-alpha", "commit-beta", "rollback-beta", "rollback-alpha"), calls);
            assertTrue(report.entries().stream().allMatch(e -> e.status() == ResourceReloadReport.Status.FAILED));
        }
    }

    @Test
    void modJarChangesAreReportedAsRestartRequired() throws Exception {
        Path game = temporaryDirectory.resolve("jar-change-game");
        try (ResourceReloadService service = new ResourceReloadService(game)) {
            Path jar = game.resolve("mods/example.jar");
            Files.writeString(jar, "replacement");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
            while (service.report().restartRequired().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(List.of(jar.toAbsolutePath().normalize()), service.report().restartRequired());
        }
    }

    @Test
    void closingSubscriptionPreventsFutureReloadCallbacks() throws Exception {
        Path game = temporaryDirectory.resolve("closed-subscription-game");
        Path resources = game.resolve("config/vantaloader/example/resources");
        AtomicBoolean called = new AtomicBoolean();
        try (ResourceReloadService service = new ResourceReloadService(game)) {
            var subscription = service.register("example", "closed", resources, context -> {
                called.set(true);
                return new ResourceReloadPlan() {
                    @Override public void commit() { }
                    @Override public void rollback() { }
                };
            });
            subscription.close();
            Files.writeString(resources.resolve("ignored.json"), "{}");
            Thread.sleep(250);
            assertFalse(called.get());
            assertTrue(service.report().entries().isEmpty());
        }
    }

    private static ResourceReloadReport awaitReport(ResourceReloadService service, int count) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
        ResourceReloadReport report;
        do {
            report = service.report();
            if (report.entries().size() >= count) return report;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for " + count + " reload report entries; got " + report.entries());
        throw new AssertionError("unreachable");
    }
}
