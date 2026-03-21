package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.typewrap.GenericTypeInfo;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraaljsGenericTypeTest {
    @TempDir
    Path tempDir;

    // ── GenericTypeInfo 单元测试 ──

    @Test
    void shouldParseRawClassAsNoTypeArguments() {
        GenericTypeInfo info = GenericTypeInfo.of(String.class);
        assertEquals(String.class, info.rawType());
        assertFalse(info.hasTypeArguments());
        assertEquals(Object.class, info.typeArgument(0).rawType());
    }

    @Test
    void shouldParseParameterizedType() throws Exception {
        Type listOfString = GenericHolder.class.getDeclaredField("strings").getGenericType();
        GenericTypeInfo info = GenericTypeInfo.of(listOfString);
        assertEquals(List.class, info.rawType());
        assertTrue(info.hasTypeArguments());
        assertEquals(String.class, info.typeArgument(0).rawType());
    }

    @Test
    void shouldParseMapParameterizedType() throws Exception {
        Type mapType = GenericHolder.class.getDeclaredField("stringToIntMap").getGenericType();
        GenericTypeInfo info = GenericTypeInfo.of(mapType);
        assertEquals(Map.class, info.rawType());
        assertEquals(String.class, info.typeArgument(0).rawType());
        assertEquals(Integer.class, info.typeArgument(1).rawType());
    }

    @Test
    void shouldParseNestedParameterizedType() throws Exception {
        Type nestedType = GenericHolder.class.getDeclaredField("listOfLists").getGenericType();
        GenericTypeInfo info = GenericTypeInfo.of(nestedType);
        assertEquals(List.class, info.rawType());
        GenericTypeInfo innerInfo = info.typeArgument(0);
        assertEquals(List.class, innerInfo.rawType());
        assertEquals(Integer.class, innerInfo.typeArgument(0).rawType());
    }

    @Test
    void shouldConstructManually() {
        GenericTypeInfo info = GenericTypeInfo.of(List.class, String.class);
        assertEquals(List.class, info.rawType());
        assertTrue(info.hasTypeArguments());
        assertEquals(String.class, info.typeArgument(0).rawType());
    }

    // ── GraaljsContext 泛型转换集成测试 ──

    @Test
    void shouldConvertJsArrayToListOfString() {
        GraaljsContext context = createContext();
        Value jsArray = context.eval("listOfString.js", "['hello', 'world', 42]");
        GenericTypeInfo targetInfo = GenericTypeInfo.of(List.class, String.class);

        List<String> result = context.jsToJava(jsArray, targetInfo);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("hello", result.get(0));
        assertEquals("world", result.get(1));
        assertEquals("42", result.get(2));
    }

    @Test
    void shouldConvertJsArrayToListOfInteger() {
        GraaljsContext context = createContext();
        Value jsArray = context.eval("listOfInt.js", "[1, 2, 3]");
        GenericTypeInfo targetInfo = GenericTypeInfo.of(List.class, Integer.class);

        List<Integer> result = context.jsToJava(jsArray, targetInfo);

        assertNotNull(result);
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void shouldConvertJsArrayToSetOfString() {
        GraaljsContext context = createContext();
        Value jsArray = context.eval("setOfString.js", "['a', 'b', 'a']");
        GenericTypeInfo targetInfo = GenericTypeInfo.of(Set.class, String.class);

        Set<String> result = context.jsToJava(jsArray, targetInfo);

        assertNotNull(result);
        assertEquals(Set.of("a", "b"), result);
    }

    @Test
    void shouldConvertJsObjectToMapOfStringToInteger() {
        GraaljsContext context = createContext();
        Value jsObject = context.eval("mapOfStringInt.js", "({ a: 1, b: 2, c: 3 })");
        GenericTypeInfo targetInfo = GenericTypeInfo.of(Map.class, String.class, Integer.class);

        Map<String, Integer> result = context.jsToJava(jsObject, targetInfo);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
        assertEquals(3, result.get("c"));
    }

    @Test
    void shouldFallbackToRawConversionWithoutTypeArguments() {
        GraaljsContext context = createContext();
        Value jsArray = context.eval("rawList.js", "[1, 'two', true]");

        List<?> result = context.jsToJava(jsArray, List.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1, result.get(0));
        assertEquals("two", result.get(1));
        assertEquals(true, result.get(2));
    }

    @Test
    void shouldConvertViaMethodProxyWithGenericParameters() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("methodProxy.js", """
                result = genericHolder.joinStrings(['alpha', 'beta', 'gamma']);
                intSum = genericHolder.sumIntegers([10, 20, 30]);
                mapResult = genericHolder.describeMap({ x: 1, y: 2 });
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("alpha,beta,gamma", bindings.getMember("result").asString());
        assertEquals(60, bindings.getMember("intSum").asInt());
        assertEquals("x=1,y=2", bindings.getMember("mapResult").asString());
    }

    @Test
    void shouldConvertViaInterfaceAdapterWithGenericReturnType() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("interfaceGeneric.js", """
                adapted = useStringListProvider({ provide() { return ['x', 'y', 'z']; } });
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("x,y,z", bindings.getMember("adapted").asString());
    }

    @Test
    void shouldConvertViaGenericTypedFunction() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("genericTypedFn.js", """
                result = joinStringList(['one', 'two', 'three']);
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("one|two|three", bindings.getMember("result").asString());
    }

    @Test
    void shouldConvertViaConstructorWithGenericParameters() {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        context.eval("constructorGeneric.js", """
                holder = new GenericConstructible(['a', 'b', 'c']);
                holderResult = holder.describe();
                """);

        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("items=a,b,c", bindings.getMember("holderResult").asString());
    }

    // ── 辅助类型 ──

    private GraaljsContext createContext() {
        TestFactory factory = new TestFactory(tempDir);
        return factory.create(ScriptType.STARTUP);
    }

    @SuppressWarnings("unused")
    private static class GenericHolder {
        List<String> strings;
        Map<String, Integer> stringToIntMap;
        List<List<Integer>> listOfLists;
    }

    public static class GenericService {
        public String joinStrings(List<String> items) {
            return String.join(",", items);
        }

        public int sumIntegers(List<Integer> numbers) {
            int sum = 0;
            for (int n : numbers) {
                sum += n;
            }
            return sum;
        }

        public String describeMap(Map<String, Integer> map) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            return sb.toString();
        }
    }

    public interface StringListProvider {
        List<String> provide();
    }

    public static class GenericConstructible {
        private final List<String> items;

        public GenericConstructible(List<String> items) {
            this.items = items;
        }

        public String describe() {
            return "items=" + String.join(",", items);
        }
    }

    private static class TestFactory extends GraaljsContextFactory {
        private TestFactory(Path scriptRoot) {
            super(scriptRoot);
        }

        @Override
        protected void configureBindings(ScriptType type, Map<String, Object> bindings) {
            super.configureBindings(type, bindings);
            bindings.put("genericHolder", new GenericService());
            bindings.put("GenericConstructible", GenericConstructible.class);
            bindings.put("useStringListProvider", (ProxyExecutable) args -> {
                GraaljsContext ctx = TestFactory.this.current();
                StringListProvider provider = ctx.jsToJava(args[0], StringListProvider.class);
                List<String> result = provider.provide();
                return String.join(",", result);
            });
            bindings.put("joinStringList", (ProxyExecutable) args -> {
                GraaljsContext ctx = TestFactory.this.current();
                GenericTypeInfo listOfString = GenericTypeInfo.of(List.class, String.class);
                @SuppressWarnings("unchecked")
                List<String> list = ctx.jsToJava(args[0], listOfString);
                return String.join("|", list);
            });
        }
    }

    /**
     * 构造一个简单的 ParameterizedType 实例用于测试。
     */
    private static ParameterizedType parameterizedType(Class<?> raw, Type... args) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return args;
            }

            @Override
            public Type getRawType() {
                return raw;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }
}
