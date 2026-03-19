package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.proxy.ProxyIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 将 Java {@link Iterator} 包装为 GraalVM {@link ProxyIterator}，
 * 使其在 JS 中支持迭代器协议。
 */
public class JavaIteratorProxy implements ProxyIterator {
    private final GraaljsContext context;
    private final Iterator<?> iterator;

    public JavaIteratorProxy(GraaljsContext context, Iterator<?> iterator) {
        this.context = context;
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Object getNext() throws NoSuchElementException {
        return context.javaToJs(iterator.next());
    }
}
