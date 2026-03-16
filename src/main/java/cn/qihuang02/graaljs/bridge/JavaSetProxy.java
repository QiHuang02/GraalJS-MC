package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 为 Set 提供稳定顺序的数组式访问。
 */
public class JavaSetProxy implements ProxyArray, ProxyValue {
    private final GraaljsContext context;
    private final Set<Object> set;

    @SuppressWarnings("unchecked")
    public JavaSetProxy(GraaljsContext context, Set<?> set) {
        this.context = context;
        this.set = (Set<Object>) set;
    }

    @Override
    public Object get(long index) {
        List<Object> snapshot = snapshot();
        if (index < 0 || index >= snapshot.size()) {
            return null;
        }
        return context.javaToJs(snapshot.get((int) index));
    }

    @Override
    public void set(long index, Value value) {
        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        }

        Object converted = context.jsToJava(value, Object.class);
        List<Object> snapshot = snapshot();
        int targetIndex = (int) index;
        if (targetIndex > snapshot.size()) {
            throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        }
        if (targetIndex == snapshot.size()) {
            set.add(converted);
            return;
        }

        Object previous = snapshot.get(targetIndex);
        set.remove(previous);
        set.add(converted);
    }

    @Override
    public boolean remove(long index) {
        List<Object> snapshot = snapshot();
        if (index < 0 || index >= snapshot.size()) {
            return false;
        }
        return set.remove(snapshot.get((int) index));
    }

    @Override
    public long getSize() {
        return set.size();
    }

    @Override
    public Object unwrap() {
        return set;
    }

    private List<Object> snapshot() {
        return new ArrayList<>(set);
    }
}
