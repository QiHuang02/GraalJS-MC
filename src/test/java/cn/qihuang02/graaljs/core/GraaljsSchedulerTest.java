package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.binding.BindingsBuilder;
import cn.qihuang02.graaljs.binding.SchedulerAPI;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraaljsSchedulerTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldFireTimeoutAfterDelay() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("timeout.js", """
                fired = false;
                scheduler.setTimeout(() => { fired = true; }, 100);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertFalse(bindings.getMember("fired").asBoolean());

        factory.tickScheduler(ScriptType.STARTUP, 50);
        assertFalse(bindings.getMember("fired").asBoolean());

        factory.tickScheduler(ScriptType.STARTUP, 100);
        assertTrue(bindings.getMember("fired").asBoolean());
    }

    @Test
    void shouldFireTimeoutOnlyOnce() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("timeoutOnce.js", """
                count = 0;
                scheduler.setTimeout(() => { count = count + 1; }, 100);
                """);

        factory.tickScheduler(ScriptType.STARTUP, 100);
        factory.tickScheduler(ScriptType.STARTUP, 200);
        factory.tickScheduler(ScriptType.STARTUP, 300);

        assertEquals(1, context.getPolyglotContext().getBindings("js").getMember("count").asInt());
    }

    @Test
    void shouldFireIntervalRepeatedly() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("interval.js", """
                count = 0;
                scheduler.setInterval(() => { count = count + 1; }, 50);
                """);

        factory.tickScheduler(ScriptType.STARTUP, 50);
        assertEquals(1, context.getPolyglotContext().getBindings("js").getMember("count").asInt());

        factory.tickScheduler(ScriptType.STARTUP, 100);
        assertEquals(2, context.getPolyglotContext().getBindings("js").getMember("count").asInt());

        factory.tickScheduler(ScriptType.STARTUP, 150);
        assertEquals(3, context.getPolyglotContext().getBindings("js").getMember("count").asInt());
    }

    @Test
    void shouldClearTimeout() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("clearTimeout.js", """
                fired = false;
                id = scheduler.setTimeout(() => { fired = true; }, 100);
                cleared = scheduler.clearTimeout(id);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertTrue(bindings.getMember("cleared").asBoolean());

        factory.tickScheduler(ScriptType.STARTUP, 200);
        assertFalse(bindings.getMember("fired").asBoolean());
    }

    @Test
    void shouldClearInterval() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("clearInterval.js", """
                count = 0;
                id = scheduler.setInterval(() => { count = count + 1; }, 50);
                """);

        factory.tickScheduler(ScriptType.STARTUP, 50);
        assertEquals(1, context.getPolyglotContext().getBindings("js").getMember("count").asInt());

        SchedulerAPI scheduler = factory.getScheduler(ScriptType.STARTUP);
        scheduler.clearInterval(
                context.getPolyglotContext().getBindings("js").getMember("id").asInt()
        );

        factory.tickScheduler(ScriptType.STARTUP, 100);
        assertEquals(1, context.getPolyglotContext().getBindings("js").getMember("count").asInt());
    }

    @Test
    void shouldClearIntervalFromScript() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("clearIntervalScript.js", """
                count = 0;
                id = scheduler.setInterval(() => { count = count + 1; }, 50);
                """);

        factory.tickScheduler(ScriptType.STARTUP, 50);

        context.eval("doClear.js", """
                scheduler.clearInterval(id);
                """);

        factory.tickScheduler(ScriptType.STARTUP, 100);
        assertEquals(1, context.getPolyglotContext().getBindings("js").getMember("count").asInt());
    }

    @Test
    void shouldReturnFalseWhenClearingNonexistentId() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("clearNonexistent.js", """
                result = scheduler.clearTimeout(9999);
                """);

        assertFalse(context.getPolyglotContext().getBindings("js").getMember("result").asBoolean());
    }

    @Test
    void shouldRejectNonExecutableCallback() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        assertThrows(Exception.class, () ->
                context.eval("badCallback.js", "scheduler.setTimeout('not a function', 100);")
        );
    }

    @Test
    void shouldRejectNonPositiveInterval() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        assertThrows(Exception.class, () ->
                context.eval("badInterval.js", "scheduler.setInterval(() => {}, 0);")
        );
    }

    @Test
    void shouldHandleZeroDelayTimeout() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("zeroDelay.js", """
                fired = false;
                scheduler.setTimeout(() => { fired = true; }, 0);
                """);

        factory.tickScheduler(ScriptType.STARTUP, 0);
        assertTrue(context.getPolyglotContext().getBindings("js").getMember("fired").asBoolean());
    }

    @Test
    void shouldReportCorrectSize() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("size.js", """
                id1 = scheduler.setTimeout(() => {}, 100);
                id2 = scheduler.setInterval(() => {}, 50);
                sizeAfterAdd = scheduler.size();
                scheduler.clearTimeout(id1);
                sizeAfterClear = scheduler.size();
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(2, bindings.getMember("sizeAfterAdd").asInt());
        assertEquals(1, bindings.getMember("sizeAfterClear").asInt());
    }

    @Test
    void shouldCleanupOnContextClose() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("cleanup.js", """
                scheduler.setTimeout(() => {}, 100);
                scheduler.setInterval(() => {}, 50);
                """);

        SchedulerAPI scheduler = factory.getScheduler(ScriptType.STARTUP);
        assertEquals(2, scheduler.size());

        factory.close(ScriptType.STARTUP);
        // After close, the scheduler for this type should be removed
        assertTrue(factory.getScheduler(ScriptType.STARTUP) == null);
    }

    @Test
    void shouldTickSchedulerViaFactory() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.SERVER);

        context.eval("factoryTick.js", """
                count = 0;
                scheduler.setTimeout(() => { count = count + 1; }, 100);
                scheduler.setInterval(() => { count = count + 10; }, 200);
                """);

        assertEquals(0, factory.tickScheduler(ScriptType.SERVER, 50));
        assertEquals(1, factory.tickScheduler(ScriptType.SERVER, 100));
        assertEquals(1, factory.tickScheduler(ScriptType.SERVER, 200));
        assertEquals(11, context.getPolyglotContext().getBindings("js").getMember("count").asInt());
    }

    @Test
    void shouldReturnZeroWhenTickingNonexistentContext() {
        TestFactory factory = new TestFactory(tempDir);
        assertEquals(0, factory.tickScheduler(ScriptType.CLIENT, 100));
    }

    @Test
    void shouldNotFireEarlyWhenRegisteredLate() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // Advance time to 5000ms first
        factory.tickScheduler(ScriptType.STARTUP, 5000);

        // Register setTimeout at currentTimeMs=5000, delay=100 → should fire at 5100
        context.eval("lateTimeout.js", """
                fired = false;
                scheduler.setTimeout(() => { fired = true; }, 100);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");

        // tick(5050) should NOT fire (5050 < 5100)
        factory.tickScheduler(ScriptType.STARTUP, 5050);
        assertFalse(bindings.getMember("fired").asBoolean());

        // tick(5100) should fire (5100 >= 5100)
        factory.tickScheduler(ScriptType.STARTUP, 5100);
        assertTrue(bindings.getMember("fired").asBoolean());
    }

    @Test
    void shouldFireIntervalCorrectlyAfterLateRegistration() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // Advance time to 5000ms first
        factory.tickScheduler(ScriptType.STARTUP, 5000);

        // Register setInterval at currentTimeMs=5000, interval=100 → first fire at 5100
        context.eval("lateInterval.js", """
                count = 0;
                scheduler.setInterval(() => { count = count + 1; }, 100);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");

        // tick(5050) should NOT fire
        factory.tickScheduler(ScriptType.STARTUP, 5050);
        assertEquals(0, bindings.getMember("count").asInt());

        // tick(5100) should fire once
        factory.tickScheduler(ScriptType.STARTUP, 5100);
        assertEquals(1, bindings.getMember("count").asInt());

        // tick(5200) should fire again
        factory.tickScheduler(ScriptType.STARTUP, 5200);
        assertEquals(2, bindings.getMember("count").asInt());
    }

    private static class TestFactory extends GraaljsContextFactory {
        private TestFactory(Path scriptRoot) {
            super(scriptRoot);
        }

        @Override
        protected void configureBindings(ScriptType type, BindingsBuilder builder) {
            super.configureBindings(type, builder);
        }
    }
}
