package cn.qihuang02.graaljs.util;

import java.util.List;

/**
 * 支持从数据创建对象实例和列表的接口。
 * 实现类可以定义如何从原始数据（如 Map、JSON 等）构建自身实例。
 *
 * @param <T> 对象类型
 */
public interface DataObject<T> {
    /**
     * 从数据创建单个对象实例。
     *
     * @param data 原始数据
     * @return 创建的对象实例，如果无法创建则返回 null
     */
    T createFromData(Object data);

    /**
     * 从数据创建对象列表。
     *
     * @param data 原始数据（通常是数组或列表）
     * @return 创建的对象列表
     */
    List<T> createListFromData(Object data);
}
