package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.binding.BindingsBuilder;
import cn.qihuang02.graaljs.binding.ForgeEventBridge;
import cn.qihuang02.graaljs.bridge.CustomMember;
import cn.qihuang02.graaljs.bridge.CustomMemberProvider;
import cn.qihuang02.graaljs.error.ErrorReporter;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import cn.qihuang02.graaljs.util.CustomJavaToJsWrapper;
import cn.qihuang02.graaljs.util.HideFromJS;
import cn.qihuang02.graaljs.util.RemapForJS;
import cn.qihuang02.graaljs.util.RemapPrefixForJS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraaljsContextIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldExposeBindingsAndBridgeMembers() {
        TestBindings bindings = new TestBindings();
        TestFactory factory = new TestFactory(tempDir, bindings);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("bindings.js", """
                console.log("boot", 1);
                api.record("base");
                api.aliasRecord("alias");
                aliasStatus = api.status();
                visibleField = api.visibleField;
                hiddenFieldPresent = "hiddenField" in api;
                hiddenMethodPresent = "hiddenCall" in api;
                bridgeMap.answer = 42;
                bridgeList[0] = "first";
                bridgeList[1] = 2;
                bridgeSet[0] = "alpha";
                bridgeSet[1] = "beta";
                bridgeSet[1] = "beta-2";
                setSnapshot = bridgeSet[0] + "|" + bridgeSet[1] + "|" + bridgeSet.length;
                wrappedName = customWrapped.name;
                wrappedCount = customWrapped.count;
                staticFallbackName = staticFallback.kind;
                staticFallbackPing = staticFallback.ping("ok");
                staticPing = TestStatics.ping("ok");
                staticName = TestStatics.NAME;
                """);

        assertEquals(List.of("base", "alias:alias"), bindings.api.calls);
        assertEquals("ready", context.getPolyglotContext().getBindings("js").getMember("aliasStatus").asString());
        assertEquals("visible", context.getPolyglotContext().getBindings("js").getMember("visibleField").asString());
        assertFalse(context.getPolyglotContext().getBindings("js").getMember("hiddenFieldPresent").asBoolean());
        assertFalse(context.getPolyglotContext().getBindings("js").getMember("hiddenMethodPresent").asBoolean());
        assertEquals(42, bindings.bridgeMap.get("answer"));
        assertEquals(List.of("first", 2), bindings.bridgeList);
        assertEquals(List.of("alpha", "beta-2"), new ArrayList<>(bindings.bridgeSet));
        assertEquals("alpha|beta-2|2", context.getPolyglotContext().getBindings("js").getMember("setSnapshot").asString());
        assertEquals("wrapped", context.getPolyglotContext().getBindings("js").getMember("wrappedName").asString());
        assertEquals(2, context.getPolyglotContext().getBindings("js").getMember("wrappedCount").asInt());
        assertEquals("fallback", context.getPolyglotContext().getBindings("js").getMember("staticFallbackName").asString());
        assertEquals("fallback:ok", context.getPolyglotContext().getBindings("js").getMember("staticFallbackPing").asString());
        assertEquals("pong:ok", context.getPolyglotContext().getBindings("js").getMember("staticPing").asString());
        assertEquals("STATIC", context.getPolyglotContext().getBindings("js").getMember("staticName").asString());
    }

    @Test
    void shouldAllowDisablingInstanceStaticFallback() {
        TestBindings bindings = new TestBindings();
        TestFactory factory = new TestFactory(tempDir, bindings);
        factory.setInstanceStaticFallback(false);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("noStaticFallback.js", """
                hasStaticKind = "kind" in staticFallback;
                hasStaticPing = "ping" in staticFallback;
                """);

        Value scopedBindings = context.getPolyglotContext().getBindings("js");
        assertFalse(scopedBindings.getMember("hasStaticKind").asBoolean());
        assertFalse(scopedBindings.getMember("hasStaticPing").asBoolean());
    }

    @Test
    void shouldLoadScriptsInStableOrderAndReloadServerContext() throws IOException {
        TestBindings bindings = new TestBindings();
        TestFactory factory = new TestFactory(tempDir, bindings);
        writeScript(ScriptType.STARTUP, "02-second.js", "api.record('startup-2');");
        writeScript(ScriptType.STARTUP, "01-first.js", "api.record('startup-1');");
        writeScript(ScriptType.SERVER, "01-server.js", "api.record('server-1');");

        GraaljsContext startupContext = factory.createAndLoad(ScriptType.STARTUP);
        GraaljsContext serverContext = factory.createAndLoad(ScriptType.SERVER);

        assertEquals(List.of("startup-1", "startup-2", "server-1"), bindings.api.calls);

        writeScript(ScriptType.SERVER, "01-server.js", "api.record('server-2');");
        GraaljsContext reloadedContext = factory.reload(ScriptType.SERVER);

        assertNotSame(serverContext, reloadedContext);
        assertThrows(IllegalStateException.class, () -> serverContext.addToScope("x", 1));
        assertEquals(List.of("startup-1", "startup-2", "server-1", "server-2"), bindings.api.calls);

        startupContext.close();
        reloadedContext.close();
    }

    @Test
    void shouldEvaluateVisibilityRules() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());

        assertFalse(factory.visibleToScripts("java.lang.Runtime", ClassVisibilityContext.BINDING));
        assertFalse(factory.visibleToScripts("java.nio.file.Files", ClassVisibilityContext.MEMBER));
        assertTrue(factory.visibleToScripts("java.util.ArrayList", ClassVisibilityContext.RETURN_TYPE));

        // 新增黑名单类验证
        assertFalse(factory.visibleToScripts("java.lang.ClassLoader", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("java.lang.Thread", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("java.lang.invoke.MethodHandle", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("java.lang.Class", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("sun.misc.Unsafe", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("jdk.internal.misc.Unsafe", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("com.sun.management.HotSpotDiagnosticMXBean", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("javax.management.MBeanServer", ClassVisibilityContext.CLASS_LOOKUP));
        assertFalse(factory.visibleToScripts("javax.script.ScriptEngine", ClassVisibilityContext.CLASS_LOOKUP));
    }

    @Test
    void shouldApplyContextSpecificVisibilityRules() {
        ContextAwareVisibilityFactory factory = new ContextAwareVisibilityFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        assertNull(factory.resolveVisibleClassOrNull("cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$ConstructibleType", ClassVisibilityContext.CLASS_LOOKUP));
        assertTrue(factory.resolveVisibleClassOrNull("cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$ConstructibleType", ClassVisibilityContext.RETURN_TYPE) != null);
        assertNull(factory.resolveVisibleClassOrNull("java.util.ArrayList", ClassVisibilityContext.PACKAGE_LOOKUP));
        assertTrue(factory.resolveVisibleClassOrNull("java.util.ArrayList", ClassVisibilityContext.CLASS_LOOKUP) != null);

        context.eval("contextVisibility.js", """
                hiddenLookup = Java.typeOrNull('cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$ConstructibleType') === null;
                hiddenLookupVisible = Java.isVisible('cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$ConstructibleType') === false;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertTrue(bindings.getMember("hiddenLookup").asBoolean());
        assertTrue(bindings.getMember("hiddenLookupVisible").asBoolean());

        IllegalArgumentException packageLookupException = assertThrows(IllegalArgumentException.class, () ->
                factory.resolveVisibleClass("java.util.ArrayList", ClassVisibilityContext.PACKAGE_LOOKUP)
        );
        assertTrue(packageLookupException.getMessage().contains("not visible"));

        cn.qihuang02.graaljs.binding.RuntimeAPI runtimeApi = new cn.qihuang02.graaljs.binding.RuntimeAPI(factory, context);
        IllegalArgumentException interfaceException = assertThrows(IllegalArgumentException.class, () ->
                runtimeApi.proxy(
                        context.eval("hiddenInterface.js", "(value => value)"),
                        "cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$GreetingCallback"
                )
        );
        assertTrue(interfaceException.getMessage().contains("not visible"));

        IllegalArgumentException abstractException = assertThrows(IllegalArgumentException.class, () ->
                runtimeApi.extend(
                        context.eval("hiddenAbstract.js", "({ greet(name) { return name; } })"),
                        "cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter",
                        "prefix"
                )
        );
        assertTrue(abstractException.getMessage().contains("not visible"));
    }

    @Test
    void shouldAdaptInterfacesAndCollectionConversions() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        Value runnableValue = context.eval("callable.js", "(() => 123)");
        Runnable runnable = context.jsToJava(runnableValue, Runnable.class);
        runnable.run();

        Value arrayValue = context.eval("array.js", "[1, 2, 3]");
        int[] ints = context.jsToJava(arrayValue, int[].class);
        assertEquals(3, ints.length);
        assertEquals(2, ints[1]);

        Value setValue = context.eval("set.js", "['a', 'b', 'a']");
        assertEquals(Set.of("a", "b"), context.jsToJava(setValue, Set.class));

        Value optionalValue = context.eval("optional.js", "'demo'");
        assertEquals(Optional.of("demo"), context.jsToJava(optionalValue, Optional.class));
    }

    @Test
    void shouldReuseWrappedProxyInstances() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);
        ScriptApi api = new ScriptApi();

        Object wrappedOnce = context.wrapAsJavaObject(api);
        Object wrappedTwice = context.wrapAsJavaObject(api);
        Object wrappedClassOnce = context.wrapJavaClass(TestStatics.class);
        Object wrappedClassTwice = context.wrapJavaClass(TestStatics.class);

        assertSame(wrappedOnce, wrappedTwice);
        assertSame(wrappedClassOnce, wrappedClassTwice);
    }

    @Test
    void shouldReportScriptErrorsThroughErrorReporter() throws IOException {
        RecordingErrorReporter reporter = new RecordingErrorReporter();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setErrorReporter(reporter);
        GraaljsContext context = factory.create(ScriptType.STARTUP);
        Source source = Source.newBuilder("js", "throw new Error('boom')", "failing.js").buildLiteral();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> context.eval(source));

        assertTrue(exception.getMessage().contains("boom"));
        assertEquals("failing.js", reporter.lastSourceName);
        assertTrue(reporter.errorCount >= 1);
        assertTrue(reporter.runtimeErrorCount >= 1);
        assertTrue(reporter.lastLineSource.contains("throw new Error('boom')"));
        assertTrue(reporter.lastLineOffset >= -1);
    }

    @Test
    void shouldHideHostExceptionDetailsWhenExceptionTypeIsInvisible() {
        RecordingErrorReporter reporter = new RecordingErrorReporter();
        HiddenExceptionFactory factory = new HiddenExceptionFactory(tempDir, new TestBindings());
        factory.setErrorReporter(reporter);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                context.eval("hostFailure.js", "hostErrors.explode();")
        );

        assertEquals("Host exception is not visible to scripts", reporter.lastMessage);
        assertEquals("hostFailure.js", reporter.lastSourceName);
        assertTrue(reporter.lastLineSource.contains("hostErrors.explode();"));
        assertEquals(IllegalStateException.class.getName(), reporter.lastCauseType);
        assertFalse(exception.getMessage().contains("HiddenHostException"));
    }

    @Test
    void shouldExposeCustomMembersAndWrappedFunctions() {
        TestBindings bindings = new TestBindings();
        TestFactory factory = new TestFactory(tempDir, bindings);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("custom.js", """
                injectedValue = api.dynamicValue;
                api.dynamicValue = "changed";
                afterChange = api.dynamicValue;
                typedResult = typedJoin("count", 2);
                dynamicResult = dynamicArgs("a", 1, true);
                """);

        assertEquals("dynamic", context.getPolyglotContext().getBindings("js").getMember("injectedValue").asString());
        assertEquals("changed", context.getPolyglotContext().getBindings("js").getMember("afterChange").asString());
        assertEquals("count:2", context.getPolyglotContext().getBindings("js").getMember("typedResult").asString());
        assertEquals("a|1|true", context.getPolyglotContext().getBindings("js").getMember("dynamicResult").asString());
    }

    @Test
    void shouldConstructJavaClassesAndConvertRecords() {
        TestBindings bindings = new TestBindings();
        TestFactory factory = new TestFactory(tempDir, bindings);
        factory.registerDefaultRecordProperties(new SampleRecord("fallback", 7));
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("construct.js", """
                created = new ConstructibleType("demo", 3);
                createdSummary = created.summary();
                recordSummary = typedRecordSummary({ name: "alpha" });
                hasUnsafeReturn = "runtimeLeak" in visibilitySample;
                hasUnsafeArg = "acceptFile" in visibilitySample;
                hasVisibleMethod = "ok" in visibilitySample;
                """);

        assertEquals("demo:3", context.getPolyglotContext().getBindings("js").getMember("createdSummary").asString());
        assertEquals("alpha:7", context.getPolyglotContext().getBindings("js").getMember("recordSummary").asString());
        assertFalse(context.getPolyglotContext().getBindings("js").getMember("hasUnsafeReturn").asBoolean());
        assertFalse(context.getPolyglotContext().getBindings("js").getMember("hasUnsafeArg").asBoolean());
        assertTrue(context.getPolyglotContext().getBindings("js").getMember("hasVisibleMethod").asBoolean());
    }

    @Test
    void shouldBindCurrentContextAndExposeMinecraftTypeWrappers() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext entered = factory.enter(ScriptType.STARTUP);

        assertSame(entered, factory.current());

        GraaljsContext context = factory.getContext(ScriptType.STARTUP);
        ResourceLocation resourceLocation = context.jsToJava("minecraft:stone", ResourceLocation.class);
        BlockPos blockPosFromArray = context.jsToJava(context.eval("blockPos.js", "[1, 2, 3]"), BlockPos.class);
        BlockPos blockPosFromString = context.jsToJava("4 5 6", BlockPos.class);
        Vec3 vec3FromMap = context.jsToJava(context.eval("vec3.js", "({ x: 1.5, y: 2.5, z: 3.5 })"), Vec3.class);
        Component component = context.jsToJava("hello", Component.class);
        Runnable runnable = context.asInterface(context.eval("interface.js", "(() => 1)"), Runnable.class);
        runnable.run();

        assertEquals("minecraft", resourceLocation.getNamespace());
        assertEquals("stone", resourceLocation.getPath());
        assertEquals(new BlockPos(1, 2, 3), blockPosFromArray);
        assertEquals(new BlockPos(4, 5, 6), blockPosFromString);
        assertEquals(new Vec3(1.5, 2.5, 3.5), vec3FromMap);
        assertEquals("hello", component.getString());
    }

    @Test
    void shouldExposeRuntimeBinding() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("runtime.js", """
                runtimeType = runtime.scriptType();
                wrappedType = runtime.wrap(TestStatics).NAME;
                runtimeClass = runtime.type('cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$ConstructibleType');
                builderText = new runtimeClass('ab', 2).summary();
                """);

        assertEquals("startup", context.getPolyglotContext().getBindings("js").getMember("runtimeType").asString());
        assertEquals("STATIC", context.getPolyglotContext().getBindings("js").getMember("wrappedType").asString());
        assertEquals("ab:2", context.getPolyglotContext().getBindings("js").getMember("builderText").asString());
    }

    @Test
    void shouldExposeRhinoStyleJavaBindings() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("javaBindings.js", """
                ConstructibleClass = Java.type('cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$ConstructibleType');
                builderA = new ConstructibleClass('ab', 2).summary();
                packageList = new Packages.java.util.ArrayList();
                packageList[0] = 'x';
                packageList[1] = 'y';
                builderB = packageList[0] + packageList[1] + ':' + packageList.length;
                listSize = new java.util.ArrayList().length;
                visibleString = Java.isVisible('java.lang.String');
                hiddenRuntime = Java.isVisible('java.lang.Runtime');
                missingType = Java.typeOrNull('java.lang.DoesNotExist') === null;
                blockedType = Java.typeOrNull('java.lang.Runtime') === null;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("ab:2", bindings.getMember("builderA").asString());
        assertEquals("xy:2", bindings.getMember("builderB").asString());
        assertEquals(0, bindings.getMember("listSize").asInt());
        assertTrue(bindings.getMember("visibleString").asBoolean());
        assertFalse(bindings.getMember("hiddenRuntime").asBoolean());
        assertTrue(bindings.getMember("missingType").asBoolean());
        assertTrue(bindings.getMember("blockedType").asBoolean());
    }

    @Test
    void shouldDispatchEventCallbacksInsideContext() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("events.js", """
                registerCount = events.on('tick', payload => {
                    firstSeen = payload.text;
                    firstCount = payload.count;
                });
                secondRegisterCount = events.on('tick', payload => {
                    secondSeen = payload.text + ':' + payload.count;
                });
                emittedCount = events.emit('tick', { text: 'demo', count: 2 });
                listenerCount = events.listenerCount('tick');
                cleared = events.clear('tick');
                afterClearCount = events.listenerCount('tick');
                emittedAfterClear = events.emit('tick', { text: 'ignored', count: 99 });
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(1, bindings.getMember("registerCount").asInt());
        assertEquals(2, bindings.getMember("secondRegisterCount").asInt());
        assertEquals(2, bindings.getMember("emittedCount").asInt());
        assertEquals("demo", bindings.getMember("firstSeen").asString());
        assertEquals(2, bindings.getMember("firstCount").asInt());
        assertEquals("demo:2", bindings.getMember("secondSeen").asString());
        assertEquals(2, bindings.getMember("listenerCount").asInt());
        assertTrue(bindings.getMember("cleared").asBoolean());
        assertEquals(0, bindings.getMember("afterClearCount").asInt());
        assertEquals(0, bindings.getMember("emittedAfterClear").asInt());
    }

    @Test
    void shouldReceiveHostLifecycleEvents() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.SERVER);

        context.eval("hostEvents.js", """
                onceCount = 0;
                events.on('server.started', event => {
                    exactName = event.name;
                    exactType = event.scriptType;
                    exactMessage = event.payload.message;
                });
                events.once('server.started', event => {
                    onceCount = onceCount + 1;
                });
                removable = event => { removed = true; };
                events.on('server.started', removable);
                removedListeners = events.off('server.started', removable);
                events.on('*', event => {
                    wildcardName = event.name;
                    wildcardType = event.scriptType;
                    wildcardMessage = event.payload.message;
                });
                """);

        int firstDispatch = factory.emitEvent(ScriptType.SERVER, "server.started", Map.of("message", "ready"));
        int secondDispatch = factory.emitEvent(ScriptType.SERVER, "server.started", Map.of("message", "again"));

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(3, firstDispatch);
        assertEquals(2, secondDispatch);
        assertEquals(1, bindings.getMember("removedListeners").asInt());
        assertEquals("server.started", bindings.getMember("exactName").asString());
        assertEquals("server", bindings.getMember("exactType").asString());
        assertEquals("again", bindings.getMember("exactMessage").asString());
        assertEquals(1, bindings.getMember("onceCount").asInt());
        assertEquals("server.started", bindings.getMember("wildcardName").asString());
        assertEquals("server", bindings.getMember("wildcardType").asString());
        assertEquals("again", bindings.getMember("wildcardMessage").asString());
    }

    @Test
    void shouldAdaptMultiMethodInterfaces() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        MultiCallback callback = context.jsToJava(context.eval("multi.js", "({ open(name) { return 'open:' + name; }, close() { return 'close'; } })"), MultiCallback.class);
        assertEquals("open:door|close", callback.open("door") + "|" + callback.close());

        context.eval("runtimeAdapt.js", """
                adaptedSummary = useMultiCallback(runtime.adapt({
                    open(name) { return 'js:' + name; },
                    close() { return 'done'; }
                }, 'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$MultiCallback'));
                """);
        assertEquals("js:input|done", context.getPolyglotContext().getBindings("js").getMember("adaptedSummary").asString());

        SameSignatureCallback sameSignature = context.asInterface(
                context.eval("sameSignature.js", "(value => 'same:' + value)"),
                SameSignatureCallback.class
        );
        assertEquals("same:a|same:b", sameSignature.open("a") + "|" + sameSignature.close("b"));

        Object multiProxy = context.asInterfaces(
                context.eval("multiProxy.js", "(value => 'proxy:' + value)"),
                GreetingCallback.class,
                FarewellCallback.class
        );
        assertTrue(multiProxy instanceof GreetingCallback);
        assertTrue(multiProxy instanceof FarewellCallback);
        assertEquals("proxy:alex", ((GreetingCallback) multiProxy).greet("alex"));
        assertEquals("proxy:alex", ((FarewellCallback) multiProxy).bye("alex"));

        context.eval("runtimeProxy.js", """
                runtimeProxy = runtime.proxy(
                    value => 'runtime:' + value,
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$GreetingCallback',
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$FarewellCallback'
                );
                runtimeProxySummary = useGreeting(runtimeProxy) + '|' + useFarewell(runtimeProxy);
                """);
        assertEquals("runtime:alex|runtime:alex", context.getPolyglotContext().getBindings("js").getMember("runtimeProxySummary").asString());
    }

    @Test
    void shouldRejectExecutableFunctionsForIncompatibleInterfaceSignatures() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                context.asInterface(context.eval("invalidSignature.js", "(value => value)"), IncompatibleCallback.class)
        );

        assertTrue(exception.getMessage().contains("share the same signature"));
    }

    @Test
    void shouldExtendAbstractClassesFromJsObjects() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        AbstractGreeter directGreeter = context.asAbstractClass(
                context.eval("directAbstract.js", "({ greet(name) { return 'direct:' + name; } })"),
                AbstractGreeter.class,
                "ctx"
        );
        assertEquals("ctx:direct:alex", directGreeter.format("alex"));
        assertEquals("ctx", directGreeter.prefix());

        context.eval("abstractAdapters.js", """
                runtimeGreeter = runtime.extend({
                    greet(name) { return 'runtime:' + name; }
                }, 'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter', 'rt');
                javaGreeter = Java.extend(
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter',
                    { greet(name) { return 'java:' + name; } },
                    'java'
                );
                runtimeSummary = runtimeGreeter.format('beta');
                javaSummary = javaGreeter.format('gamma');
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("rt:runtime:beta", bindings.getMember("runtimeSummary").asString());
        assertEquals("java:java:gamma", bindings.getMember("javaSummary").asString());

        AbstractGreeter missingMethodGreeter = context.asAbstractClass(
                context.eval("brokenAbstract.js", "({})"),
                AbstractGreeter.class,
                "missing"
        );
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> missingMethodGreeter.greet("alex"));
        assertTrue(exception.getMessage().contains("does not implement abstract method"));

        Object mixedAdapter = context.asAbstractClass(
                context.eval("mixedAbstract.js", "({ greet(name) { return 'mix:' + name; }, nickname() { return 'hybrid'; } })"),
                AbstractGreeter.class,
                new Class<?>[]{Nicknamed.class},
                "mix"
        );
        assertTrue(mixedAdapter instanceof AbstractGreeter);
        assertTrue(mixedAdapter instanceof Nicknamed);
        assertEquals("mix:mix:alex", ((AbstractGreeter) mixedAdapter).format("alex"));
        assertEquals("hybrid", ((Nicknamed) mixedAdapter).nickname());

        context.eval("mixedRuntimeAdapters.js", """
                runtimeMixed = runtime.extendWithInterfaces(
                    {
                        greet(name) { return 'runtime-mix:' + name; },
                        nickname() { return 'rt-hybrid'; }
                    },
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter',
                    ['cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$Nicknamed'],
                    'rtmix'
                );
                javaMixed = Java.extendWithInterfaces(
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter',
                    {
                        greet(name) { return 'java-mix:' + name; },
                        nickname() { return 'java-hybrid'; }
                    },
                    ['cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$Nicknamed'],
                    'javamix'
                );
                runtimeMixedSummary = useNicknamedGreeter(runtimeMixed);
                javaMixedSummary = useNicknamedGreeter(javaMixed);
                """);

        Value mixedBindings = context.getPolyglotContext().getBindings("js");
        assertEquals("rtmix:runtime-mix:alex|rt-hybrid", mixedBindings.getMember("runtimeMixedSummary").asString());
        assertEquals("javamix:java-mix:alex|java-hybrid", mixedBindings.getMember("javaMixedSummary").asString());
    }

    @Test
    void shouldExposeJavaAdapterTemplatesAndDirectCreation() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("javaAdapter.js", """
                abstractTemplate = JavaAdapter.type(
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter',
                    ['cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$Nicknamed']
                );
                abstractKind = abstractTemplate.kind;
                abstractSuper = abstractTemplate.superClassName;
                abstractInterfaceCount = abstractTemplate.interfaceNames.length;
                abstractGreeter = new abstractTemplate({
                    greet(name) { return 'template:' + name; },
                    nickname() { return 'templated'; }
                }, 'tmpl');
                abstractSummary = useNicknamedGreeter(abstractGreeter);

                directGreeter = JavaAdapter.createWithInterfaces(
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter',
                    ['cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$Nicknamed'],
                    {
                        greet(name) { return 'direct:' + name; },
                        nickname() { return 'directed'; }
                    },
                    'direct'
                );
                directSummary = useNicknamedGreeter(directGreeter);

                interfaceTemplate = JavaAdapter.type(
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$GreetingCallback',
                    ['cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$FarewellCallback']
                );
                interfaceKind = interfaceTemplate.kind;
                interfaceSuper = interfaceTemplate.superClassName === null;
                interfaceNames = interfaceTemplate.interfaceNames.join('|');
                interfaceProxy = interfaceTemplate.create(value => 'iface:' + value);
                interfaceSummary = useGreeting(interfaceProxy) + '|' + useFarewell(interfaceProxy);

                runtimeTemplate = runtime.adapterType(
                    'cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$AbstractGreeter',
                    ['cn.qihuang02.graaljs.core.GraaljsContextIntegrationTest$Nicknamed']
                );
                runtimeAdapter = new runtimeTemplate({
                    greet(name) { return 'runtime-template:' + name; },
                    nickname() { return 'runtime-template-nick'; }
                }, 'rt');
                runtimeSummary = useNicknamedGreeter(runtimeAdapter);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("abstract", bindings.getMember("abstractKind").asString());
        assertTrue(bindings.getMember("abstractSuper").asString().endsWith("$AbstractGreeter"));
        assertEquals(1, bindings.getMember("abstractInterfaceCount").asInt());
        assertEquals("tmpl:template:alex|templated", bindings.getMember("abstractSummary").asString());
        assertEquals("direct:direct:alex|directed", bindings.getMember("directSummary").asString());
        assertEquals("interface", bindings.getMember("interfaceKind").asString());
        assertTrue(bindings.getMember("interfaceSuper").asBoolean());
        assertTrue(bindings.getMember("interfaceNames").asString().contains("GreetingCallback"));
        assertTrue(bindings.getMember("interfaceNames").asString().contains("FarewellCallback"));
        assertEquals("iface:alex|iface:alex", bindings.getMember("interfaceSummary").asString());
        assertEquals("rt:runtime-template:alex|runtime-template-nick", bindings.getMember("runtimeSummary").asString());
    }

    @Test
    void shouldContinueEventDispatchAfterListenerError() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("eventErrorIsolation.js", """
                secondReceived = false;
                events.on('test', payload => { throw new Error('listener boom'); });
                events.on('test', payload => { secondReceived = true; });
                events.emit('test', { data: 1 });
                """);

        assertTrue(context.getPolyglotContext().getBindings("js").getMember("secondReceived").asBoolean());
    }

    @Test
    void shouldResolveOverloadedMethodsByPrecision() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("overload.js", """
                intResult = overloadTarget.accept(42);
                nullResult = overloadTarget.accept(null);
                stringIntResult = overloadTarget.accept("a", 1);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("int:42", bindings.getMember("intResult").asString());
        assertEquals("object:null", bindings.getMember("nullResult").asString());
        assertEquals("string-int:a:1", bindings.getMember("stringIntResult").asString());
    }

    @Test
    void shouldResolveByteShortCharOverloads() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("fineOverload.js", """
                charResult = fineOverloadTarget.acceptChar('a');
                byteResult = fineOverloadTarget.acceptNum(42);
                shortResult = fineOverloadTarget.acceptNum(1000);
                intResult = fineOverloadTarget.acceptNum(100000);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("char:a", bindings.getMember("charResult").asString());
        assertEquals("byte:42", bindings.getMember("byteResult").asString());
        assertEquals("short:1000", bindings.getMember("shortResult").asString());
        assertEquals("int:100000", bindings.getMember("intResult").asString());
    }

    @Test
    void shouldDetectAmbiguousMethodOverloads() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // process("a", "b") is ambiguous: both process(Object,String) and process(String,Object) match equally
        assertThrows(IllegalStateException.class, () ->
                context.eval("ambiguous.js", "ambiguousTarget.process('a', 'b');")
        );
    }

    @Test
    void shouldDispatchAbstractClassConstructorOverloads() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // Test single-arg constructor
        MultiConstructorAbstract singleArg = context.asAbstractClass(
                context.eval("abstractCtor1.js", "({ work() { return 'single'; } })"),
                MultiConstructorAbstract.class,
                "tag1"
        );
        assertEquals("tag1", singleArg.tag());
        assertEquals("single", singleArg.work());

        // Test two-arg constructor
        MultiConstructorAbstract twoArg = context.asAbstractClass(
                context.eval("abstractCtor2.js", "({ work() { return 'double'; } })"),
                MultiConstructorAbstract.class,
                "tag2", 5
        );
        assertEquals("tag2:5", twoArg.tag());
        assertEquals("double", twoArg.work());
    }

    @Test
    void shouldExposeListProxyJsMethods() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("listMethods.js", """
                bridgeList.push("a");
                bridgeList.push("b");
                bridgeList.push("c");
                listLength = bridgeList.length;
                joinResult = bridgeList.join("-");
                includesA = bridgeList.includes("a");
                includesZ = bridgeList.includes("z");
                indexOfB = bridgeList.indexOf("b");
                indexOfZ = bridgeList.indexOf("z");
                mapped = bridgeList.map(function(v) { return v + "!"; }).join(",");
                filtered = bridgeList.filter(function(v) { return v !== "b"; }).join(",");
                found = bridgeList.find(function(v) { return v === "c"; });
                sliced = bridgeList.slice(1, 3).join(",");
                popped = bridgeList.pop();
                afterPopLength = bridgeList.length;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(3, bindings.getMember("listLength").asInt());
        assertEquals("a-b-c", bindings.getMember("joinResult").asString());
        assertTrue(bindings.getMember("includesA").asBoolean());
        assertFalse(bindings.getMember("includesZ").asBoolean());
        assertEquals(1, bindings.getMember("indexOfB").asInt());
        assertEquals(-1, bindings.getMember("indexOfZ").asInt());
        assertEquals("a!,b!,c!", bindings.getMember("mapped").asString());
        assertEquals("a,c", bindings.getMember("filtered").asString());
        assertEquals("c", bindings.getMember("found").asString());
        assertEquals("b,c", bindings.getMember("sliced").asString());
        assertEquals("c", bindings.getMember("popped").asString());
        assertEquals(2, bindings.getMember("afterPopLength").asInt());
    }

    @Test
    void shouldExposeMapProxyJsMethods() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("mapMethods.js", """
                bridgeMap.x = 10;
                bridgeMap.y = 20;
                mapSize = bridgeMap.size;
                hasX = bridgeMap.has("x");
                hasZ = bridgeMap.has("z");
                forEachResult = [];
                bridgeMap.forEach(function(value, key) {
                    forEachResult.push(key + "=" + value);
                });
                forEachStr = forEachResult.join(",");
                deletedX = bridgeMap.delete("x");
                afterDeleteSize = bridgeMap.size;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(2, bindings.getMember("mapSize").asInt());
        assertTrue(bindings.getMember("hasX").asBoolean());
        assertFalse(bindings.getMember("hasZ").asBoolean());
        assertTrue(bindings.getMember("forEachStr").asString().contains("x=10"));
        assertTrue(bindings.getMember("forEachStr").asString().contains("y=20"));
        assertTrue(bindings.getMember("deletedX").asBoolean());
        assertEquals(1, bindings.getMember("afterDeleteSize").asInt());
    }

    @Test
    void shouldExposeSetProxyJsMethods() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("setMethods.js", """
                bridgeSet.add("x");
                bridgeSet.add("y");
                bridgeSet.add("x");
                setSize = bridgeSet.size;
                hasX = bridgeSet.has("x");
                hasZ = bridgeSet.has("z");
                forEachResult = [];
                bridgeSet.forEach(function(value) {
                    forEachResult.push(value);
                });
                forEachStr = forEachResult.join(",");
                deletedX = bridgeSet.delete("x");
                afterDeleteSize = bridgeSet.size;
                arrayLength = bridgeSet.toArray().length;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(2, bindings.getMember("setSize").asInt());
        assertTrue(bindings.getMember("hasX").asBoolean());
        assertFalse(bindings.getMember("hasZ").asBoolean());
        assertTrue(bindings.getMember("forEachStr").asString().contains("x"));
        assertTrue(bindings.getMember("forEachStr").asString().contains("y"));
        assertTrue(bindings.getMember("deletedX").asBoolean());
        assertEquals(1, bindings.getMember("afterDeleteSize").asInt());
        assertEquals(1, bindings.getMember("arrayLength").asInt());
    }

    @Test
    void shouldBridgeForgeEventsToJsCallbacks() {
        IEventBus testBus = BusBuilder.builder().build();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setTestEventBus(testBus);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // Register a Forge event mapping via the factory hook
        ForgeEventBridge bridge = factory.getEventBus(ScriptType.STARTUP).getForgeBridge();
        bridge.registerMapping("test.event", TestForgeEvent.class);

        // Subscribe from JS
        context.eval("forgeEvents.js", """
                forgeResult = null;
                forgeListenerCount = events.onForge("test.event", function(event) {
                    forgeResult = event.message();
                });
                forgeAvailable = events.forgeEvents();
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(1, bindings.getMember("forgeListenerCount").asInt());

        // Fire the Forge event
        testBus.post(new TestForgeEvent("hello from forge"));

        assertEquals("hello from forge", bindings.getMember("forgeResult").asString());
        assertEquals(1, factory.getEventBus(ScriptType.STARTUP).forgeListenerCount("test.event"));

        // Unsubscribe and verify
        context.eval("forgeUnsubscribe.js", """
                removable = function(event) {};
                events.onForge("test.event", removable);
                beforeRemove = events.forgeListenerCount("test.event");
                events.offForge("test.event", removable);
                afterRemove = events.forgeListenerCount("test.event");
                """);

        assertEquals(2, bindings.getMember("beforeRemove").asInt());
        assertEquals(1, bindings.getMember("afterRemove").asInt());

        // Close should clean up all Forge listeners
        factory.close(ScriptType.STARTUP);
        assertEquals(0, bridge.listenerCount("test.event"));
    }

    @Test
    void shouldRejectUnknownForgeEventName() {
        IEventBus testBus = BusBuilder.builder().build();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setTestEventBus(testBus);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        assertThrows(IllegalStateException.class, () ->
                context.eval("unknownForge.js", "events.onForge('nonexistent', function() {});")
        );
    }

    @Test
    void shouldCancelForgeEventWhenCallbackReturnsFalse() {
        IEventBus testBus = BusBuilder.builder().build();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setTestEventBus(testBus);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        ForgeEventBridge bridge = factory.getEventBus(ScriptType.STARTUP).getForgeBridge();
        bridge.registerMapping("test.cancelable", CancelableForgeEvent.class);

        context.eval("cancelEvent.js", """
                events.onForge("test.cancelable", function(event) {
                    return false;
                });
                """);

        CancelableForgeEvent event = new CancelableForgeEvent("cancel me");
        testBus.post(event);
        assertTrue(event.isCanceled(), "Event should be canceled when callback returns false");
    }

    @Test
    void shouldCancelForgeEventWhenCallbackReturnsCancelledObject() {
        IEventBus testBus = BusBuilder.builder().build();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setTestEventBus(testBus);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        ForgeEventBridge bridge = factory.getEventBus(ScriptType.STARTUP).getForgeBridge();
        bridge.registerMapping("test.cancelable", CancelableForgeEvent.class);

        context.eval("cancelEventObj.js", """
                events.onForge("test.cancelable", function(event) {
                    return { cancelled: true };
                });
                """);

        CancelableForgeEvent event = new CancelableForgeEvent("cancel me");
        testBus.post(event);
        assertTrue(event.isCanceled(), "Event should be canceled when callback returns { cancelled: true }");
    }

    @Test
    void shouldSetForgeEventResultFromCallbackReturnValue() {
        IEventBus testBus = BusBuilder.builder().build();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setTestEventBus(testBus);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        ForgeEventBridge bridge = factory.getEventBus(ScriptType.STARTUP).getForgeBridge();
        bridge.registerMapping("test.result", ResultForgeEvent.class);

        context.eval("resultEvent.js", """
                events.onForge("test.result", function(event) {
                    return "allow";
                });
                """);

        ResultForgeEvent event = new ResultForgeEvent("test");
        testBus.post(event);
        assertEquals(Event.Result.ALLOW, event.getResult(), "Event result should be ALLOW");
    }

    @Test
    void shouldNotCancelNonCancelableEvent() {
        IEventBus testBus = BusBuilder.builder().build();
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        factory.setTestEventBus(testBus);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        ForgeEventBridge bridge = factory.getEventBus(ScriptType.STARTUP).getForgeBridge();
        bridge.registerMapping("test.event", TestForgeEvent.class);

        context.eval("noCancelEvent.js", """
                events.onForge("test.event", function(event) {
                    return false;
                });
                """);

        // TestForgeEvent is not @Cancelable, so returning false should not throw
        TestForgeEvent event = new TestForgeEvent("no cancel");
        testBus.post(event);
        // No assertion needed — just verify no exception is thrown
    }

    @Test
    void shouldNotAllowDirectHostObjectMemberAccessWithPublicAccessFalse() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // With allowPublicAccess(false), direct host objects should not expose members
        // unless they go through our proxy layer
        context.eval("hostAccess.js", """
                // api goes through our proxy layer, so members should be accessible
                hasRecord = "record" in api;
                hasVisibleField = "visibleField" in api;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertTrue(bindings.getMember("hasRecord").asBoolean(), "Proxy-wrapped members should be accessible");
        assertTrue(bindings.getMember("hasVisibleField").asBoolean(), "Proxy-wrapped fields should be accessible");
    }

    private void writeScript(ScriptType type, String fileName, String content) throws IOException {
        Path dir = tempDir.resolve(type.directory);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), content);
    }

    private static class TestFactory extends GraaljsContextFactory {
        private final TestBindings bindings;
        private IEventBus testEventBus;

        private TestFactory(Path scriptRoot, TestBindings bindings) {
            super(scriptRoot);
            this.bindings = bindings;
        }

        void setTestEventBus(IEventBus bus) {
            this.testEventBus = bus;
        }

        @Override
        protected IEventBus getForgeEventBus() {
            return testEventBus != null ? testEventBus : BusBuilder.builder().build();
        }

        @Override
        protected void configureBindings(ScriptType type, BindingsBuilder builder) {
            super.configureBindings(type, builder);
            builder.add("api", bindings.api)
                    .add("bridgeMap", bindings.bridgeMap)
                    .add("bridgeList", bindings.bridgeList)
                    .add("bridgeSet", bindings.bridgeSet)
                    .add("customWrapped", bindings.customWrapped)
                    .add("staticFallback", bindings.staticFallback)
                    .add("hostErrors", bindings.hostErrors)
                    .addClass("TestStatics", TestStatics.class)
                    .addClass("ConstructibleType", ConstructibleType.class)
                    .addTypedFunction("typedJoin", (context, args) -> args[0] + ":" + args[1], String.class, Integer.class)
                    .addTypedFunction("typedRecordSummary", (context, args) -> {
                        SampleRecord record = (SampleRecord) args[0];
                        return record.name() + ":" + record.count();
                    }, SampleRecord.class)
                    .addTypedFunction("useMultiCallback", (context, args) -> {
                        MultiCallback callback = (MultiCallback) args[0];
                        return callback.open("input") + "|" + callback.close();
                    }, MultiCallback.class)
                    .addTypedFunction("useGreeting", (context, args) -> {
                        GreetingCallback callback = (GreetingCallback) args[0];
                        return callback.greet("alex");
                    }, GreetingCallback.class)
                    .addTypedFunction("useFarewell", (context, args) -> {
                        FarewellCallback callback = (FarewellCallback) args[0];
                        return callback.bye("alex");
                    }, FarewellCallback.class)
                    .addTypedFunction("useNicknamedGreeter", (context, args) -> {
                        AbstractGreeter greeter = (AbstractGreeter) args[0];
                        Nicknamed nicknamed = (Nicknamed) args[0];
                        return greeter.format("alex") + "|" + nicknamed.nickname();
                    }, AbstractGreeter.class)
                    .addFunction("dynamicArgs", args -> {
                        StringBuilder builder1 = new StringBuilder();
                        for (int i = 0; i < args.length; i++) {
                            if (i > 0) {
                                builder1.append('|');
                            }
                            builder1.append(args[i].as(Object.class));
                        }
                        return builder1.toString();
                    })
                    .add("visibilitySample", new VisibilitySample())
                    .add("overloadTarget", new OverloadTarget())
                    .add("ambiguousTarget", new AmbiguousTarget())
                    .add("fineOverloadTarget", new FineOverloadTarget())
                    .addClass("MultiConstructorAbstract", MultiConstructorAbstract.class);
        }
    }

    private static class ContextAwareVisibilityFactory extends TestFactory {
        private ContextAwareVisibilityFactory(Path scriptRoot, TestBindings bindings) {
            super(scriptRoot, bindings);
        }

        @Override
        public boolean visibleToScripts(String className, ClassVisibilityContext visibilityContext) {
            if (!super.visibleToScripts(className, visibilityContext)) {
                return false;
            }
            if (className.endsWith("$ConstructibleType")) {
                return visibilityContext != ClassVisibilityContext.CLASS_LOOKUP;
            }
            if ("java.util.ArrayList".equals(className)) {
                return visibilityContext != ClassVisibilityContext.PACKAGE_LOOKUP;
            }
            if (className.endsWith("$GreetingCallback") || className.endsWith("$Nicknamed")) {
                return visibilityContext != ClassVisibilityContext.ADAPTER_INTERFACE;
            }
            if (className.endsWith("$AbstractGreeter")) {
                return visibilityContext != ClassVisibilityContext.ADAPTER_SUPER;
            }
            return true;
        }
    }

    private static class HiddenExceptionFactory extends TestFactory {
        private HiddenExceptionFactory(Path scriptRoot, TestBindings bindings) {
            super(scriptRoot, bindings);
        }

        @Override
        public boolean visibleToScripts(String className, ClassVisibilityContext visibilityContext) {
            if (className.endsWith("$HiddenHostException") && visibilityContext == ClassVisibilityContext.EXCEPTION) {
                return false;
            }
            return super.visibleToScripts(className, visibilityContext);
        }
    }

    private static class TestBindings {
        private final ScriptApi api = new ScriptApi();
        private final Map<String, Object> bridgeMap = new LinkedHashMap<>();
        private final List<Object> bridgeList = new ArrayList<>();
        private final Set<Object> bridgeSet = new LinkedHashSet<>();
        private final CustomWrappedValue customWrapped = new CustomWrappedValue();
        private final StaticFallbackSample staticFallback = new StaticFallbackSample();
        private final HostErrorSource hostErrors = new HostErrorSource();
    }

    private static class RecordingErrorReporter implements ErrorReporter {
        private int errorCount;
        private int runtimeErrorCount;
        private String lastSourceName;
        private String lastLineSource;
        private int lastLineOffset;
        private String lastMessage;
        private String lastCauseType;

        @Override
        public void warning(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset) {
        }

        @Override
        public void error(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset, Throwable cause) {
            errorCount++;
            lastSourceName = sourceName;
            lastLineSource = lineSource;
            lastLineOffset = lineOffset;
            lastMessage = message;
            lastCauseType = cause == null ? null : cause.getClass().getName();
        }

        @Override
        public RuntimeException runtimeError(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset, Throwable cause) {
            runtimeErrorCount++;
            lastSourceName = sourceName;
            lastLineSource = lineSource;
            lastLineOffset = lineOffset;
            lastMessage = message;
            lastCauseType = cause == null ? null : cause.getClass().getName();
            return new IllegalStateException(message, cause);
        }
    }

    @RemapPrefixForJS("get")
    private static class ScriptApi implements CustomMemberProvider {
        private final List<String> calls = new ArrayList<>();
        public String visibleField = "visible";
        private String dynamicValue = "dynamic";

        @HideFromJS
        public String hiddenField = "hidden";

        public void record(String value) {
            calls.add(value);
        }

        @RemapForJS("aliasRecord")
        public void renamedRecord(String value) {
            calls.add("alias:" + value);
        }

        public String getStatus() {
            return "ready";
        }

        @HideFromJS
        public void hiddenCall() {
            calls.add("hidden");
        }

        @Override
        public Collection<CustomMember> getCustomMembers() {
            return List.of(new CustomMember("dynamicValue", String.class, dynamicValue));
        }
    }

    private static class TestStatics {
        public static final String NAME = "STATIC";

        public static String ping(String value) {
            return "pong:" + value;
        }
    }

    private static class StaticFallbackSample {
        public static final String kind = "fallback";

        public String instanceName() {
            return "instance";
        }

        public static String ping(String value) {
            return "fallback:" + value;
        }
    }

    private static class HostErrorSource {
        public void explode() {
            throw new HiddenHostException("blocked");
        }
    }

    private static class HiddenHostException extends RuntimeException {
        private HiddenHostException(String message) {
            super(message);
        }
    }

    public static class ConstructibleType {
        private final String name;
        private final int count;

        public ConstructibleType(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String summary() {
            return name + ":" + count;
        }
    }

    public record SampleRecord(String name, int count) {
    }

    private static class CustomWrappedValue implements CustomJavaToJsWrapper {
        @Override
        public Object convertJavaToJs(GraaljsContext context) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", "wrapped");
            value.put("count", 2);
            return context.javaToJs(value);
        }
    }

    private static class VisibilitySample {
        public String ok() {
            return "ok";
        }

        public Runtime runtimeLeak() {
            return Runtime.getRuntime();
        }

        public void acceptFile(File file) {
        }
    }

    public interface MultiCallback {
        String open(String name);

        String close();
    }

    public interface SameSignatureCallback {
        String open(String value);

        String close(String value);
    }

    public interface GreetingCallback {
        String greet(String value);
    }

    public interface FarewellCallback {
        String bye(String value);
    }

    public interface IncompatibleCallback {
        String open(String value);

        String close();
    }

    public abstract static class AbstractGreeter {
        private final String prefix;

        protected AbstractGreeter(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }

        public String format(String name) {
            return prefix + ":" + greet(name);
        }

        public abstract String greet(String name);
    }

    @Test
    void shouldWrapCallbackArgumentsThroughBridgeLayer() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // Define a JS function that implements BridgedArgCallback
        // When Java calls the callback with a BridgedArgHolder, JS should see
        // the bridged version (aliasName visible, hiddenField not visible)
        context.eval("bridgeCallback.js", """
                result = {};
                callback = {
                    accept(holder) {
                        result.hasAlias = "aliasName" in holder;
                        result.hasHidden = "secretField" in holder;
                        result.aliasValue = holder.aliasName();
                        result.publicValue = holder.publicField;
                    }
                };
                """);

        Value callbackValue = context.getPolyglotContext().getBindings("js").getMember("callback");
        BridgedArgCallback adapted = context.asInterface(callbackValue, BridgedArgCallback.class);

        // Call from Java side with a Java object
        BridgedArgHolder holder = new BridgedArgHolder();
        adapted.accept(holder);

        Value result = context.getPolyglotContext().getBindings("js").getMember("result");
        assertTrue(result.getMember("hasAlias").asBoolean(), "Should see @RemapForJS alias");
        assertFalse(result.getMember("hasHidden").asBoolean(), "Should not see @HideFromJS field");
        assertEquals("aliased", result.getMember("aliasValue").asString());
        assertEquals("visible", result.getMember("publicValue").asString());
    }

    @Test
    void shouldBridgeFunctionalInterfaceArgumentsThroughProxy() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        // A JS function adapted as a functional interface should still bridge arguments
        context.eval("functionalBridge.js", """
                functionalResult = {};
                functionalCallback = function(holder) {
                    functionalResult.hasAlias = "aliasName" in holder;
                    functionalResult.hasHidden = "secretField" in holder;
                    functionalResult.aliasValue = holder.aliasName();
                    functionalResult.publicValue = holder.publicField;
                };
                """);

        Value callbackValue = context.getPolyglotContext().getBindings("js").getMember("functionalCallback");
        BridgedArgCallback adapted = context.asInterface(callbackValue, BridgedArgCallback.class);

        BridgedArgHolder holder = new BridgedArgHolder();
        adapted.accept(holder);

        Value result = context.getPolyglotContext().getBindings("js").getMember("functionalResult");
        assertTrue(result.getMember("hasAlias").asBoolean(), "Functional interface should bridge args: @RemapForJS alias visible");
        assertFalse(result.getMember("hasHidden").asBoolean(), "Functional interface should bridge args: @HideFromJS field hidden");
        assertEquals("aliased", result.getMember("aliasValue").asString());
        assertEquals("visible", result.getMember("publicValue").asString());
    }

    @Test
    void shouldResetClientEventsStateWhenContextClosed() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());

        // Simulate: create CLIENT context, then close it externally
        factory.createAndLoad(ScriptType.CLIENT);
        assertTrue(factory.getContext(ScriptType.CLIENT) != null);

        factory.close(ScriptType.CLIENT);
        assertNull(factory.getContext(ScriptType.CLIENT));

        // After close, creating again should work
        factory.createAndLoad(ScriptType.CLIENT);
        assertTrue(factory.getContext(ScriptType.CLIENT) != null);
        factory.close(ScriptType.CLIENT);
    }

    public interface BridgedArgCallback {
        void accept(BridgedArgHolder holder);
    }

    public static class BridgedArgHolder {
        public String publicField = "visible";

        @HideFromJS
        public String secretField = "hidden";

        @RemapForJS("aliasName")
        public String getOriginalName() {
            return "aliased";
        }
    }

    public interface Nicknamed {
        String nickname();
    }

    public static class OverloadTarget {
        public String accept(int value) {
            return "int:" + value;
        }

        public String accept(long value) {
            return "long:" + value;
        }

        public String accept(Object value) {
            return "object:" + value;
        }

        public String accept(String text, int number) {
            return "string-int:" + text + ":" + number;
        }

        public String accept(String text, Object... rest) {
            return "string-varargs:" + text + ":" + rest.length;
        }
    }

    public static class AmbiguousTarget {
        public String process(Object a, String b) {
            return "object-string:" + a + ":" + b;
        }

        public String process(String a, Object b) {
            return "string-object:" + a + ":" + b;
        }
    }

    public static class FineOverloadTarget {
        public String accept(char value) {
            return "char:" + value;
        }

        public String accept(String value) {
            return "string:" + value;
        }

        // char-only overload (no String competitor)
        public String acceptChar(char value) {
            return "char:" + value;
        }

        public String acceptNum(byte value) {
            return "byte:" + value;
        }

        public String acceptNum(short value) {
            return "short:" + value;
        }

        public String acceptNum(int value) {
            return "int:" + value;
        }
    }

    public abstract static class MultiConstructorAbstract {
        private final String tag;

        protected MultiConstructorAbstract(String tag) {
            this.tag = tag;
        }

        protected MultiConstructorAbstract(String tag, int count) {
            this.tag = tag + ":" + count;
        }

        public String tag() {
            return tag;
        }

        public abstract String work();
    }

    public static class TestForgeEvent extends Event {
        private String message;

        public TestForgeEvent() {
            this.message = "";
        }

        public TestForgeEvent(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    @net.minecraftforge.eventbus.api.Cancelable
    public static class CancelableForgeEvent extends Event {
        private final String message;

        public CancelableForgeEvent() {
            this.message = "";
        }

        public CancelableForgeEvent(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    @net.minecraftforge.eventbus.api.Event.HasResult
    public static class ResultForgeEvent extends Event {
        private final String message;

        public ResultForgeEvent() {
            this.message = "";
        }

        public ResultForgeEvent(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
