package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.typewrap.EnumTypeWrapper;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraaljsTypeWrappersTest {
    @Test
    void shouldRegisterAndResolveCustomWrapper() {
        TypeWrappers wrappers = new TypeWrappers();
        wrappers.register(SampleValue.class, from -> from instanceof String, (context, from, target) -> new SampleValue(from + "-wrapped"));

        var factory = wrappers.getWrapperFactory(SampleValue.class, "demo");
        assertNotNull(factory);
        assertEquals("demo-wrapped", factory.wrap(null, "demo", SampleValue.class).value());
    }

    @Test
    void shouldRejectDuplicateWrapperRegistration() {
        TypeWrappers wrappers = new TypeWrappers();
        wrappers.register(SampleValue.class, (context, from, target) -> new SampleValue("a"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> wrappers.register(SampleValue.class, (context, from, target) -> new SampleValue("b")));
        assertTrue(exception.getMessage().contains("已注册包装器"));
    }

    @Test
    void shouldConvertEnumByNameAndOrdinal() {
        EnumTypeWrapper<TestState> wrapper = new EnumTypeWrapper<>(TestState.class);

        assertEquals(TestState.READY, wrapper.wrap(null, "READY", TestState.class));
        assertEquals(TestState.RUNNING, wrapper.wrap(null, "running", TestState.class));
        assertEquals(TestState.DONE, wrapper.wrap(null, 2, TestState.class));
    }

    record SampleValue(String value) {
    }

    enum TestState {
        READY,
        RUNNING,
        DONE
    }
}
