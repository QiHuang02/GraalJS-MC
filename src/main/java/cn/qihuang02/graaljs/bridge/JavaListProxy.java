package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 List 提供原生数组式访问。
 */
public class JavaListProxy implements ProxyArray, ProxyValue {
    private final GraaljsContext context;
    private final List<Object> list;

    @SuppressWarnings("unchecked")
    public JavaListProxy(GraaljsContext context, List<?> list) {
        this.context = context;
        this.list = (List<Object>) list;
    }

    @Override
    public Object get(long index) {
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return context.javaToJs(list.get((int) index));
    }

    @Override
    public void set(long index, Value value) {
        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        }
        int targetIndex = (int) index;
        Object converted = context.jsToJava(value, Object.class);
        while (list.size() < targetIndex) {
            list.add(null);
        }
        if (targetIndex == list.size()) {
            list.add(converted);
        } else {
            list.set(targetIndex, converted);
        }
    }

    @Override
    public boolean remove(long index) {
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove((int) index);
        return true;
    }

    @Override
    public long getSize() {
        return list.size();
    }

    @Override
    public Object unwrap() {
        return list;
    }
}
