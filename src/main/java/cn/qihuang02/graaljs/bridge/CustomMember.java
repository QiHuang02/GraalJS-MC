package cn.qihuang02.graaljs.bridge;

/**
 * 为宿主对象动态注入脚本成员。
 */
public record CustomMember(String name, Class<?> type, Object value) {
}
