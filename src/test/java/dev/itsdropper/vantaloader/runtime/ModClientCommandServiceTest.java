package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ClientCommand;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModClientCommandServiceTest {
    @Test
    void parsesQuotedAndEscapedArgumentsAndConsumesOnlyRegisteredCommands() {
        ModClientCommandService service = new ModClientCommandService(true);
        var scope = service.createScope("examplemod");
        List<List<String>> received = new ArrayList<>();
        scope.register(new ClientCommand("echo", "Echo args", "echo <args...>",
                command -> received.add(command.arguments())));
        List<String> feedback = new ArrayList<>();

        assertTrue(service.dispatch("/echo hello \"two words\" three\\ four", feedback::add));
        assertEquals(List.of(List.of("hello", "two words", "three four")), received);
        assertFalse(service.dispatch("/say hello", feedback::add));
        assertEquals(List.of(), feedback);

        scope.close();
        assertFalse(service.dispatch("/echo hello", feedback::add));
        service.close();
    }

    @Test
    void malformedInputIsConsumedWithUsefulFeedback() {
        ModClientCommandService service = new ModClientCommandService(true);
        var scope = service.createScope("examplemod");
        scope.register(new ClientCommand("echo", "Echo args", "echo <text>",
                command -> fail("Malformed command must not execute")));
        List<String> feedback = new ArrayList<>();

        assertTrue(service.dispatch("/echo \"unterminated", feedback::add));
        assertEquals(2, feedback.size());
        assertTrue(feedback.get(0).contains("unterminated quote"));
        assertTrue(feedback.get(1).contains("/echo <text>"));
        scope.close();
        service.close();
    }

    @Test
    void rejectsGlobalNameCollisionsAndClosesRegistrationsWithTheirOwner() {
        ModClientCommandService service = new ModClientCommandService(true);
        var first = service.createScope("firstmod");
        var second = service.createScope("secondmod");
        var registration = first.register(new ClientCommand("reload", "Reload", "reload",
                command -> { }));
        assertThrows(IllegalStateException.class, () -> second.register(
                new ClientCommand("reload", "Duplicate", "reload", command -> { })));
        assertTrue(first.find("reload").isPresent());

        first.close();
        assertTrue(registration.isClosed());
        var replacement = second.register(new ClientCommand("reload", "Reload", "reload",
                command -> { }));
        assertFalse(replacement.isClosed());
        second.close();
        service.close();
    }

    @Test
    void capabilityGateRejectsRegistrationWhenAdapterDoesNotSupportCommands() {
        ModClientCommandService service = new ModClientCommandService(false);
        var scope = service.createScope("examplemod");
        assertFalse(scope.isAvailable());
        assertTrue(scope.find("echo").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> scope.register(
                new ClientCommand("echo", "Echo", "echo", command -> { })));
        assertFalse(service.dispatch("/echo", ignored -> fail("Unavailable commands must not dispatch")));
        scope.close();
        service.close();
    }

    @Test
    void callbackFailuresAreReportedAndDoNotEscapeIntoMinecraft() {
        ModClientCommandService service = new ModClientCommandService(true);
        var scope = service.createScope("examplemod");
        scope.register(new ClientCommand("broken", "Broken", "broken",
                command -> { throw new IllegalStateException("test failure"); }));
        List<String> feedback = new ArrayList<>();
        assertTrue(service.dispatch("/broken", feedback::add));
        assertEquals(1, feedback.size());
        assertTrue(feedback.get(0).contains("test failure"));
        scope.close();
        service.close();
    }
}
