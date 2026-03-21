package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.bridge.CustomMember;
import cn.qihuang02.graaljs.bridge.CustomMemberProvider;
import cn.qihuang02.graaljs.error.ErrorReporter;
import cn.qihuang02.graaljs.error.GraaljsException;
import cn.qihuang02.graaljs.error.ScriptException;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import cn.qihuang02.graaljs.util.CustomJavaToJsWrapper;
import cn.qihuang02.graaljs.util.HideFromJS;
import cn.qihuang02.graaljs.util.RemapForJS;
import cn.qihuang02.graaljs.util.RemapPrefixForJS;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
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

        IllegalArgumentException packageLookupException = assertThrows(IllegalArgumentException.class, () ->
                factory.resolveVisibleClass("java.util.ArrayList", ClassVisibilityContext.PACKAGE_LOOKUP)
        );
        assertTrue(packageLookupException.getMessage().contains("not visible"));
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

        ScriptException exception = assertThrows(ScriptException.class, () -> context.eval(source));

        assertTrue(exception.getMessage().contains("boom"));
        assertEquals("failing.js", reporter.lastSourceName);
        assertTrue(reporter.errorCount >= 1);
        assertTrue(reporter.lastLineSource.contains("throw new Error('boom')"));
        assertTrue(reporter.lastLineOffset >= -1);
    }

    @Test
    void shouldHideHostExceptionDetailsWhenExceptionTypeIsInvisible() {
        RecordingErrorReporter reporter = new RecordingErrorReporter();
        HiddenExceptionFactory factory = new HiddenExceptionFactory(tempDir, new TestBindings());
        factory.setErrorReporter(reporter);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        ScriptException exception = assertThrows(ScriptException.class, () ->
                context.eval("hostFailure.js", "hostErrors.explode();")
        );

        assertEquals("Host exception is not visible to scripts", reporter.lastMessage);
        assertEquals("hostFailure.js", reporter.lastSourceName);
        assertTrue(reporter.lastLineSource.contains("hostErrors.explode();"));
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
    void shouldAdaptMultiMethodInterfaces() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        MultiCallback callback = context.jsToJava(context.eval("multi.js", "({ open(name) { return 'open:' + name; }, close() { return 'close'; } })"), MultiCallback.class);
        assertEquals("open:door|close", callback.open("door") + "|" + callback.close());

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

        AbstractGreeter missingMethodGreeter = context.asAbstractClass(
                context.eval("brokenAbstract.js", "({})"),
                AbstractGreeter.class,
                "missing"
        );
        IllegalStateException missingException = assertThrows(IllegalStateException.class, () -> missingMethodGreeter.greet("alex"));
        assertTrue(missingException.getMessage().contains("does not implement abstract method"));

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

        assertThrows(GraaljsException.class, () ->
                context.eval("ambiguous.js", "ambiguousTarget.process('a', 'b');")
        );
    }

    @Test
    void shouldDispatchAbstractClassConstructorOverloads() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        MultiConstructorAbstract singleArg = context.asAbstractClass(
                context.eval("abstractCtor1.js", "({ work() { return 'single'; } })"),
                MultiConstructorAbstract.class,
                "tag1"
        );
        assertEquals("tag1", singleArg.tag());
        assertEquals("single", singleArg.work());

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
    void shouldNotAllowDirectHostObjectMemberAccessWithPublicAccessFalse() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("hostAccess.js", """
                hasRecord = "record" in api;
                hasVisibleField = "visibleField" in api;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertTrue(bindings.getMember("hasRecord").asBoolean(), "Proxy-wrapped members should be accessible");
        assertTrue(bindings.getMember("hasVisibleField").asBoolean(), "Proxy-wrapped fields should be accessible");
    }

    @Test
    void shouldWrapCallbackArgumentsThroughBridgeLayer() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

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

        factory.createAndLoad(ScriptType.CLIENT);
        assertTrue(factory.getContext(ScriptType.CLIENT) != null);

        factory.close(ScriptType.CLIENT);
        assertNull(factory.getContext(ScriptType.CLIENT));

        factory.createAndLoad(ScriptType.CLIENT);
        assertTrue(factory.getContext(ScriptType.CLIENT) != null);
        factory.close(ScriptType.CLIENT);
    }

    @Test
    void shouldReadBeanPropertyViaGetter() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("beanRead.js", """
                beanName = beanTarget.name;
                beanActive = beanTarget.active;
                beanReadOnly = beanTarget.readOnly;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("initial", bindings.getMember("beanName").asString());
        assertTrue(bindings.getMember("beanActive").asBoolean());
        assertEquals("readonly-value", bindings.getMember("beanReadOnly").asString());
    }

    @Test
    void shouldWriteBeanPropertyViaSetter() {
        TestBindings testBindings = new TestBindings();
        TestFactory factory = new TestFactory(tempDir, testBindings);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("beanWrite.js", """
                beanTarget.name = "changed";
                beanTarget.active = false;
                afterName = beanTarget.name;
                afterActive = beanTarget.active;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("changed", bindings.getMember("afterName").asString());
        assertFalse(bindings.getMember("afterActive").asBoolean());
        assertEquals("changed", testBindings.beanTarget.getName());
        assertFalse(testBindings.beanTarget.isActive());
    }

    @Test
    void shouldRejectWriteToReadOnlyBeanProperty() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        Exception exception = assertThrows(Exception.class, () ->
                context.eval("beanReadOnlyWrite.js", "beanTarget.readOnly = 'nope';")
        );
        assertTrue(exception.getMessage().contains("Read-only") || exception.getMessage().contains("readOnly")
                || exception.getCause() != null && exception.getCause().getMessage() != null
                && exception.getCause().getMessage().contains("Read-only"));
    }

    @Test
    void shouldNotSynthesizeBeanPropertyWhenRemapPrefixAliasExists() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("beanPrefix.js", """
                statusResult = beanWithPrefix.status();
                countResult = beanWithPrefix.count();
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("ok", bindings.getMember("statusResult").asString());
        assertEquals(42, bindings.getMember("countResult").asInt());
    }

    @Test
    void shouldPreferFieldOverBeanProperty() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("beanFieldPriority.js", """
                fieldValue = beanWithField.name;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("field-value", bindings.getMember("fieldValue").asString());
    }

    @Test
    void shouldExposeBeanPropertyInHasMemberAndMemberKeys() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("beanHasMember.js", """
                hasName = "name" in beanTarget;
                hasActive = "active" in beanTarget;
                hasReadOnly = "readOnly" in beanTarget;
                hasNonExistent = "nonExistent" in beanTarget;
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertTrue(bindings.getMember("hasName").asBoolean());
        assertTrue(bindings.getMember("hasActive").asBoolean());
        assertTrue(bindings.getMember("hasReadOnly").asBoolean());
        assertFalse(bindings.getMember("hasNonExistent").asBoolean());
    }

    @Test
    void shouldIterateListWithForOf() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("listForOf.js", """
                result = [];
                for (const item of bridgeList) {
                    result.push(item);
                }
                bridgeList.push("a");
                bridgeList.push("b");
                bridgeList.push("c");
                result2 = [];
                for (const item of bridgeList) {
                    result2.push(item);
                }
                forOfResult = result2.join(",");
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("a,b,c", bindings.getMember("forOfResult").asString());
    }

    @Test
    void shouldIterateSetWithForOf() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("setForOf.js", """
                bridgeSet.add("x");
                bridgeSet.add("y");
                bridgeSet.add("z");
                result = [];
                for (const item of bridgeSet) {
                    result.push(item);
                }
                forOfResult = result.join(",");
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("x,y,z", bindings.getMember("forOfResult").asString());
    }

    @Test
    void shouldIteratePureIterableWithForOf() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("iterableForOf.js", """
                result = [];
                for (const item of iterableTarget) {
                    result.push(item);
                }
                forOfResult = result.join(",");
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("a,b,c", bindings.getMember("forOfResult").asString());
    }

    @Test
    void shouldExposeForEachOnIterableProxy() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("iterableForEach.js", """
                result = [];
                iterableTarget.forEach(function(item) {
                    result.push(item + "!");
                });
                forEachResult = result.join(",");
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("a!,b!,c!", bindings.getMember("forEachResult").asString());
    }

    @Test
    void shouldIterateNestedIterables() {
        TestFactory factory = new TestFactory(tempDir, new TestBindings());
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("nestedIterable.js", """
                bridgeList.push(iterableTarget);
                result = [];
                for (const iterable of bridgeList) {
                    for (const item of iterable) {
                        result.push(item);
                    }
                }
                nestedResult = result.join(",");
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("a,b,c", bindings.getMember("nestedResult").asString());
    }

    private void writeScript(ScriptType type, String fileName, String content) throws IOException {
        Path dir = tempDir.resolve(type.directory);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), content);
    }

    private static class TestFactory extends GraaljsContextFactory {
        private final TestBindings bindings;

        private TestFactory(Path scriptRoot, TestBindings bindings) {
            super(scriptRoot);
            this.bindings = bindings;
        }

        @Override
        protected void configureBindings(ScriptType type, Map<String, Object> bindings) {
            super.configureBindings(type, bindings);
            bindings.put("api", this.bindings.api);
            bindings.put("bridgeMap", this.bindings.bridgeMap);
            bindings.put("bridgeList", this.bindings.bridgeList);
            bindings.put("bridgeSet", this.bindings.bridgeSet);
            bindings.put("customWrapped", this.bindings.customWrapped);
            bindings.put("staticFallback", this.bindings.staticFallback);
            bindings.put("hostErrors", this.bindings.hostErrors);
            bindings.put("TestStatics", TestStatics.class);
            bindings.put("ConstructibleType", ConstructibleType.class);
            bindings.put("typedJoin", (ProxyExecutable) args -> {
                GraaljsContext ctx = this.current();
                String s = ctx.jsToJava(args[0], String.class);
                Integer i = ctx.jsToJava(args[1], Integer.class);
                return s + ":" + i;
            });
            bindings.put("typedRecordSummary", (ProxyExecutable) args -> {
                GraaljsContext ctx = this.current();
                SampleRecord record = ctx.jsToJava(args[0], SampleRecord.class);
                return record.name() + ":" + record.count();
            });
            bindings.put("useMultiCallback", (ProxyExecutable) args -> {
                GraaljsContext ctx = this.current();
                MultiCallback callback = ctx.jsToJava(args[0], MultiCallback.class);
                return callback.open("input") + "|" + callback.close();
            });
            bindings.put("useGreeting", (ProxyExecutable) args -> {
                GraaljsContext ctx = this.current();
                GreetingCallback callback = ctx.jsToJava(args[0], GreetingCallback.class);
                return callback.greet("alex");
            });
            bindings.put("useFarewell", (ProxyExecutable) args -> {
                GraaljsContext ctx = this.current();
                FarewellCallback callback = ctx.jsToJava(args[0], FarewellCallback.class);
                return callback.bye("alex");
            });
            bindings.put("useNicknamedGreeter", (ProxyExecutable) args -> {
                GraaljsContext ctx = this.current();
                AbstractGreeter greeter = ctx.jsToJava(args[0], AbstractGreeter.class);
                Nicknamed nicknamed = ctx.jsToJava(args[0], Nicknamed.class);
                return greeter.format("alex") + "|" + nicknamed.nickname();
            });
            bindings.put("dynamicArgs", (ProxyExecutable) args -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append('|');
                    sb.append(args[i].as(Object.class));
                }
                return sb.toString();
            });
            bindings.put("visibilitySample", this.bindings.visibilitySample);
            bindings.put("overloadTarget", this.bindings.overloadTarget);
            bindings.put("ambiguousTarget", this.bindings.ambiguousTarget);
            bindings.put("fineOverloadTarget", this.bindings.fineOverloadTarget);
            bindings.put("MultiConstructorAbstract", MultiConstructorAbstract.class);
            bindings.put("beanTarget", this.bindings.beanTarget);
            bindings.put("beanWithPrefix", this.bindings.beanWithPrefix);
            bindings.put("beanWithField", this.bindings.beanWithField);
            bindings.put("beanStatic", this.bindings.beanStatic);
            bindings.put("iterableTarget", this.bindings.iterableTarget);
            bindings.put("iteratorTarget", this.bindings.iteratorTarget);
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
        private final VisibilitySample visibilitySample = new VisibilitySample();
        private final OverloadTarget overloadTarget = new OverloadTarget();
        private final AmbiguousTarget ambiguousTarget = new AmbiguousTarget();
        private final FineOverloadTarget fineOverloadTarget = new FineOverloadTarget();
        private final BeanTarget beanTarget = new BeanTarget();
        private final BeanWithPrefixTarget beanWithPrefix = new BeanWithPrefixTarget();
        private final BeanWithFieldTarget beanWithField = new BeanWithFieldTarget();
        private final BeanStaticTarget beanStatic = new BeanStaticTarget();
        private final SimpleIterable iterableTarget = new SimpleIterable(List.of("a", "b", "c"));
        private final List<String> iteratorTarget = new ArrayList<>(List.of("x", "y", "z"));
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

    // ── Bean Property 测试辅助类 ──

    public static class BeanTarget {
        private String name = "initial";
        private boolean active = true;
        private String readOnly = "readonly-value";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public String getReadOnly() { return readOnly; }
    }

    @RemapPrefixForJS("get")
    public static class BeanWithPrefixTarget {
        public String getStatus() { return "ok"; }
        public int getCount() { return 42; }
    }

    public static class BeanWithFieldTarget {
        public String name = "field-value";
        public String getName() { return "getter-value"; }
    }

    public static class BeanStaticTarget {
        private static String label = "static-label";
        public static String getLabel() { return label; }
        public static void setLabel(String l) { label = l; }
    }

    // ── Iterable/Iterator 测试辅助类 ──

    public static class SimpleIterable implements Iterable<String> {
        private final List<String> items;

        public SimpleIterable(List<String> items) {
            this.items = new ArrayList<>(items);
        }

        @Override
        public java.util.Iterator<String> iterator() {
            return items.iterator();
        }
    }
}
